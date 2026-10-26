// 바닐라 CactusBlock: 모래·선인장 위에서, 옆 네 칸이 비어(단단하지 않고 용암 아님) 있을 때만 산다. 무작위 틱으로 3칸까지 자란다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class CactusBlock extends BlockBehavior {

    private static final Prop AGE = B.prop("age");

    public CactusBlock(int block) { super(block); }

    /** entityInside: 선인장 피해 1. */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) { entity.hurt(org.domi.furnace.server.entity.Entity.Damage.CACTUS, 1.0F); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        Pos above = pos.above();
        if (level.isEmptyBlock(above)) {
            int h = 1;
            while (B.is(level.getBlockState(pos.below(h)), block)) h++;
            if (h < 3) {
                int age = AGE.intValue(state);
                if (age == 15) {
                    level.setBlockAndUpdate(above, defaultState());
                    int reset = AGE.withInt(state, 0);
                    level.setBlock(pos, reset, 4);
                    level.neighborChanged(reset, above, block, pos, false);
                } else {
                    level.setBlock(pos, AGE.withInt(state, age + 1), 4);
                }
            }
        }
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (!canSurvive(state, level, pos)) level.scheduleTick(pos, block, 1);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        for (int d : Dir.HORIZONTAL) {
            Pos p = pos.relative(d);
            if (B.isSolid(level.getBlockState(p)) || Fluids.isLava(level.getFluidState(p))) return false;
        }
        int below = level.getBlockState(pos.below());
        return (B.is(below, block) || B.isTag(below, "minecraft:sand")) && !B.isLiquid(level.getBlockState(pos.above()));
    }
}
