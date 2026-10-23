// 바닐라 AzaleaBlock: 진달래 덤불. 흙류·찰흙 위에서 산다. 뼛가루 45% 확률로 진달래 나무가 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class AzaleaBlock extends BushBlock implements BonemealableBlock {

    private static final int CLAY = B.id("clay");

    public AzaleaBlock(int block) { super(block); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) { return B.is(state, CLAY) || super.mayPlaceOn(state, level, pos); }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return Fluids.isEmpty(level.getFluidState(pos.above())); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return level.random().nextFloat() < 0.45; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        // TODO(지형 생성): TreeGrower.AZALEA.growTree(level, chunkGenerator, pos, state, random)
    }
}
