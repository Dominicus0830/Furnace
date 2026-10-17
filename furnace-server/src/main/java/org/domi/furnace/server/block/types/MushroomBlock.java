// 바닐라 MushroomBlock: 버섯. 어두운(밝기 13 미만) 단단한 블록 위나 #mushroom_grow_block 위. 무작위 틱(1/25)에 주변(최대 5)으로 번진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class MushroomBlock extends BushBlock implements BonemealableBlock {

    public MushroomBlock(int block) { super(block); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (random.nextInt(25) != 0) return;
        int left = 5;
        for (int x = -4; x <= 4; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -4; z <= 4; z++) {
                    if (B.is(level.getBlockState(pos.offset(x, y, z)), block) && --left <= 0) return;
                }
            }
        }
        Pos target = pos.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
        for (int i = 0; i < 4; i++) {
            if (level.isEmptyBlock(target) && B.canSurvive(state, level, target)) pos = target;
            target = pos.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
        }
        if (level.isEmptyBlock(target) && B.canSurvive(state, level, target)) level.setBlock(target, state, 2);
    }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) { return B.isFullOcclusion(state); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos below = pos.below();
        int bs = level.getBlockState(below);
        return B.isTag(bs, "minecraft:mushroom_grow_block") || level.getRawBrightness(pos, 0) < 13 && mayPlaceOn(bs, level, below);
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return true; }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return random.nextFloat() < 0.4; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        // TODO(지형 생성): growMushroom — 칸을 지우고 거대 버섯 기능(정의 인자 feature) 배치, 실패하면 되돌림.
    }
}
