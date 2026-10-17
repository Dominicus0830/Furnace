// 바닐라 HeavyCoreBlock: 무거운 핵. 물에 잠길 수 있다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class HeavyCoreBlock extends BlockBehavior {

    public HeavyCoreBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return WATERLOGGED.with(defaultState(), B.waterAt(ctx)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
