// 바닐라 WaterloggedTransparentBlock: 물에 잠길 수 있는 투명 블록. 잠긴 물은 떨어지는 물 원천이다(getSource(true)).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class WaterloggedTransparentBlock extends TransparentBlock {

    public WaterloggedTransparentBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return WATERLOGGED.with(super.getStateForPlacement(ctx), B.waterAt(ctx)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public int getFluidState(int state) { return WATERLOGGED.is(state) ? Fluids.WATER_SOURCE | 1 << 7 : Fluids.EMPTY; }
}
