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
}
