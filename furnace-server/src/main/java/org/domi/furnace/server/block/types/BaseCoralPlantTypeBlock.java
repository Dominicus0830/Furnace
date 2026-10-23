// 바닐라 BaseCoralPlantTypeBlock: 산호(죽은 것 포함) 바탕. 아래 윗면이 단단해야 산다. 물에 잠김(기본). 주변에 물이 없으면 죽을 틱을 건다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public abstract class BaseCoralPlantTypeBlock extends BlockBehavior {

    protected BaseCoralPlantTypeBlock(int block) { super(block); }

    /** tryScheduleDieTick: 물이 없으면 60~99틱 뒤 틱. */
    protected void tryScheduleDieTick(int state, BlockLevel level, Pos pos) {
        if (!scanForWater(state, level, pos)) level.scheduleTick(pos, block, 60 + level.random().nextInt(40));
    }

    protected static boolean scanForWater(int state, BlockLevel level, Pos pos) {
        if (WATERLOGGED.is(state)) return true;
        for (int d : Dir.ALL) if (Fluids.isWater(level.getFluidState(pos.relative(d)))) return true;
        return false;
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int f = ctx.level().getFluidState(ctx.clickedPos());
        return WATERLOGGED.with(defaultState(), Fluids.isWater(f) && Fluids.amount(f) == 8);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return dir == Dir.DOWN && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos below = pos.below();
        return B.isFaceSturdy(level.getBlockState(below), level, below, Dir.UP);
    }
}
