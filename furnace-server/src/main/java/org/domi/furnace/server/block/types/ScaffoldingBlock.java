// 바닐라 ScaffoldingBlock: 비계. 아래 받침(단단한 윗면)이나 옆 비계에서 거리(0~7). 7 이 되면 무너진다(떨어지는 블록).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class ScaffoldingBlock extends BlockBehavior {

    private static final Prop DISTANCE = B.prop("distance"), BOTTOM = B.prop("bottom");
    private static final int SCAFFOLDING = B.id("scaffolding");

    public ScaffoldingBlock(int block) { super(block); }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) { return ctx.holding(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        Pos pos = ctx.clickedPos();
        BlockLevel level = ctx.level();
        int d = getDistance(level, pos);
        int s = WATERLOGGED.with(defaultState(), B.waterAt(ctx));
        return BOTTOM.with(DISTANCE.withInt(s, d), isBottom(level, pos, d));
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) { level.scheduleTick(pos, block, 1); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        level.scheduleTick(pos, block, 1);
        return state;
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        int d = getDistance(level, pos);
        int s = BOTTOM.with(DISTANCE.withInt(state, d), isBottom(level, pos, d));
        if (DISTANCE.intValue(s) == 7) {
            if (DISTANCE.intValue(state) == 7) {
                // TODO(엔티티): FallingBlockEntity.fall(level, pos, s)
            } else {
                level.destroyBlock(pos, true);
            }
        } else if (state != s) {
            level.setBlock(pos, s, 3);
        }
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return getDistance(level, pos) < 7; }

    private boolean isBottom(BlockLevel level, Pos pos, int d) { return d > 0 && !B.is(level.getBlockState(pos.below()), block); }

    /** getDistance: 아래가 비계면 그 거리, 아래 윗면이 단단하면 0, 아니면 옆 비계 거리 + 1(최대 7). */
    public static int getDistance(BlockLevel level, Pos pos) {
        Pos below = pos.below();
        int bs = level.getBlockState(below);
        int d = 7;
        if (B.is(bs, SCAFFOLDING)) d = DISTANCE.intValue(bs);
        else if (B.isFaceSturdy(bs, level, below, Dir.UP)) return 0;
        for (int dir : Dir.HORIZONTAL) {
            int s = level.getBlockState(pos.relative(dir));
            if (B.is(s, SCAFFOLDING)) {
                d = Math.min(d, DISTANCE.intValue(s) + 1);
                if (d == 1) break;
            }
        }
        return d;
    }
}
