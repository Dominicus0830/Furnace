// 바닐라 MangrovePropaguleBlock: 맹그로브 번식체. 바닥에 심으면 묘목처럼 자라고, 맹그로브 잎에 매달리면(hanging) age 0..4 로 자란다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class MangrovePropaguleBlock extends SaplingBlock {

    static final Prop AGE = B.prop("age"), HANGING = B.prop("hanging");
    private static final int CLAY = B.id("clay"), MANGROVE_LEAVES = B.id("mangrove_leaves"), MANGROVE_PROPAGULE = B.id("mangrove_propagule");

    public MangrovePropaguleBlock(int block) { super(block); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) { return super.mayPlaceOn(state, level, pos) || B.is(state, CLAY); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return AGE.withInt(WATERLOGGED.with(super.getStateForPlacement(ctx), B.waterAt(ctx)), 4);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        return isHanging(state) ? B.is(level.getBlockState(pos.above()), MANGROVE_LEAVES) : super.canSurvive(state, level, pos);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return dir == Dir.UP && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!isHanging(state)) {
            if (random.nextInt(7) == 0) advanceTree(level, pos, state, random);
        } else if (!isFullyGrown(state)) {
            level.setBlock(pos, AGE.cycle(state), 2);
        }
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return !isHanging(state) || !isFullyGrown(state); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) {
        return isHanging(state) ? !isFullyGrown(state) : super.isBonemealSuccess(level, random, pos, state);
    }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        if (isHanging(state) && !isFullyGrown(state)) level.setBlock(pos, AGE.cycle(state), 2);
        else super.performBonemeal(level, random, pos, state);
    }

    private static boolean isHanging(int state) { return HANGING.is(state); }

    private static boolean isFullyGrown(int state) { return AGE.intValue(state) == 4; }

    /** createNewHangingPropagule(age): 맹그로브 잎 뼛가루가 만드는 매달린 번식체. */
    public static int createNewHangingPropagule(int age) { return AGE.withInt(HANGING.with(B.defaultState(MANGROVE_PROPAGULE), true), age); }
}
