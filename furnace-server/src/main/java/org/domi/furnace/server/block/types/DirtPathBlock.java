// 바닐라 DirtPathBlock: 흙길. 위가 단단한 블록(울타리 문 제외)이면 다음 틱에 흙이 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class DirtPathBlock extends BlockBehavior {

    private static final int DIRT = B.id("dirt");

    public DirtPathBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        // TODO(엔티티): Block.pushEntitiesUp(흙길, 흙, level, pos)
        return !canSurvive(defaultState(), ctx.level(), ctx.clickedPos()) ? B.defaultState(DIRT) : super.getStateForPlacement(ctx);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir == Dir.UP && !canSurvive(state, level, pos)) level.scheduleTick(pos, block, 1);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) { FarmBlock.turnToDirt(null, state, level, pos); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int above = level.getBlockState(pos.above());
        return !B.isSolid(above) || B.behavior(above) instanceof FenceGateBlock;
    }
}
