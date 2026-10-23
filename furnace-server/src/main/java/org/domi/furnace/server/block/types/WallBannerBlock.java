// 바닐라 WallBannerBlock: 벽 깃발. 바라보는 수평 방향의 반대 벽(단단한 블록)에 붙는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class WallBannerBlock extends AbstractBannerBlock {

    static final Prop FACING = B.prop("facing");

    public WallBannerBlock(int block) { super(block); }

    private static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        return B.isSolid(level.getBlockState(pos.relative(Dir.opposite(facing(state)))));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.opposite(facing(state)) && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = defaultState();
        for (int d : ctx.nearestLookingDirections()) {
            if (Dir.horizontal(d)) {
                s = FACING.with(s, Dir.NAME[Dir.opposite(d)]);
                if (canSurvive(s, ctx.level(), ctx.clickedPos())) return s;
            }
        }
        return -1;
    }
}
