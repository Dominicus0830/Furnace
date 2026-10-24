// 바닐라 CarvedPumpkinBlock(깎은 호박·잭오랜턴): 바라보는 방향의 반대로 놓인다. 눈·철 골렘 모양 위에 놓이면 골렘이 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class CarvedPumpkinBlock extends HorizontalDirectionalBlock {

    public CarvedPumpkinBlock(int block) { super(block); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, B.block(state))) trySpawnGolem(level, pos);
    }

    private static boolean isSnowBlock(BlockLevel level, Pos p) { return B.is(level.getBlockState(p), "minecraft:snow_block"); }

    private static boolean isIronBlock(BlockLevel level, Pos p) { return B.is(level.getBlockState(p), "minecraft:iron_block"); }

    private static boolean isAir(BlockLevel level, Pos p) { return B.isAir(level.getBlockState(p)); }

    /** 눈 골렘(호박 + 눈 블록 2) 또는 철 골렘(T자 철 블록 + 호박, 팔 양옆은 공기) 모양이면 블록을 지우고 골렘을 만든다. */
    private static void trySpawnGolem(BlockLevel level, Pos pos) {
        Pos below1 = pos.below(), below2 = below1.below();
        if (isSnowBlock(level, below1) && isSnowBlock(level, below2)) {
            clear(level, new Pos[] {pos, below1, below2});
            level.spawnGolem(false, below2);
            update(level, new Pos[] {pos, below1, below2});
            return;
        }
        if (!isIronBlock(level, below1) || !isIronBlock(level, below2)) return;
        for (int axis = 0; axis < 2; axis++) {
            int dx = axis == 0 ? 1 : 0, dz = axis == 0 ? 0 : 1;
            Pos a = below1.offset(-dx, 0, -dz), b = below1.offset(dx, 0, dz);
            if (!isIronBlock(level, a) || !isIronBlock(level, b)) continue;
            Pos[] air = {pos.offset(-dx, 0, -dz), pos.offset(dx, 0, dz), below2.offset(-dx, 0, -dz), below2.offset(dx, 0, dz)};
            boolean ok = true;
            for (Pos p : air) ok &= isAir(level, p);
            if (!ok) continue;
            Pos[] cells = {air[0], a, air[2], pos, below1, below2, air[1], b, air[3]};   // 열 순서(왼쪽 → 가운데 → 오른쪽), 위에서 아래로
            clear(level, cells);
            level.spawnGolem(true, below2);
            update(level, cells);
            return;
        }
    }

    private static void clear(BlockLevel level, Pos[] cells) {
        int air = B.defaultState(B.id("minecraft:air"));
        for (Pos p : cells) {
            int old = level.getBlockState(p);
            level.setBlock(p, air, 2);
            level.levelEvent(null, 2001, p, old);
        }
    }

    private static void update(BlockLevel level, Pos[] cells) {
        int air = B.id("minecraft:air");
        for (Pos p : cells) level.blockUpdated(p, air);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]); }
}
