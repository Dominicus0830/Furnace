// 바닐라 SugarCaneBlock: 사탕수수 위나, 옆에 물(또는 살얼음)이 닿은 흙·모래 위에서만 산다. 무작위 틱으로 3칸까지 자란다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class SugarCaneBlock extends BlockBehavior {

    private static final Prop AGE = B.prop("age");
    private static final int FROSTED_ICE = B.id("frosted_ice");

    public SugarCaneBlock(int block) { super(block); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (level.isEmptyBlock(pos.above())) {
            int h = 1;
            while (B.is(level.getBlockState(pos.below(h)), block)) h++;
            if (h < 3) {
                int age = AGE.intValue(state);
                if (age == 15) {
                    B.grow(level, pos.above(), defaultState(), 3);   // 플러그인 BlockGrowEvent — 취소돼도 나이는 0(CraftBukkit)
                    level.setBlock(pos, AGE.withInt(state, 0), 4);
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
        int below = level.getBlockState(pos.below());
        if (B.is(below, block)) return true;
        if (B.isTag(below, "minecraft:dirt") || B.isTag(below, "minecraft:sand")) {
            Pos b = pos.below();
            for (int d : Dir.HORIZONTAL) {
                Pos p = b.relative(d);
                if (Fluids.isWater(level.getFluidState(p)) || B.is(level.getBlockState(p), FROSTED_ICE)) return true;
            }
        }
        return false;
    }
}
