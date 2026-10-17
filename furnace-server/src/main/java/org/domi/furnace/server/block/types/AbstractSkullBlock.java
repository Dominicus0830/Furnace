// 바닐라 AbstractSkullBlock: 머리·해골. 신호를 받으면 powered(용 머리 입 움직임 — 클라).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public abstract class AbstractSkullBlock extends BaseEntityBlock {

    static final Prop POWERED = B.prop("powered");

    protected AbstractSkullBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return POWERED.with(defaultState(), ctx.level().hasNeighborSignal(ctx.clickedPos())); }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != POWERED.is(state)) level.setBlock(pos, POWERED.with(state, signal), 2);
    }
}
