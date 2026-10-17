// 바닐라 PistonHeadBlock: 늘어난 피스톤 머리. 맞는 받침(같은 종류·늘어남·같은 방향)이나 움직이는 피스톤이 있어야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class PistonHeadBlock extends DirectionalBlock {

    private static final Prop TYPE = B.prop("type");
    private static final int PISTON = B.id("piston"), STICKY_PISTON = B.id("sticky_piston"), MOVING_PISTON = B.id("moving_piston");

    public PistonHeadBlock(int block) { super(block); }

    /** isFittingBase: 받침이 같은 종류 피스톤이고 늘어나 있고 같은 방향. */
    private static boolean isFittingBase(int head, int base) {
        int b = "normal".equals(TYPE.valueName(head)) ? PISTON : STICKY_PISTON;
        return B.is(base, b) && PistonBaseBlock.EXTENDED.is(base) && facing(base) == facing(head);
    }

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        if (player.instabuild()) {
            Pos base = pos.relative(Dir.opposite(facing(state)));
            if (isFittingBase(state, level.getBlockState(base))) level.destroyBlock(base, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block)) {
            super.onRemove(state, level, pos, newState, moved);
            Pos base = pos.relative(Dir.opposite(facing(state)));
            if (isFittingBase(state, level.getBlockState(base))) level.destroyBlock(base, true);
        }
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return Dir.opposite(dir) == facing(state) && !canSurvive(state, level, pos)
                ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int base = level.getBlockState(pos.relative(Dir.opposite(facing(state))));
        return isFittingBase(state, base) || B.is(base, MOVING_PISTON) && facing(base) == facing(state);
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (canSurvive(state, level, pos)) level.neighborChanged(pos.relative(Dir.opposite(facing(state))), sourceBlock, sourcePos);
    }
}
