// 바닐라 WitherSkullBlock: 위더 해골. 영혼 모래·흙 T자 위에 세 개가 모이면 위더(엔티티).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ItemStack;

public class WitherSkullBlock extends SkullBlock {

    public WitherSkullBlock(int block) { super(block); }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) { checkSpawn(level, pos); }

    private static boolean isSkull(BlockLevel level, Pos p) {
        int s = level.getBlockState(p);
        return B.is(s, "minecraft:wither_skeleton_skull") || B.is(s, "minecraft:wither_skeleton_wall_skull");
    }

    private static boolean isBase(BlockLevel level, Pos p) { return B.isTag(level.getBlockState(p), "minecraft:wither_summon_base_blocks"); }

    /**
     * checkSpawn: 평화로움이 아니고, 방금 놓은 해골이 속한 틀(위 줄 해골 셋, 가운데 줄 #wither_summon_base_blocks 셋, 맨 아래 가운데 같은 블록 + 양옆 공기)이
     * x 축이나 z 축으로 맞으면 블록을 지우고 위더를 만든다.
     */
    public static void checkSpawn(BlockLevel level, Pos pos) {
        if (pos.y() < level.minBuildHeight() || level.difficulty() == 0 || !isSkull(level, pos)) return;
        for (int axis = 0; axis < 2; axis++) {
            int dx = axis == 0 ? 1 : 0, dz = axis == 0 ? 0 : 1;
            for (int col = -1; col <= 1; col++) {
                Pos top = pos.offset(-col * dx, 0, -col * dz);   // 위 줄 가운데
                Pos mid = top.below(), bottom = mid.below();
                Pos[] cells = {top.offset(-dx, 0, -dz), top, top.offset(dx, 0, dz), mid.offset(-dx, 0, -dz), mid, mid.offset(dx, 0, dz),
                        bottom.offset(-dx, 0, -dz), bottom, bottom.offset(dx, 0, dz)};
                boolean ok = true;
                for (int i = 0; i < 3 && ok; i++) ok = isSkull(level, cells[i]);
                for (int i = 3; i < 6 && ok; i++) ok = isBase(level, cells[i]);
                ok = ok && B.isAir(level.getBlockState(cells[6])) && isBase(level, cells[7]) && B.isAir(level.getBlockState(cells[8]));
                if (!ok) continue;
                CarvedPumpkinBlock.clear(level, cells);
                level.spawnWither(bottom, axis == 0);
                CarvedPumpkinBlock.update(level, cells);
                return;
            }
        }
    }
}
