// 바닐라 FlowerPotBlock: 화분(빈 화분·심긴 화분). 빈 화분에 심을 수 있는 블록 아이템을 쓰면 심고, 심긴 화분을 누르면 꺼낸다.
package org.domi.furnace.server.block.types;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockItems;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ItemStack;

public class FlowerPotBlock extends BlockBehavior {

    /** POTTED_BY_CONTENT: 심을 블록 → 심긴 화분 블록. */
    private static final Map<Integer, Integer> POTTED_BY_CONTENT = new ConcurrentHashMap<>();
    private static final int AIR = B.id("air"), FLOWER_POT = B.id("flower_pot");

    private final int potted;

    public FlowerPotBlock(int block) {
        super(block);
        this.potted = B.id(Blocks.get().params(block).get("potted").getAsString());
        POTTED_BY_CONTENT.put(potted, block);
    }

    public int potted() { return potted; }

    private boolean isEmpty() { return potted == AIR; }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        BlockItems.Entry e = stack.isEmpty() ? null : BlockItems.entry(stack.itemId());
        int target = e == null ? AIR : POTTED_BY_CONTENT.getOrDefault(e.block(), AIR);
        if (target == AIR) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!isEmpty()) return ItemInteractionResult.CONSUME;
        level.setBlock(pos, B.defaultState(target), 3);
        level.gameEvent("minecraft:block_change", pos, player, -1);
        player.awardStat("minecraft:pot_flower");
        if (!player.instabuild()) player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (isEmpty()) return InteractionResult.CONSUME;
        ItemStack item = B.stack(BlockItems.asItem(potted).replace("minecraft:", ""), 1);
        if (!player.addToInventory(item)) player.drop(item);
        level.setBlock(pos, B.defaultState(FLOWER_POT), 3);
        level.gameEvent("minecraft:block_change", pos, player, -1);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.DOWN && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
