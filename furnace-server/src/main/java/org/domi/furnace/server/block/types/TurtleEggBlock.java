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

    // TODO(엔티티): stepOn(1/100)·fallOn(1/3) — 거북·박쥐가 아닌 생물이 밟으면 destroyEgg.

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
