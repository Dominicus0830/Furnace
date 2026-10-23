// 바닐라 WallSignBlock: 벽 표지판. 등 뒤 칸이 단단(isSolid)해야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class WallSignBlock extends SignBlock {

    static final Prop FACING = B.prop("facing");

    public WallSignBlock(int block) { super(block); }

    static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        return B.isSolid(level.getBlockState(pos.relative(Dir.opposite(facing(state)))));
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = defaultState();
        boolean water = B.waterAt(ctx);
        for (int d : ctx.nearestLookingDirections()) {
            if (Dir.horizontal(d)) {
                s = FACING.with(s, Dir.NAME[Dir.opposite(d)]);
                if (B.canSurvive(s, ctx.level(), ctx.clickedPos())) return WATERLOGGED.with(s, water);
            }
        }
        return -1;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return Dir.opposite(dir) == facing(state) && !canSurvive(state, level, pos)
                ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
