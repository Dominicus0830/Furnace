// 바닐라 CoralPlantBlock: 살아 있는 산호. 놓이거나 이웃이 바뀔 때 물이 없으면 죽을 틱을 걸고, 틱에 여전히 없으면 죽은 산호가 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class CoralPlantBlock extends BaseCoralPlantBlock {

    private final int dead;

    public CoralPlantBlock(int block) {
        super(block);
        this.dead = B.id(Blocks.get().params(block).get("dead").getAsString());
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) { tryScheduleDieTick(state, level, pos); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!scanForWater(state, level, pos)) level.setBlock(pos, WATERLOGGED.with(B.defaultState(dead), false), 2);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir == Dir.DOWN && !canSurvive(state, level, pos)) return B.defaultState("air");
        tryScheduleDieTick(state, level, pos);
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
