// 바닐라 TallGrassBlock: 풀·고사리. 뼛가루로 위가 비었으면 두 칸 풀·큰 고사리가 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class TallGrassBlock extends BushBlock implements BonemealableBlock {

    private static final int FERN = B.id("fern"), LARGE_FERN = B.id("large_fern"), TALL_GRASS = B.id("tall_grass");

    public TallGrassBlock(int block) { super(block); }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return true; }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        int d = B.defaultState(B.is(state, FERN) ? LARGE_FERN : TALL_GRASS);
        if (B.canSurvive(d, level, pos) && level.isEmptyBlock(pos.above())) DoublePlantBlock.placeAt(level, d, pos, 2);
    }
}
