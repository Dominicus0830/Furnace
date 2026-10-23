// 바닐라 WallTorchBlock: 벽에 붙은 횃불. 붙은 벽 면이 단단해야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class WallTorchBlock extends TorchBlock {

    static final Prop FACING = B.prop("facing");

    public WallTorchBlock(int block) { super(block); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return canSurvive(level, pos, facing(state)); }

    /** WallTorchBlock.canSurvive(level, pos, facing): 등 뒤 칸의 facing 면이 단단한가. */
    static boolean canSurvive(BlockLevel level, Pos pos, int facing) {
        Pos behind = pos.relative(Dir.opposite(facing));
        return B.isFaceSturdy(level.getBlockState(behind), level, behind, facing);
    }

    static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    /** getStateForPlacement: 시선 순서대로 수평 방향의 반대를 보게 놓아 보고 살 수 있는 첫 방향. */
    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = defaultState();
        for (int d : ctx.nearestLookingDirections()) {
            if (Dir.horizontal(d)) {
                s = FACING.with(s, Dir.NAME[Dir.opposite(d)]);
                if (B.canSurvive(s, ctx.level(), ctx.clickedPos())) return s;
            }
        }
        return -1;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return Dir.opposite(dir) == facing(state) && !canSurvive(state, level, pos) ? B.defaultState("air") : state;
    }
}
