// 바닐라 TorchflowerCropBlock: 나이 0~1, 다 자라면(나이 2) 횃불꽃 블록이 된다. 무작위 틱 1/3 은 건너뛰고, 뼛가루는 한 단계.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class TorchflowerCropBlock extends CropBlock {

    private static final int TORCHFLOWER = B.id("torchflower");

    public TorchflowerCropBlock(int block) { super(block); }

    @Override
    public int maxAge() { return 2; }

    @Override
    public int stateForAge(int age) { return age == 2 ? B.defaultState(TORCHFLOWER) : super.stateForAge(age); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (random.nextInt(3) != 0) super.randomTick(state, level, pos, random);
    }

    @Override
    protected int bonemealAgeIncrease(BlockLevel level) { return 1; }
}
