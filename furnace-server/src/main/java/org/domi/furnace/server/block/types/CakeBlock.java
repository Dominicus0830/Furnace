// 바닐라 CakeBlock: 케이크. 먹으면(배고프거나 크리에이티브) 한 조각씩 줄고 허기 2·포화 0.1. 안 먹은 케이크에 양초를 꽂으면 양초 케이크.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockItems;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class CakeBlock extends org.domi.furnace.server.block.BlockBehavior {

    static final Prop BITES = B.prop("bites");

    public CakeBlock(int block) { super(block); }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (B.holdsTag(stack, "minecraft:candles") && BITES.intValue(state) == 0) {
            var e = BlockItems.entry(stack.itemId());
            int candleCake = e == null ? -1 : B.id(B.BLOCKS.name(e.block()) + "_cake");
            // Paper: EntityChangeBlockEvent 를 막으면 초를 쓰지 않고 기본 상호작용(먹기)으로 넘긴다
            if (candleCake >= 0 && !B.entityChange(level, player, pos, B.defaultState(candleCake))) {
                player.resyncHands();   // Paper: 막으면 클라이언트가 미리 줄인 초를 다시 보낸다
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (candleCake >= 0) {
                if (!player.instabuild()) player.setItemInHand(hand, stack.withCount(stack.count() - 1));
                level.playSound(null, pos, "minecraft:block.cake.add_candle", "blocks", 1.0F, 1.0F);
                level.setBlockAndUpdate(pos, B.defaultState(candleCake));
                level.gameEvent("minecraft:block_change", pos, player, -1);
                player.awardStat("minecraft:used:" + org.domi.furnace.server.item.ItemTools.itemName(stack));
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) { return eat(level, pos, state, player); }

    /** eat: 한 조각. 마지막 조각이면 케이크가 사라진다(BLOCK_DESTROY). */
    protected static InteractionResult eat(BlockLevel level, Pos pos, int state, BlockActor player) {
        if (!player.canEat(false)) return InteractionResult.PASS;
        int bites = BITES.intValue(state);
        // Paper: 먹기 전에 EntityChangeBlockEvent(다음 조각, 마지막이면 그 칸의 유체) — 막으면 PASS(초 케이크는 초를 떨구지 않는다)
        if (!B.entityChange(level, player, pos, bites < 6 ? BITES.withInt(state, bites + 1) : org.domi.furnace.server.block.Fluids.createLegacyBlock(level.getFluidState(pos)))) {
            return InteractionResult.PASS;
        }
        player.awardStat("minecraft:eat_cake_slice");
        player.eat(2, 0.1F);
        level.gameEvent("minecraft:eat", pos, player, -1);
        if (bites < 6) {
            level.setBlock(pos, BITES.withInt(state, bites + 1), 3);
        } else {
            level.removeBlock(pos, false);
            level.gameEvent("minecraft:block_destroy", pos, player, -1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.DOWN && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return B.isSolid(level.getBlockState(pos.below())); }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return outputSignal(BITES.intValue(state)); }

    public static int outputSignal(int bites) { return (7 - bites) * 2; }
}
