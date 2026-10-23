// 바닐라 SporeBlossomBlock: 천장(위 칸 아랫면 가운데)에 매달리고, 물속에서는 못 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;

public class SporeBlossomBlock extends BlockBehavior {

    public SporeBlossomBlock(int block) { super(block); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        return B.canSupportCenter(level, pos.above(), Dir.DOWN) && !level.isWaterAt(pos);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.UP && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
