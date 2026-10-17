// 바닐라 SweetBerryBushBlock: 나이 0~3. 다 자라면(2 이상) 따서 열매를 떨구고 나이 1로. 뼛가루로 자란다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;

public class SweetBerryBushBlock extends BushBlock implements BonemealableBlock {

    private static final Prop AGE = B.prop("age");

    public SweetBerryBushBlock(int block) { super(block); }

    @Override
    public boolean isRandomlyTicking(int state) { return AGE.intValue(state) < 3; }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        int age = AGE.intValue(state);
        if (age < 3 && random.nextInt(5) == 0 && level.getRawBrightness(pos.above(), 0) >= 9) {
            int s = AGE.withInt(state, age + 1);
            level.setBlock(pos, s, 2);
            level.gameEvent("minecraft:block_change", pos, null, s);
        }
    }

    // TODO(엔티티): entityInside — 끼여 느려지고 움직이면 달콤한 열매 덤불 피해 1.

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        boolean full = AGE.intValue(state) == 3;
        return !full && B.holds(stack, "minecraft:bone_meal")
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        int age = AGE.intValue(state);
        if (age > 1) {
            int n = 1 + level.random().nextInt(2);
            level.popResource(pos, B.stack("minecraft:sweet_berries", n + (age == 3 ? 1 : 0)));
            level.playSound(null, pos, "minecraft:block.sweet_berry_bush.pick_berries", "blocks", 1.0F, 0.8F + level.random().nextFloat() * 0.4F);
            int s = AGE.withInt(state, 1);
            level.setBlock(pos, s, 2);
            level.gameEvent("minecraft:block_change", pos, player, s);
            return InteractionResult.CONSUME;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return AGE.intValue(state) < 3; }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        level.setBlock(pos, AGE.withInt(state, Math.min(3, AGE.intValue(state) + 1)), 2);
    }
}
