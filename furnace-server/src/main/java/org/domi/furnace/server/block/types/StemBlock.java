// 바닐라 StemBlock: 수박·호박 줄기. 밝으면 나이 7 까지 자라고, 다 자라면 무작위 옆 빈칸(흙·경작지 위)에 열매를 맺는다.
package org.domi.furnace.server.block.types;

import com.google.gson.JsonObject;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class StemBlock extends BushBlock implements BonemealableBlock {

    private static final Prop AGE = B.prop("age"), FACING = B.prop("facing");
    private static final int FARMLAND = B.id("farmland");
    private final int fruit, attachedStem;

    public StemBlock(int block) {
        super(block);
        JsonObject p = Blocks.get().params(block);
        this.fruit = B.id(p.get("fruit").getAsString());
        this.attachedStem = B.id(p.get("attached_stem").getAsString());
    }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) { return B.is(state, FARMLAND); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) < 9) return;
        float speed = CropBlock.getGrowthSpeed(block, level, pos);
        if (random.nextInt((int) (25.0F / speed) + 1) != 0) return;
        int age = AGE.intValue(state);
        if (age < 7) {
            B.grow(level, pos, AGE.withInt(state, age + 1), 2);
            return;
        }
        int dir = Dir.HORIZONTAL[random.nextInt(4)];
        Pos p = pos.relative(dir);
        int below = level.getBlockState(p.below());
        if (B.isAir(level.getBlockState(p)) && (B.is(below, FARMLAND) || B.isTag(below, "minecraft:dirt"))) {
            if (!B.grow(level, p, B.defaultState(fruit), 3)) return;   // 플러그인 BlockGrowEvent(열매 칸)
            level.setBlockAndUpdate(pos, FACING.with(B.defaultState(attachedStem), Dir.NAME[dir]));
        }
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return AGE.intValue(state) != 7; }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        int age = Math.min(7, AGE.intValue(state) + FrostedIceBlock.nextInt(level.random(), 2, 5));
        int s = AGE.withInt(state, age);
        if (!B.grow(level, pos, s, 2)) return;   // 플러그인 BlockGrowEvent 취소
        if (age == 7) randomTick(s, level, pos, level.random());
    }
}
