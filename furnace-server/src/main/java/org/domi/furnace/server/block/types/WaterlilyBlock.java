// 바닐라 WaterlilyBlock: 수련잎. 물 원천(또는 얼음) 위, 그 위에 유체가 없는 곳에서만 산다. 보트가 닿으면 부서진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;

public class WaterlilyBlock extends BushBlock {

    public WaterlilyBlock(int block) { super(block); }

    // TODO(엔티티): entityInside — 보트가 닿으면 destroyBlock(pos, true, 보트).

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) {
        int here = level.getFluidState(pos), above = level.getFluidState(pos.above());
        return (Fluids.type(here) == Fluids.T_WATER || B.behavior(state) instanceof IceBlock) && Fluids.isEmpty(above);
    }
}
