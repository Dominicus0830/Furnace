// 바닐라 TallSeagrassBlock: 물속 두 칸 해초. 언제나 물에 잠겨 있다(유체 = 물 원천). 양동이로 뜰 수 없다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class TallSeagrassBlock extends DoublePlantBlock {

    private static final int MAGMA_BLOCK = B.id("magma_block");

    public TallSeagrassBlock(int block) { super(block); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) {
        return B.isFaceSturdy(state, level, pos, Dir.UP) && !B.is(state, MAGMA_BLOCK);
    }

    private static boolean fullWater(int fluid) { return Fluids.isWater(fluid) && Fluids.amount(fluid) == 8; }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = super.getStateForPlacement(ctx);
        return s >= 0 && fullWater(ctx.level().getFluidState(ctx.clickedPos().above())) ? s : -1;
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        if (UPPER.equals(HALF.valueName(state))) {
            int below = level.getBlockState(pos.below());
            return B.is(below, block) && lower(below);
        }
        return super.canSurvive(state, level, pos) && fullWater(level.getFluidState(pos));
    }

    @Override
    public int getFluidState(int state) { return Fluids.WATER_SOURCE; }

    /** LiquidBlockContainer — 물을 받지 않는다(흐름이 이 칸으로 들어가지 않는다). */
    @Override
    public boolean isLiquidContainer(int state) { return true; }

    @Override
    public boolean canPlaceLiquid(org.domi.furnace.server.block.BlockActor player, org.domi.furnace.server.block.BlockLevel level,
                                  org.domi.furnace.server.block.Pos pos, int state, int fluidType) { return false; }

    @Override
    public boolean placeLiquid(org.domi.furnace.server.block.BlockLevel level, org.domi.furnace.server.block.Pos pos, int state, int fluidState) { return false; }
}
