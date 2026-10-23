// 바닐라 RedstoneWallTorchBlock: 벽에 붙은 레드스톤 횃불. 붙은 벽 칸이 신호를 받으면 꺼진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class RedstoneWallTorchBlock extends RedstoneTorchBlock {

    private static final int WALL_TORCH = B.id("wall_torch");

    public RedstoneWallTorchBlock(int block) { super(block); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return WallTorchBlock.canSurvive(level, pos, WallTorchBlock.facing(state)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return Dir.opposite(dir) == WallTorchBlock.facing(state) && !canSurvive(state, level, pos) ? B.defaultState("air") : state;
    }

    /** getStateForPlacement: 벽 횃불의 방향 고르기를 그대로 쓴다. */
    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = B.behavior(B.defaultState(WALL_TORCH)).getStateForPlacement(ctx);
        return s < 0 ? -1 : WallTorchBlock.FACING.with(defaultState(), WallTorchBlock.FACING.valueName(s));
    }

    @Override
    protected boolean hasNeighborSignal(BlockLevel level, Pos pos, int state) {
        int d = Dir.opposite(WallTorchBlock.facing(state));
        return level.hasSignal(pos.relative(d), d);
    }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) {
        return LIT.is(state) && WallTorchBlock.facing(state) != dir ? 15 : 0;
    }
}
