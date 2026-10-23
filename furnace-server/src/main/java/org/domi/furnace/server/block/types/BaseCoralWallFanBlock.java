// 바닐라 BaseCoralWallFanBlock: 벽에 붙은 (죽은) 산호 부채. 붙은 면이 단단해야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class BaseCoralWallFanBlock extends BaseCoralFanBlock {

    static final Prop FACING = B.prop("facing");

    public BaseCoralWallFanBlock(int block) { super(block); }

    static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return Dir.opposite(dir) == facing(state) && !canSurvive(state, level, pos) ? B.defaultState("air") : state;
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int f = facing(state);
        Pos behind = pos.relative(Dir.opposite(f));
        return B.isFaceSturdy(level.getBlockState(behind), level, behind, f);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = super.getStateForPlacement(ctx);
        for (int d : ctx.nearestLookingDirections()) {
            if (Dir.horizontal(d)) {
                s = FACING.with(s, Dir.NAME[Dir.opposite(d)]);
                if (B.canSurvive(s, ctx.level(), ctx.clickedPos())) return s;
            }
        }
        return -1;
    }
}
