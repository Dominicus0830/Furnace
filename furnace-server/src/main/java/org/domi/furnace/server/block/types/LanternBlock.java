// 바닐라 LanternBlock: 랜턴. 시선 순서대로 천장(매달림)이나 바닥에 놓는다. 붙은 면 가운데가 받쳐야 산다. 물에 잠길 수 있다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class LanternBlock extends BlockBehavior {

    private static final Prop HANGING = B.prop("hanging");

    public LanternBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        boolean water = B.waterAt(ctx);
        for (int d : ctx.nearestLookingDirections()) {
            if (Dir.axis(d) == Dir.AXIS_Y) {
                int s = HANGING.with(defaultState(), d == Dir.UP);
                if (B.canSurvive(s, ctx.level(), ctx.clickedPos())) return WATERLOGGED.with(s, water);
            }
        }
        return -1;
    }

    /** getConnectedDirection: 매달렸으면 아래, 아니면 위(이 블록 쪽에서 본 방향 — 붙은 칸은 그 반대). */
    private static int connected(int state) { return HANGING.is(state) ? Dir.DOWN : Dir.UP; }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int d = Dir.opposite(connected(state));
        return B.canSupportCenter(level, pos.relative(d), Dir.opposite(d));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return Dir.opposite(connected(state)) == dir && !canSurvive(state, level, pos)
                ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
