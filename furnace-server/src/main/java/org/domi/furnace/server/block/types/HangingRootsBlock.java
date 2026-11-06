// 바닐라 HangingRootsBlock: 매달린 뿌리. 위 블록의 아랫면이 단단해야 살고, 물에 잠길 수 있다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class HangingRootsBlock extends BlockBehavior {

    public HangingRootsBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = super.getStateForPlacement(ctx);
        return s < 0 ? -1 : WATERLOGGED.with(s, B.waterAt(ctx));
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos above = pos.above();
        return B.isFaceSturdy(level.getBlockState(above), level, above, Dir.DOWN);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir == Dir.UP && !canSurvive(state, level, pos)) return B.defaultState("air");
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
