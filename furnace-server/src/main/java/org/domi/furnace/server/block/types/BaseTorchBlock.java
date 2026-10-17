// 바닐라 BaseTorchBlock: 아래 칸 윗면 가운데가 받쳐 줘야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;

public abstract class BaseTorchBlock extends BlockBehavior {

    protected BaseTorchBlock(int block) { super(block); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.DOWN && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return B.canSupportCenter(level, pos.below(), Dir.UP); }
}
