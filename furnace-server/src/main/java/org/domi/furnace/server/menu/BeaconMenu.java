// 바닐라 BeaconMenu(신호기, beacon) — 칸: 0 지불 아이템, 1..27 주 인벤토리, 28..36 핫바. 데이터: 0 단수, 1 첫째 효과 id+1, 2 둘째 효과 id+1(0 = 없음).
package org.domi.furnace.server.menu;

import org.domi.furnace.server.block.entity.BeaconBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.world.MenuBlockContext;

public class BeaconMenu extends AbstractContainerMenu {

    public static final int PAYMENT_SLOT = 0, INV_SLOT_START = 1, INV_SLOT_END = 28, USE_ROW_SLOT_START = 28, USE_ROW_SLOT_END = 37;

    private final int[] blockPos;
    private final BlockAccess access;
    private final BeaconBlockEntity be;
    private final Container paymentContainer;
    private final Slot paymentSlot;

    public BeaconMenu(int containerId, Container playerInventory, BeaconBlockEntity be, int[] blockPos, BlockAccess access) {
        super("minecraft:beacon", containerId);
        this.be = be;
        this.blockPos = blockPos;
        this.access = access;
        this.paymentContainer = new SimpleContainer(1) {
            @Override public int maxStackSize() { return 1; }
        };
        this.paymentSlot = addSlot(new Slot(paymentContainer, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return ItemContext.isInTag("minecraft:item", "minecraft:beacon_payment_items", stack.itemId()); }

            @Override public int getMaxStackSize() { return 1; }

            @Override public int getMaxStackSize(ItemStack stack) { return 1; }
        });
        addPlayerSlotsInline(playerInventory);
        addDataSlot(DataSlot.of(() -> be.levels, v -> be.levels = v));
        addDataSlot(DataSlot.of(() -> encode(be.primaryPower), v -> {
            var r = MenuBlockContext.current();
            be.setPrimary(decode(v), r == null ? null : r.level());
        }));
        addDataSlot(DataSlot.of(() -> encode(be.secondaryPower), v -> be.setSecondary(decode(v))));
    }

    private void addPlayerSlotsInline(Container playerInventory) {
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
    }

    /** BeaconMenu.encodeEffect: 효과 레지스트리 id + 1 (없으면 0). */
    static int encode(String effect) {
        if (effect == null) return 0;
        Integer id = ItemContext.id("minecraft:mob_effect", effect);
        return id == null ? 0 : id + 1;
    }

    /** BeaconMenu.decodeEffect. */
    static String decode(int v) { return v == 0 ? null : ItemContext.name("minecraft:mob_effect", v - 1); }

    @Override public int[] containerBlockPos() { return blockPos; }

    @Override public boolean stillValid(MenuPlayer player) {
        return access.stillBlock() && player.distanceToSqr(blockPos[0] + 0.5, blockPos[1] + 0.5, blockPos[2] + 0.5) <= 64.0;
    }

    /** 바닐라 updateEffects(set_beacon): 지불 칸에 아이템이 있으면 효과를 정하고 1개를 소모한다. */
    public void updateEffects(String primary, String secondary) {
        if (paymentSlot.hasItem()) {
            setData(1, encode(primary));
            setData(2, encode(secondary));
            paymentSlot.remove(1);
            var r = MenuBlockContext.current();
            if (r != null) r.markBlockEntityChanged(be);
        }
    }

    public boolean hasPayment() { return !paymentContainer.get(0).isEmpty(); }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        broadcastChanges();
    }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        ItemStack payment = paymentSlot.remove(paymentSlot.getMaxStackSize());
        if (!payment.isEmpty()) player.drop(payment, false);
    }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        Moved moved;
        if (index == 0) {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true);
            if (!moved.changed()) return ItemStack.EMPTY;
            slot.onQuickCraft(moved.remaining(), result);
        } else if (!paymentSlot.hasItem() && paymentSlot.mayPlace(stack) && stack.count() == 1) {
            moved = moveItemStackTo(stack, 0, 1, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else if (index >= INV_SLOT_START && index < INV_SLOT_END) {
            moved = moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else if (index >= USE_ROW_SLOT_START && index < USE_ROW_SLOT_END) {
            moved = moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        } else {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        }
        stack = moved.remaining();
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(stack);
        slot.setChanged();
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }
}
