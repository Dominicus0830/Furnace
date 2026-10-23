// 바닐라 NyliumBlock: 진홍·뒤틀린 나일리움. 위가 빛을 막으면 무작위 틱에 네더랙이 된다. 뼛가루로 네더 식물(지형 생성 기능).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class NyliumBlock extends BlockBehavior implements BonemealableBlock {

    private static final int NETHERRACK = B.id("netherrack");

    public NyliumBlock(int block) { super(block); }

    private static boolean canBeNylium(BlockLevel level, Pos pos) { return !B.lightBlockedAbove(level.getBlockState(pos.above())); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canBeNylium(level, pos)) level.setBlockAndUpdate(pos, B.defaultState(NETHERRACK));
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return B.isAir(level.getBlockState(pos.above())); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        // TODO(지형 생성): 진홍 — crimson_forest_vegetation_bonemeal / 뒤틀린 — warped_forest_vegetation_bonemeal·nether_sprouts_bonemeal·
        //  1/8 twisting_vines_bonemeal 배치 기능을 위 칸에.
    }
}
