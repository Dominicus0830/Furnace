// 바닐라 KelpPlantBlock: 켈프 몸통. 언제나 물에 잠김. 마그마 위에는 붙지 않는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Fluids;

public class KelpPlantBlock extends GrowingPlantBlock.GrowingPlantBody {

    private static final int MAGMA_BLOCK = B.id("magma_block");

    public KelpPlantBlock(int block) { super(block, KELP); }

    @Override
    protected boolean canAttachTo(int state) { return !B.is(state, MAGMA_BLOCK); }

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
