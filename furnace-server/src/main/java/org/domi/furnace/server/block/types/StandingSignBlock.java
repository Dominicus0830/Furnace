// 바닐라 StandingSignBlock: 땅에 세운 표지판. 16방향 rotation, 아래 칸이 단단(isSolid)해야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class StandingSignBlock extends SignBlock {

    static final Prop ROTATION = B.prop("rotation");

    public StandingSignBlock(int block) { super(block); }

    /** RotationSegment.convertToDegrees. */
    @Override
    public float yRotationDegrees(int state) { return ROTATION.intValue(state) * 22.5F; }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return B.isSolid(level.getBlockState(pos.below())); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = ROTATION.withInt(defaultState(), segment(ctx.rotation() + 180.0F));
        return WATERLOGGED.with(s, B.waterAt(ctx));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.DOWN && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
