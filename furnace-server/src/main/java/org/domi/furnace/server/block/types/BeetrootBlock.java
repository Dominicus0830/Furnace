// 바닐라 BeetrootBlock: 나이 0~3 작물. 무작위 틱의 1/3 은 건너뛰고, 뼛가루 증가량이 1/3.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class BeetrootBlock extends CropBlock {

    public BeetrootBlock(int block) { super(block); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (random.nextInt(3) != 0) super.randomTick(state, level, pos, random);
    }

    @Override
    protected int bonemealAgeIncrease(BlockLevel level) { return super.bonemealAgeIncrease(level) / 3; }
}
