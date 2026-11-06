// 바닐라 MushroomBlock: 버섯. 어두운(밝기 13 미만) 단단한 블록 위나 #mushroom_grow_block 위. 무작위 틱(1/25)에 주변(최대 5)으로 번진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class MushroomBlock extends BushBlock implements BonemealableBlock {

    /** 거대 버섯 configured feature(정의 인자 feature). */
    private final String feature;

    public MushroomBlock(int block) {
        super(block);
        this.feature = org.domi.furnace.server.block.Blocks.get().params(block).get("feature").getAsString();
    }

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
        final Pos origin = pos;
        Pos target = pos.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
        for (int i = 0; i < 4; i++) {
            if (level.isEmptyBlock(target) && B.canSurvive(state, level, target)) pos = target;
            target = pos.offset(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
        }
        if (level.isEmptyBlock(target) && B.canSurvive(state, level, target)) B.spread(level, origin, target, state, 2);   // 플러그인 BlockSpreadEvent
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
        growMushroom(level, pos, state, random);
    }

    /** growMushroom: 칸을 지우고(removeBlock) 거대 버섯을 놓는다. 실패하면 버섯을 되돌린다(flag 3). 미구현 feature 면 아무것도 안 한다. */
    public boolean growMushroom(BlockLevel level, Pos pos, int state, RandomSource random) {
        org.domi.furnace.server.block.LevelFeature f = level.configuredFeature(feature);
        if (f == null || !f.supported()) return false;
        level.removeBlock(pos, false);
        if (level.placeFeature(f, pos, random)) return true;
        level.setBlock(pos, state, 3);
        return false;
    }
}
