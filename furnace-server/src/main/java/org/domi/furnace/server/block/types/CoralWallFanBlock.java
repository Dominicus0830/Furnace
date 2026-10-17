// 바닐라 CoralWallFanBlock: 벽에 붙은 살아 있는 산호 부채. 물이 없으면 죽은 벽 산호 부채가 된다(방향 유지).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class CoralWallFanBlock extends BaseCoralWallFanBlock {

    private final int dead;

    public CoralWallFanBlock(int block) {
        super(block);
        this.dead = B.id(Blocks.get().params(block).get("dead").getAsString());
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) { tryScheduleDieTick(state, level, pos); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!scanForWater(state, level, pos)) {
            level.setBlock(pos, FACING.with(WATERLOGGED.with(B.defaultState(dead), false), FACING.valueName(state)), 2);
        }
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (Dir.opposite(dir) == facing(state) && !canSurvive(state, level, pos)) return B.defaultState("air");
        B.tickWaterlogged(state, level, pos);
        tryScheduleDieTick(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
