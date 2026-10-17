// 바닐라 BubbleColumnBlock: 거품 기둥(언제나 물 원천). 아래가 영혼 모래면 위로(drag=false), 마그마면 아래로(drag=true) 끈다.
// 아래부터 물 원천이 이어지는 동안 위로 기둥을 세운다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class BubbleColumnBlock extends BlockBehavior {

    private static final Prop DRAG = B.prop("drag");
    private static final int BUBBLE_COLUMN = B.id("bubble_column"), WATER = B.id("water"), SOUL_SAND = B.id("soul_sand"),
            MAGMA_BLOCK = B.id("magma_block");

    public BubbleColumnBlock(int block) { super(block); }

    // TODO(엔티티): entityInside — 위가 공기면 수면 튕김, 아니면 기둥 안 끌림(drag 방향).

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        updateColumn(level, pos, state, level.getBlockState(pos.below()));
    }

    @Override
    public int getFluidState(int state) { return Fluids.WATER_SOURCE; }

    /** updateColumn(level, pos, belowState): 그 칸부터 위로. */
    public static void updateColumn(BlockLevel level, Pos pos, int belowState) { updateColumn(level, pos, level.getBlockState(pos), belowState); }

    public static void updateColumn(BlockLevel level, Pos pos, int state, int belowState) {
        if (!canExistIn(state)) return;
        int s = columnState(belowState);
        level.setBlock(pos, s, 2);
        Pos m = pos.above();
        while (canExistIn(level.getBlockState(m))) {
            if (!level.setBlock(m, s, 2)) return;
            m = m.above();
        }
    }

    private static boolean canExistIn(int state) {
        if (B.is(state, BUBBLE_COLUMN)) return true;
        int f = B.fluid(state);
        return B.is(state, WATER) && Fluids.amount(f) >= 8 && Fluids.isSource(f);
    }

    private static int columnState(int below) {
        if (B.is(below, BUBBLE_COLUMN)) return below;
        if (B.is(below, SOUL_SAND)) return DRAG.with(B.defaultState(BUBBLE_COLUMN), false);
        return B.is(below, MAGMA_BLOCK) ? DRAG.with(B.defaultState(BUBBLE_COLUMN), true) : B.defaultState(WATER);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        level.scheduleFluidTick(pos, Fluids.T_WATER, 5);
        if (!canSurvive(state, level, pos) || dir == Dir.DOWN
                || dir == Dir.UP && !B.is(neighborState, BUBBLE_COLUMN) && canExistIn(neighborState)) {
            level.scheduleTick(pos, block, 5);
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int below = level.getBlockState(pos.below());
        return B.is(below, BUBBLE_COLUMN) || B.is(below, MAGMA_BLOCK) || B.is(below, SOUL_SAND);
    }
}
