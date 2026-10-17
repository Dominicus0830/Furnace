// 바닐라 ConcretePowderBlock: 물에 닿으면(놓을 때·이웃이 바뀔 때·떨어져 내려앉을 때) 콘크리트가 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class ConcretePowderBlock extends FallingBlock {

    private final int concrete;

    public ConcretePowderBlock(int block) {
        super(block);
        this.concrete = B.id(Blocks.get().params(block).get("concrete").getAsString());
    }

    /** onLand(떨어진 블록이 내려앉을 때): 물에 닿으면 콘크리트. TODO(엔티티): FallingBlockEntity 가 부른다. */
    public void onLand(BlockLevel level, Pos pos, int state, int replaced) {
        if (shouldSolidify(level, pos, replaced)) level.setBlock(pos, B.defaultState(concrete), 3);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        return shouldSolidify(level, pos, level.getBlockState(pos)) ? B.defaultState(concrete) : super.getStateForPlacement(ctx);
    }

    private static boolean shouldSolidify(BlockLevel level, Pos pos, int state) { return canSolidify(state) || touchesLiquid(level, pos); }

    /** touchesLiquid: 바닐라 그대로 — 아래 방향은 직전에 본 칸이 물일 때만 보고, 면 판정은 가루 칸 기준. */
    private static boolean touchesLiquid(BlockLevel level, Pos pos) {
        Pos m = pos;
        for (int d : Dir.ALL) {
            int s = level.getBlockState(m);
            if (d != Dir.DOWN || canSolidify(s)) {
                m = pos.relative(d);
                s = level.getBlockState(m);
                if (canSolidify(s) && !B.isFaceSturdy(s, level, pos, Dir.opposite(d))) return true;
            }
        }
        return false;
    }

    private static boolean canSolidify(int state) { return Fluids.isWater(B.fluid(state)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return touchesLiquid(level, pos) ? B.defaultState(concrete) : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
