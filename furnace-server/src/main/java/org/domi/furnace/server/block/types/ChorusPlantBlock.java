// 바닐라 ChorusPlantBlock: 후렴 식물 줄기. 같은 줄기·후렴 꽃(아래는 엔드 돌도)으로 여섯 방향 연결. 받침이 없으면 다음 틱에 부서진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class ChorusPlantBlock extends BlockBehavior {

    /** PipeBlock.PROPERTY_BY_DIRECTION. */
    static final Prop[] SIDE = new Prop[6];
    private static final int CHORUS_FLOWER = B.id("chorus_flower"), END_STONE = B.id("end_stone");

    static {
        for (int d = 0; d < 6; d++) SIDE[d] = B.prop(Dir.NAME[d]);
    }

    public ChorusPlantBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return stateWithConnections(ctx.level(), ctx.clickedPos(), defaultState()); }

    /** getStateWithConnections(level, pos, state). */
    public static int stateWithConnections(BlockLevel level, Pos pos, int state) {
        int b = B.block(state);
        for (int d : new int[] {Dir.DOWN, Dir.UP, Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST}) {
            int n = level.getBlockState(pos.relative(d));
            boolean c = B.is(n, b) || B.is(n, CHORUS_FLOWER) || d == Dir.DOWN && B.is(n, END_STONE);
            if (SIDE[d].has(state)) state = SIDE[d].with(state, c);
        }
        return state;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (!canSurvive(state, level, pos)) {
            level.scheduleTick(pos, block, 1);
            return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
        }
        boolean c = B.is(neighborState, block) || B.is(neighborState, CHORUS_FLOWER) || dir == Dir.DOWN && B.is(neighborState, END_STONE);
        return SIDE[dir].with(state, c);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int below = level.getBlockState(pos.below());
        boolean vertical = !B.isAir(level.getBlockState(pos.above())) && !B.isAir(below);
        for (int d : Dir.HORIZONTAL) {
            Pos p = pos.relative(d);
            if (B.is(level.getBlockState(p), block)) {
                if (vertical) return false;
                int pb = level.getBlockState(p.below());
                if (B.is(pb, block) || B.is(pb, END_STONE)) return true;
            }
        }
        return B.is(below, block) || B.is(below, END_STONE);
    }
}
