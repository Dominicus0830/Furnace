// 바닐라 LadderBlock: 사다리. 바라보는 쪽 벽(단단한 면)에 붙고, 같은 방향 사다리의 앞면을 눌러 그 칸에 또 놓을 수는 없다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class LadderBlock extends BlockBehavior {

    static final Prop FACING = B.prop("facing");

    public LadderBlock(int block) { super(block); }

    private static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int f = facing(state);
        Pos behind = pos.relative(Dir.opposite(f));
        return B.isFaceSturdy(level.getBlockState(behind), level, behind, f);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (Dir.opposite(dir) == facing(state) && !canSurvive(state, level, pos)) return B.defaultState("air");
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        if (!ctx.replacingClickedOnBlock()) {
            int s = ctx.level().getBlockState(ctx.clickedPos().relative(Dir.opposite(ctx.clickedFace())));
            if (B.is(s, block) && facing(s) == ctx.clickedFace()) return -1;
        }
        int s = defaultState();
        for (int d : ctx.nearestLookingDirections()) {
            if (Dir.horizontal(d)) {
                s = FACING.with(s, Dir.NAME[Dir.opposite(d)]);
                if (canSurvive(s, ctx.level(), ctx.clickedPos())) return WATERLOGGED.with(s, B.waterAt(ctx));
            }
        }
        return -1;
    }
}
