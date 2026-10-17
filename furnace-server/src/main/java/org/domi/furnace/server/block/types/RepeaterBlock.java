// 바닐라 RepeaterBlock: 레드스톤 중계기. delay 1..4(틱 2배), 옆에서 켜진 다이오드가 들어오면 잠긴다(locked).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class RepeaterBlock extends DiodeBlock {

    static final Prop LOCKED = B.prop("locked"), DELAY = B.prop("delay");

    public RepeaterBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (!player.mayBuild()) return InteractionResult.PASS;
        level.setBlock(pos, DELAY.cycle(state), 3);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    protected int getDelay(int state) { return DELAY.intValue(state) * 2; }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = super.getStateForPlacement(ctx);
        return LOCKED.with(s, isLocked(ctx.level(), ctx.clickedPos(), s));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir == Dir.DOWN && !canSurviveOn(level, neighborPos, neighborState)) return B.defaultState("air");
        if (Dir.axis(dir) != Dir.axis(facing(state))) return LOCKED.with(state, isLocked(level, pos, state));
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean isLocked(BlockLevel level, Pos pos, int state) { return getAlternateSignal(level, pos, state) > 0; }

    @Override
    protected boolean sideInputDiodesOnly() { return true; }
}
