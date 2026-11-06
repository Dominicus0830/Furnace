// 바닐라 LeavesBlock: 나뭇잎. 가장 가까운 통나무까지 거리(1~7)를 이웃에서 전한다. 거리 7 이고 놓인 것이 아니면 무작위 틱에 썩는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class LeavesBlock extends BlockBehavior {

    static final Prop DISTANCE = B.prop("distance"), PERSISTENT = B.prop("persistent");

    public LeavesBlock(int block) { super(block); }

    @Override
    public boolean isRandomlyTicking(int state) { return DISTANCE.intValue(state) == 7 && !PERSISTENT.is(state); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (decaying(state)) {
            if (!level.gameEvents().leavesDecay(level.dimension(), pos, state)) return;   // 플러그인 LeavesDecayEvent 취소
            level.dropResources(state, pos);
            level.removeBlock(pos, false);
        }
    }

    protected boolean decaying(int state) { return !PERSISTENT.is(state) && DISTANCE.intValue(state) == 7; }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) { level.setBlock(pos, updateDistance(state, level, pos), 3); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        int d = distanceAt(neighborState) + 1;
        if (d != 1 || DISTANCE.intValue(state) != d) level.scheduleTick(pos, block, 1);
        return state;
    }

    private static int updateDistance(int state, BlockLevel level, Pos pos) {
        int d = 7;
        for (int dir : Dir.ALL) {
            d = Math.min(d, distanceAt(level.getBlockState(pos.relative(dir))) + 1);
            if (d == 1) break;
        }
        return DISTANCE.withInt(state, d);
    }

    /** getDistanceAt: 통나무 0, 나뭇잎은 그 거리, 그 외 7. */
    private static int distanceAt(int state) {
        if (B.isTag(state, "minecraft:logs")) return 0;
        return DISTANCE.has(state) ? DISTANCE.intValue(state) : 7;
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = WATERLOGGED.with(PERSISTENT.with(defaultState(), true), B.waterAt(ctx));
        return updateDistance(s, ctx.level(), ctx.clickedPos());
    }
}
