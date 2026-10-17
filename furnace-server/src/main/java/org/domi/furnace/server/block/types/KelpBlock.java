// 바닐라 KelpBlock: 켈프 머리(위로 자람, 물 원천 안에서만). 마그마 위에는 붙지 않는다. 언제나 물에 잠김.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;

public class KelpBlock extends GrowingPlantBlock.GrowingPlantHead {

    private static final int WATER = B.id("water"), MAGMA_BLOCK = B.id("magma_block");

    public KelpBlock(int block) { super(block, KELP); }

    @Override
    protected boolean canGrowInto(int state) { return B.is(state, WATER); }

    @Override
    protected boolean canAttachTo(int state) { return !B.is(state, MAGMA_BLOCK); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int f = ctx.level().getFluidState(ctx.clickedPos());
        return Fluids.isWater(f) && Fluids.amount(f) == 8 ? super.getStateForPlacement(ctx) : -1;
    }

    @Override
    public int getFluidState(int state) { return Fluids.WATER_SOURCE; }
}
