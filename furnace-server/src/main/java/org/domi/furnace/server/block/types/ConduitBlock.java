// 바닐라 ConduitBlock: 전달체. 놓을 칸이 가득 찬 물이면 물에 잠긴다(기본은 잠김). 활성화·효과·공격은 ConduitBlockEntity.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class ConduitBlock extends BaseEntityBlock {

    public ConduitBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int f = ctx.level().getFluidState(ctx.clickedPos());
        return WATERLOGGED.with(defaultState(), Fluids.isWater(f) && Fluids.amount(f) == 8);
    }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        return new org.domi.furnace.server.block.entity.ConduitBlockEntity(pos, state);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
