// 바닐라 PitcherCropBlock: 나이 0~4 낭상엽 작물. 3 이상이면 두 칸이 된다. 경작지 위, 밝아야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;

public class PitcherCropBlock extends DoublePlantBlock implements BonemealableBlock {

    private static final Prop AGE = B.prop("age");
    private static final int MAX_AGE = 4, DOUBLE_PLANT_AGE_INTERSECTION = 3;
    private static final int FARMLAND = B.id("farmland");

    public PitcherCropBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return defaultState(); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (isDouble(AGE.intValue(state))) return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
        return canSurvive(state, level, pos) ? state : B.defaultState("air");
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        return isLower(state) && !sufficientLight(level, pos) ? false : super.canSurvive(state, level, pos);
    }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) { return B.is(state, FARMLAND); }

    /** entityInside: 파괴수가 지나가고 mobGriefing 이면 부순다(떨굼, 부순 엔티티 = 파괴수). */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        if (entity instanceof org.domi.furnace.server.entity.Ravager && level.gameRule("mobGriefing")) level.destroyBlockByEntity(pos, true, entity);
    }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) { return false; }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {}

    @Override
    public boolean isRandomlyTicking(int state) { return lower(state) && !isMaxAge(state); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        float speed = CropBlock.getGrowthSpeed(block, level, pos);
        if (random.nextInt((int) (25.0F / speed) + 1) == 0) grow(level, state, pos, 1);
    }

    private void grow(BlockLevel level, int state, Pos pos, int n) {
        int age = Math.min(AGE.intValue(state) + n, MAX_AGE);
        if (canGrow(level, pos, state, age)) {
            int s = AGE.withInt(state, age);
            level.setBlock(pos, s, 2);
            if (isDouble(age)) level.setBlock(pos.above(), HALF.with(s, UPPER), 3);
        }
    }

    private static boolean isUpper(int state) { return !lower(state); }

    private static boolean isLower(int state) { return lower(state); }

    private static boolean canGrowInto(BlockLevel level, Pos pos) {
        int s = level.getBlockState(pos);
        return B.isAir(s) || B.is(s, B.id("pitcher_crop"));
    }

    private static boolean sufficientLight(BlockLevel level, Pos pos) { return level.getRawBrightness(pos, 0) >= 8; }

    private static boolean isLowerHalfOf(int state, int block) { return B.is(state, block) && lower(state); }

    private static boolean isDouble(int age) { return age >= DOUBLE_PLANT_AGE_INTERSECTION; }

    private boolean canGrow(BlockLevel level, Pos pos, int state, int age) {
        return !isMaxAge(state) && sufficientLight(level, pos) && (!isDouble(age) || canGrowInto(level, pos.above()));
    }

    private static boolean isMaxAge(int state) { return AGE.intValue(state) >= MAX_AGE; }

    /** getLowerHalf: 이 칸 또는 아래 칸의 아래 반쪽. */
    private Pos lowerHalf(BlockLevel level, Pos pos, int state) {
        if (isLowerHalfOf(state, block)) return pos;
        Pos below = pos.below();
        return isLowerHalfOf(level.getBlockState(below), block) ? below : null;
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) {
        Pos p = lowerHalf(level, pos, state);
        if (p == null) return false;
        int s = level.getBlockState(p);
        return canGrow(level, p, s, AGE.intValue(s) + 1);
    }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        Pos p = lowerHalf(level, pos, state);
        if (p != null) grow(level, level.getBlockState(p), p, 1);
    }
}
