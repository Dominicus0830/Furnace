// 바닐라 HugeMushroomBlock: 거대 버섯 블록. 같은 블록과 맞닿은 면은 껍질이 아닌 속(false)이 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class HugeMushroomBlock extends BlockBehavior {

    public HugeMushroomBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        int s = defaultState();
        for (int d : new int[] {Dir.DOWN, Dir.UP, Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST}) {
            s = ChorusPlantBlock.SIDE[d].with(s, !B.is(level.getBlockState(pos.relative(d)), block));
        }
        return s;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return B.is(neighborState, block) ? ChorusPlantBlock.SIDE[dir].with(state, false) : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
