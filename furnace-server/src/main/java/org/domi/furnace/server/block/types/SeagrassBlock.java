// 바닐라 SeagrassBlock: 물속 해초(유체 = 물 원천). 단단한 윗면(마그마 제외) 위. 뼛가루로 위가 물이면 키 큰 해초.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class SeagrassBlock extends BushBlock implements BonemealableBlock {

    private static final int MAGMA_BLOCK = B.id("magma_block"), TALL_SEAGRASS = B.id("tall_seagrass"), WATER = B.id("water");

    public SeagrassBlock(int block) { super(block); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) {
        return B.isFaceSturdy(state, level, pos, Dir.UP) && !B.is(state, MAGMA_BLOCK);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int f = ctx.level().getFluidState(ctx.clickedPos());
        return Fluids.isWater(f) && Fluids.amount(f) == 8 ? super.getStateForPlacement(ctx) : -1;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        int s = super.updateShape(state, dir, neighborState, level, pos, neighborPos);
        if (!B.isAir(s)) level.scheduleFluidTick(pos, Fluids.T_WATER, 5);
        return s;
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return true; }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public int getFluidState(int state) { return Fluids.WATER_SOURCE; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        int lower = B.defaultState(TALL_SEAGRASS);
        int upper = DoublePlantBlock.HALF.with(lower, DoublePlantBlock.UPPER);
        Pos above = pos.above();
        if (B.is(level.getBlockState(above), WATER)) {
            level.setBlock(pos, lower, 2);
            level.setBlock(above, upper, 2);
        }
    }
}
