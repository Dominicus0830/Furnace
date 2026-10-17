// 바닐라 WeepingVinesBlock: 눈물 덩굴 머리(아래로 자람).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.RandomSource;

public class WeepingVinesBlock extends GrowingPlantBlock.GrowingPlantHead {

    public WeepingVinesBlock(int block) { super(block, WEEPING); }

    @Override
    protected boolean canGrowInto(int state) { return NetherVines.isValidGrowthState(state); }

    @Override
    protected int blocksToGrowWhenBonemealed(RandomSource random) { return NetherVines.blocksToGrowWhenBonemealed(random); }
}
