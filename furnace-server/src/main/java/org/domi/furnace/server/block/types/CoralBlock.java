// 바닐라 CoralBlock: 산호 블록. 둘레에 물이 없으면 60..99틱 뒤 죽은 산호 블록이 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class CoralBlock extends BlockBehavior {

    private final int dead;

    public CoralBlock(int block) {
        super(block);
        this.dead = B.id(Blocks.get().params(block).get("dead").getAsString());
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!scanForWater(level, pos) && B.fade(level, pos, B.defaultState(dead))) level.setBlock(pos, B.defaultState(dead), 2);   // 플러그인 BlockFadeEvent
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (!scanForWater(level, pos)) level.scheduleTick(pos, block, 60 + level.random().nextInt(40));
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    /** scanForWater: 여섯 이웃 중 하나라도 물(#water 유체). */
    protected boolean scanForWater(BlockLevel level, Pos pos) {
        for (int d : Dir.ALL) if (Fluids.isWater(level.getFluidState(pos.relative(d)))) return true;
        return false;
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        if (!scanForWater(ctx.level(), ctx.clickedPos())) ctx.level().scheduleTick(ctx.clickedPos(), block, 60 + ctx.level().random().nextInt(40));
        return defaultState();
    }
}
