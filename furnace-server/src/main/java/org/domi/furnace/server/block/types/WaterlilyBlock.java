// 바닐라 WaterlilyBlock: 수련잎. 물 원천(또는 얼음) 위, 그 위에 유체가 없는 곳에서만 산다. 보트가 닿으면 부서진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;

public class WaterlilyBlock extends BushBlock {

    public WaterlilyBlock(int block) { super(block); }

    /** entityInside: 보트가 닿으면 부순다(떨굼, 부순 엔티티 = 보트). */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        super.entityInside(state, level, pos, entity);
        // CraftBukkit(+Paper 새 상태): EntityChangeBlockEvent 를 막으면 부수지 않는다
        if (entity instanceof org.domi.furnace.server.entity.Boat
                && org.domi.furnace.server.block.B.entityChange(level, entity, pos, org.domi.furnace.server.block.B.fluidLeftover(state))) level.destroyBlockByEntity(pos, true, entity);
    }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) {
        int here = level.getFluidState(pos), above = level.getFluidState(pos.above());
        return (Fluids.type(here) == Fluids.T_WATER || B.behavior(state) instanceof IceBlock) && Fluids.isEmpty(above);
    }
}
