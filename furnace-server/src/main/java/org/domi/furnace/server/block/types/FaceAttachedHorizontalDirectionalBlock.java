// 바닐라 FaceAttachedHorizontalDirectionalBlock: 바닥·벽·천장에 붙는 블록(버튼·레버·숫돌). 붙은 면이 단단해야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public abstract class FaceAttachedHorizontalDirectionalBlock extends HorizontalDirectionalBlock {

    protected static final Prop FACE = B.prop("face");

    protected FaceAttachedHorizontalDirectionalBlock(int block) { super(block); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return canAttach(level, pos, Dir.opposite(getConnectedDirection(state))); }

    /** canAttach(level, pos, dir): dir 쪽 칸의 반대 면이 단단한가. */
    public static boolean canAttach(BlockLevel level, Pos pos, int dir) {
        Pos p = pos.relative(dir);
        return B.isFaceSturdy(level.getBlockState(p), level, p, Dir.opposite(dir));
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        for (int d : ctx.nearestLookingDirections()) {
            int s;
            if (Dir.axis(d) == Dir.AXIS_Y) {
                s = FACE.with(defaultState(), d == Dir.UP ? "ceiling" : "floor");
                s = FACING.with(s, Dir.NAME[ctx.horizontalDirection()]);
            } else {
                s = FACE.with(defaultState(), "wall");
                s = FACING.with(s, Dir.NAME[Dir.opposite(d)]);
            }
            if (B.canSurvive(s, ctx.level(), ctx.clickedPos())) return s;
        }
        return -1;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return Dir.opposite(getConnectedDirection(state)) == dir && !canSurvive(state, level, pos)
                ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    /** getConnectedDirection: 천장이면 아래, 바닥이면 위, 벽이면 facing(블록이 바라보는 쪽 — 붙은 칸은 그 반대). */
    protected static int getConnectedDirection(int state) {
        return switch (FACE.valueName(state)) {
            case "ceiling" -> Dir.DOWN;
            case "floor" -> Dir.UP;
            default -> Dir.byName(FACING.valueName(state));
        };
    }
}
