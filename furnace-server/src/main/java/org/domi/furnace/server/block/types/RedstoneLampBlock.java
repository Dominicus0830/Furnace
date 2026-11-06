// 바닐라 RedstoneLampBlock: 레드스톤 조명. 신호를 받으면 바로 켜지고, 끊기면 4틱 뒤 꺼진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class RedstoneLampBlock extends BlockBehavior {

    private static final Prop LIT = B.prop("lit");

    public RedstoneLampBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return LIT.with(defaultState(), ctx.level().hasNeighborSignal(ctx.clickedPos())); }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        boolean lit = LIT.is(state);
        if (lit != level.hasNeighborSignal(pos)) {
            if (lit) level.scheduleTick(pos, block, 4);
            else if (level.gameEvents().blockRedstone(level.dimension(), pos, state, 0, 15) == 15) level.setBlock(pos, LIT.cycle(state), 2);   // 플러그인 BlockRedstoneEvent
        }
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (LIT.is(state) && !level.hasNeighborSignal(pos)
                && level.gameEvents().blockRedstone(level.dimension(), pos, state, 15, 0) == 0) level.setBlock(pos, LIT.cycle(state), 2);   // 플러그인 BlockRedstoneEvent
    }
}
