// 바닐라 CropBlock: 경작지 위 작물. 밝으면(9 이상) 무작위 틱마다 주변 경작지·같은 작물 배치에 따른 확률로 자란다. 뼛가루는 2~5 단계.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class CropBlock extends BushBlock implements BonemealableBlock {

    protected static final Prop AGE = B.prop("age");
    private static final int FARMLAND = B.id("farmland");
    private static final Prop MOISTURE = B.prop("moisture");

    public CropBlock(int block) { super(block); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) { return B.is(state, FARMLAND); }

    /** getMaxAge. */
    public int maxAge() { return AGE.size(defaultState()) - 1; }

    public int age(int state) { return AGE.intValue(state); }

    public int stateForAge(int age) { return AGE.withInt(defaultState(), age); }

    public final boolean isMaxAge(int state) { return age(state) >= maxAge(); }

    @Override
    public boolean isRandomlyTicking(int state) { return !isMaxAge(state); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) >= 9) {
            int age = age(state);
            if (age < maxAge()) {
                float speed = getGrowthSpeed(block, level, pos);
                if (random.nextInt((int) (25.0F / speed) + 1) == 0) B.grow(level, pos, stateForAge(age + 1), 2);
            }
        }
    }

    /** growCrops: 뼛가루. */
    public void growCrops(BlockLevel level, Pos pos, int state) {
        int age = Math.min(age(state) + bonemealAgeIncrease(level), maxAge());
        B.grow(level, pos, stateForAge(age), 2);
    }

    protected int bonemealAgeIncrease(BlockLevel level) { return FrostedIceBlock.nextInt(level.random(), 2, 5); }

    /** getGrowthSpeed: 아래 3×3 경작지(젖으면 3, 옆은 1/4) + 1, 같은 작물이 두 방향 줄이나 대각에 있으면 반. */
    public static float getGrowthSpeed(int block, BlockLevel level, Pos pos) {
        float speed = 1.0F;
        Pos below = pos.below();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                float f = 0.0F;
                int s = level.getBlockState(below.offset(x, 0, z));
                if (B.is(s, FARMLAND)) {
                    f = 1.0F;
                    if (MOISTURE.intValue(s) > 0) f = 3.0F;
                }
                if (x != 0 || z != 0) f /= 4.0F;
                speed += f;
            }
        }
        Pos n = pos.north(), s = pos.south(), w = pos.west(), e = pos.east();
        boolean we = is(level, w, block) || is(level, e, block);
        boolean ns = is(level, n, block) || is(level, s, block);
        if (we && ns) {
            speed /= 2.0F;
        } else if (is(level, w.north(), block) || is(level, e.north(), block) || is(level, e.south(), block) || is(level, w.south(), block)) {
            speed /= 2.0F;
        }
        return speed;
    }

    private static boolean is(BlockLevel level, Pos p, int block) { return B.is(level.getBlockState(p), block); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return hasSufficientLight(level, pos) && super.canSurvive(state, level, pos); }

    protected static boolean hasSufficientLight(BlockLevel level, Pos pos) { return level.getRawBrightness(pos, 0) >= 8; }

    /** entityInside: 파괴수가 지나가고 mobGriefing 이면 부순다(떨굼, 부순 엔티티 = 파괴수). */
    @Override
    public void entityInside(int state, BlockLevel level, Pos pos, org.domi.furnace.server.entity.Entity entity) {
        if (entity instanceof org.domi.furnace.server.entity.Ravager && level.gameRule("mobGriefing")) level.destroyBlockByEntity(pos, true, entity);
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return !isMaxAge(state); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) { growCrops(level, pos, state); }
}
