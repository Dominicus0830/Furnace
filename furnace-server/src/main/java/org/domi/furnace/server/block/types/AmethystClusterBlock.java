// 바닐라 AmethystClusterBlock: 자수정 싹·송이. 누른 면 쪽을 향해 붙고(facing), 뒤 받침면이 단단해야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class AmethystClusterBlock extends AmethystBlock {

    static final Prop FACING = B.prop("facing");

    public AmethystClusterBlock(int block) { super(block); }

    static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int f = facing(state);
        Pos behind = pos.relative(Dir.opposite(f));
        return B.isFaceSturdy(level.getBlockState(behind), level, behind, f);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return dir == Dir.opposite(facing(state)) && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(WATERLOGGED.with(defaultState(), B.waterAt(ctx)), Dir.NAME[ctx.clickedFace()]);
    }
}
