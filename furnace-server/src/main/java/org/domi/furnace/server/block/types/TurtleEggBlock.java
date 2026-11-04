// 바닐라 TurtleEggBlock: 한 칸에 알 1~4개. 모래 위에서 해 질 무렵(또는 1/500) 무작위 틱마다 금이 가고 부화한다. 밟으면 깨진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;

public class TurtleEggBlock extends BlockBehavior {

    private static final Prop HATCH = B.prop("hatch"), EGGS = B.prop("eggs");

    public TurtleEggBlock(int block) { super(block); }

    /** stepOn: 웅크리지 않았으면 1/100 로 알 하나를 깬다. */
    @Override
    public void stepOn(BlockLevel level, Pos pos, int state, org.domi.furnace.server.entity.Entity entity) {
        if (!entity.isSteppingCarefully()) destroyEgg(level, state, pos, canDestroyEgg(level, entity), 100);
        super.stepOn(level, pos, state, entity);
    }

    /** fallOn: 좀비(종류 포함)가 아니면 1/3 로 알 하나를 깬다. 그다음 기본 낙하 피해. */
    @Override
    public void fallOn(BlockLevel level, int state, Pos pos, org.domi.furnace.server.entity.Entity entity, float fallDistance) {
        if (!(entity instanceof org.domi.furnace.server.entity.Zombie)) destroyEgg(level, state, pos, canDestroyEgg(level, entity), 3);
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    /** 플레이어(Entity 가 아니다) 쪽 stepOn — 플레이어는 늘 깰 수 있다. */
    public void stepOnPlayer(BlockLevel level, Pos pos, int state, boolean sneaking) {
        if (!sneaking) destroyEgg(level, state, pos, true, 100);
    }

    /** 플레이어 쪽 fallOn(낙하 피해는 PlayerVitals 가 따로 준다). */
    public void fallOnPlayer(BlockLevel level, Pos pos, int state) { destroyEgg(level, state, pos, true, 3); }

    /** destroyEgg: 깰 수 있는 엔티티면 레벨 난수 nextInt(chance) == 0 일 때 알 하나 줄이기. */
    private void destroyEgg(BlockLevel level, int state, Pos pos, boolean canDestroy, int chance) {
        if (canDestroy && level.random().nextInt(chance) == 0 && B.is(state, block)) decreaseEggs(level, pos, state);
    }

    /** canDestroyEgg: 거북·박쥐는 못 깨고, 생물이면 mobGriefing 일 때(플레이어는 늘). */
    private static boolean canDestroyEgg(BlockLevel level, org.domi.furnace.server.entity.Entity entity) {
        if (entity instanceof org.domi.furnace.server.entity.Turtle || entity instanceof org.domi.furnace.server.entity.Bat) return false;
        return entity instanceof org.domi.furnace.server.entity.LivingEntity && level.gameRule("mobGriefing");
    }

    /** decreaseEggs: 깨지는 소리, 하나뿐이면 부서지고 아니면 하나 줄인다. */
    private void decreaseEggs(BlockLevel level, Pos pos, int state) {
        level.playSound(null, pos, "minecraft:entity.turtle.egg_break", "blocks", 0.7F, 0.9F + level.random().nextFloat() * 0.2F);
        int eggs = EGGS.intValue(state);
        if (eggs <= 1) {
            level.destroyBlock(pos, false);
        } else {
            level.setBlock(pos, EGGS.withInt(state, eggs - 1), 2);
            level.gameEvent("minecraft:block_destroy", pos, null, state);
            level.levelEvent(null, 2001, pos, state);
        }
    }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (shouldUpdateHatchLevel(level) && onSand(level, pos)) {
            int hatch = HATCH.intValue(state);
            if (hatch < 2) {
                level.playSound(null, pos, "minecraft:entity.turtle.egg_crack", "blocks", 0.7F, 0.9F + random.nextFloat() * 0.2F);
                level.setBlock(pos, HATCH.withInt(state, hatch + 1), 2);
                level.gameEvent("minecraft:block_change", pos, null, state);
            } else {
                level.playSound(null, pos, "minecraft:entity.turtle.egg_hatch", "blocks", 0.7F, 0.9F + random.nextFloat() * 0.2F);
                level.removeBlock(pos, false);
                level.gameEvent("minecraft:block_destroy", pos, null, state);
                for (int i = 0; i < EGGS.intValue(state); i++) {
                    level.levelEvent(null, 2001, pos, state);
                    level.spawnBabyTurtle(pos, i);
                }
            }
        }
    }

    public static boolean onSand(BlockLevel level, Pos pos) { return isSand(level, pos.below()); }

    public static boolean isSand(BlockLevel level, Pos pos) { return B.isTag(level.getBlockState(pos), "minecraft:sand"); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (onSand(level, pos)) level.levelEvent(null, 2012, pos, 15);
    }

    private static boolean shouldUpdateHatchLevel(BlockLevel level) {
        float t = B.timeOfDay(level);
        return t < 0.69 && t > 0.65 || level.random().nextInt(500) == 0;
    }

    @Override
    public void playerDestroy(BlockLevel level, BlockActor player, Pos pos, int state, Object blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        decreaseEggs(level, pos, state);
    }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) {
        return !ctx.isSecondaryUseActive() && ctx.holding(block) && EGGS.intValue(state) < 4 || super.canBeReplaced(state, ctx);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = ctx.level().getBlockState(ctx.clickedPos());
        return B.is(s, block) ? EGGS.withInt(s, Math.min(4, EGGS.intValue(s) + 1)) : super.getStateForPlacement(ctx);
    }
}
