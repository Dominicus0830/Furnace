// 바닐라 ChiseledBookShelfBlock: 조각된 책장. 앞면 6칸(위 3·아래 3) 중 맞은 칸에 책을 꽂거나 뺀다(칸 내용은 블록 엔티티).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.block.entity.ChiseledBookShelfBlockEntity;

public class ChiseledBookShelfBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing");
    static final Prop[] SLOT_OCCUPIED = {B.prop("slot_0_occupied"), B.prop("slot_1_occupied"), B.prop("slot_2_occupied"),
            B.prop("slot_3_occupied"), B.prop("slot_4_occupied"), B.prop("slot_5_occupied")};

    public ChiseledBookShelfBlock(int block) { super(block); }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) { return new ChiseledBookShelfBlockEntity(pos, state); }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (!(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity be)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!ChiseledBookShelfBlockEntity.isBook(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int slot = getHitSlot(hit, state);
        if (slot < 0) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (SLOT_OCCUPIED[slot].is(state)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        addBook(level, pos, player, hand, be, stack, slot);
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (!(level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity be)) return InteractionResult.PASS;
        int slot = getHitSlot(hit, state);
        if (slot < 0) return InteractionResult.PASS;
        if (!SLOT_OCCUPIED[slot].is(state)) return InteractionResult.CONSUME;
        removeBook(level, pos, player, be, slot);
        return InteractionResult.sidedSuccess(false);
    }

    private static boolean enchanted(ItemStack s) { return "minecraft:enchanted_book".equals(org.domi.furnace.server.item.ItemTools.itemName(s)); }

    /** addBook: 사용 통계, 소리 고르기, consumeAndReturn(1) 을 칸에, 소리. */
    private static void addBook(BlockLevel level, Pos pos, BlockActor player, int hand, ChiseledBookShelfBlockEntity be, ItemStack stack, int slot) {
        player.awardStat("minecraft:used:" + org.domi.furnace.server.item.ItemTools.itemName(stack));
        String sound = enchanted(stack) ? "minecraft:block.chiseled_bookshelf.insert.enchanted" : "minecraft:block.chiseled_bookshelf.insert";
        ItemStack one = stack.withCount(1);
        if (!player.instabuild()) player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        be.setItem(slot, one, level);
        level.playSound(null, pos, sound, "blocks", 1.0F, 1.0F);
    }

    /** removeBook: 칸을 비워 인벤토리에(못 넣으면 떨군다), 소리, block_change. */
    private static void removeBook(BlockLevel level, Pos pos, BlockActor player, ChiseledBookShelfBlockEntity be, int slot) {
        ItemStack s = be.removeItem(slot, 1, level);
        String sound = enchanted(s) ? "minecraft:block.chiseled_bookshelf.pickup.enchanted" : "minecraft:block.chiseled_bookshelf.pickup";
        level.playSound(null, pos, sound, "blocks", 1.0F, 1.0F);
        if (!player.addToInventory(s)) player.drop(s);
        level.gameEvent("minecraft:block_change", pos, player, level.getBlockState(pos));
    }

    /** getHitSlot: 앞면을 맞혔으면 그 칸 번호(0..5), 아니면 -1. */
    static int getHitSlot(Hit hit, int state) {
        int facing = Dir.byName(FACING.valueName(state));
        if (hit.face() != facing) return -1;
        Pos rel = hit.pos().relative(facing);
        double x = hit.x() - rel.x(), y = hit.y() - rel.y(), z = hit.z() - rel.z();
        float u, v = (float) y;
        switch (facing) {
            case Dir.NORTH -> u = (float) (1.0 - x);
            case Dir.SOUTH -> u = (float) x;
            case Dir.WEST -> u = (float) z;
            case Dir.EAST -> u = (float) (1.0 - z);
            default -> { return -1; }
        }
        int row = v >= 0.5F ? 0 : 1;
        int col = u < 0.375F ? 0 : u < 0.6875F ? 1 : 2;
        return col + row * 3;
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (B.is(newState, block)) return;
        boolean spilled = false;
        if (level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity be && !be.isEmpty()) {
            for (int i = 0; i < 6; i++) {
                ItemStack s = be.getItem(i);
                if (!s.isEmpty()) level.dropItemStack(pos.x(), pos.y(), pos.z(), s);
            }
            be.clearContent();
            spilled = true;
        }
        super.onRemove(state, level, pos, newState, moved);
        if (spilled) level.updateNeighbourForOutputSignal(pos, block);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
    }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        return level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity be ? be.lastInteractedSlot() + 1 : 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }
}
