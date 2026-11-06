// 바닐라 NyliumBlock: 진홍·뒤틀린 나일리움. 위가 빛을 막으면 무작위 틱에 네더랙이 된다. 뼛가루로 네더 식물(지형 생성 기능).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class NyliumBlock extends BlockBehavior implements BonemealableBlock {

    private static final int NETHERRACK = B.id("netherrack"), CRIMSON = B.id("crimson_nylium"), WARPED = B.id("warped_nylium");

    public NyliumBlock(int block) { super(block); }

    private static boolean canBeNylium(BlockLevel level, Pos pos) { return !B.lightBlockedAbove(level.getBlockState(pos.above())); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canBeNylium(level, pos)) level.setBlockAndUpdate(pos, B.defaultState(NETHERRACK));
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return B.isAir(level.getBlockState(pos.above())); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        Pos above = pos.above();
        if (B.is(state, CRIMSON)) {
            if (!supported(level, "crimson_forest_vegetation_bonemeal")) return;
            place(level, "crimson_forest_vegetation_bonemeal", random, above);
        } else if (B.is(state, WARPED)) {
            // 셋 중 하나라도 미구현이면 통째로 안 한다(일부만 놓아 난수 순서를 어긋나게 하지 않는다)
            if (!supported(level, "warped_forest_vegetation_bonemeal") || !supported(level, "nether_sprouts_bonemeal")
                    || !supported(level, "twisting_vines_bonemeal")) return;
            place(level, "warped_forest_vegetation_bonemeal", random, above);
            place(level, "nether_sprouts_bonemeal", random, above);
            if (random.nextInt(8) == 0) place(level, "twisting_vines_bonemeal", random, above);
        }
    }

    private static boolean supported(BlockLevel level, String id) {
        org.domi.furnace.server.block.LevelFeature f = level.configuredFeature("minecraft:" + id);
        return f == null || f.supported();   // registry 에 없으면 바닐라도 건너뛴다
    }

    private static void place(BlockLevel level, String id, RandomSource random, Pos pos) {
        org.domi.furnace.server.block.LevelFeature f = level.configuredFeature("minecraft:" + id);
        if (f != null) level.placeFeature(f, pos, random);
    }
}
