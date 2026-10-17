// 바닐라 FungusBlock: 진홍·뒤틀린 균. 나일리움·균사체·영혼 흙·흙류 위. 자라는 땅(grows_on) 위에서 뼛가루 40% 로 거대 균.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class FungusBlock extends BushBlock implements BonemealableBlock {

    private static final int MYCELIUM = B.id("mycelium"), SOUL_SOIL = B.id("soul_soil");
    private final int growsOn;

    public FungusBlock(int block) {
        super(block);
        this.growsOn = B.id(Blocks.get().params(block).get("grows_on").getAsString());
    }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) {
        return B.isTag(state, "minecraft:nylium") || B.is(state, MYCELIUM) || B.is(state, SOUL_SOIL) || super.mayPlaceOn(state, level, pos);
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return B.is(level.getBlockState(pos.below()), growsOn); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return random.nextFloat() < 0.4; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        // TODO(지형 생성): 정의 인자 feature(crimson/warped_fungus_planted) 를 이 칸에 배치.
    }
}
