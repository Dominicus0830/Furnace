// 바닐라 BambooSaplingBlock: 죽순. 위에 대나무가 생기면 대나무가 된다. 무작위 틱·뼛가루로 위에 작은 잎 대나무를 올린다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class BambooSaplingBlock extends BlockBehavior implements BonemealableBlock {

    private static final int BAMBOO = B.id("bamboo");
    private static final Prop LEAVES = B.prop("leaves");

    public BambooSaplingBlock(int block) { super(block); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (random.nextInt(3) == 0 && level.isEmptyBlock(pos.above()) && level.getRawBrightness(pos.above(), 0) >= 9) growBamboo(level, pos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        return B.isTag(level.getBlockState(pos.below()), "minecraft:bamboo_plantable_on");
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (!canSurvive(state, level, pos)) return B.defaultState("air");
        if (dir == Dir.UP && B.is(neighborState, BAMBOO)) level.setBlock(pos, B.defaultState(BAMBOO), 2);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return B.isAir(level.getBlockState(pos.above())); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) { growBamboo(level, pos); }

    protected void growBamboo(BlockLevel level, Pos pos) {
        level.setBlock(pos.above(), LEAVES.with(B.defaultState(BAMBOO), "small"), 3);
    }
}
