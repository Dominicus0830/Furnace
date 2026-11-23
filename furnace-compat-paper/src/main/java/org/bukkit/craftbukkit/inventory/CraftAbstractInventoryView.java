package org.bukkit.craftbukkit.inventory;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * 창 공통 규칙(Paper 와 같은 이름 — T-264): 칸 번호 변환·칸 종류는 CraftBukkit 의 창 규칙과 같다(위쪽 칸 다음에 플레이어 주 인벤토리 27칸,
 * 그다음 핫바 9칸. 플레이어 인벤토리 창은 0 결과·1..4 제작·5..8 방어구·9..35 주·36..44 핫바·45 왼손).
 */
public abstract class CraftAbstractInventoryView implements InventoryView {

    public CraftAbstractInventoryView() {}

    @Override
    public void setItem(int slot, ItemStack item) {
        Inventory inv = getInventory(slot);
        if (inv != null) inv.setItem(convertSlot(slot), item);
        else if (item != null) throw new org.domi.furnace.compat.paper.UnsupportedFeature("InventoryView#setItem(OUTSIDE)", "Bukkit drops the item when it is put in the outside slot - Furnace has no API yet to drop it there");
    }

    @Override
    public ItemStack getItem(int slot) {
        Inventory inv = getInventory(slot);
        return inv == null ? null : inv.getItem(convertSlot(slot));
    }

    @Override
    public Inventory getInventory(int rawSlot) {
        if (rawSlot == OUTSIDE || rawSlot == -1) return null;
        if (rawSlot < 0) throw new IllegalArgumentException("Negative, non outside slot " + rawSlot);
        if (rawSlot >= countSlots()) throw new IllegalArgumentException("Slot " + rawSlot + " greater than inventory slot count");
        return rawSlot < getTopInventory().getSize() ? getTopInventory() : getBottomInventory();
    }

    @Override
    public int convertSlot(int rawSlot) {
        int numInTop = getTopInventory().getSize();
        if (rawSlot < numInTop) return rawSlot;
        int slot = rawSlot - numInTop;
        if (getType() == InventoryType.CRAFTING || getType() == InventoryType.CREATIVE) {
            if (slot < 4) return 39 - slot;   // 방어구(머리부터)
            if (slot > 39) return slot;       // 왼손(40)
            slot -= 4;
        }
        return slot >= 27 ? slot - 27 : slot + 9;   // 핫바는 창 끝에 있다
    }

    @Override
    public SlotType getSlotType(int slot) {
        if (slot < 0) return SlotType.OUTSIDE;
        if (slot < getTopInventory().getSize()) {
            return switch (getType()) {
                case BLAST_FURNACE, FURNACE, SMOKER -> slot == 2 ? SlotType.RESULT : slot == 1 ? SlotType.FUEL : SlotType.CRAFTING;
                case BREWING -> slot == 3 ? SlotType.FUEL : SlotType.CRAFTING;
                case ENCHANTING, BEACON -> SlotType.CRAFTING;
                case WORKBENCH, CRAFTING -> slot == 0 ? SlotType.RESULT : SlotType.CRAFTING;
                case ANVIL, CARTOGRAPHY, GRINDSTONE, MERCHANT -> slot == 2 ? SlotType.RESULT : SlotType.CRAFTING;
                case STONECUTTER -> slot == 1 ? SlotType.RESULT : SlotType.CRAFTING;
                case LOOM, SMITHING, SMITHING_NEW -> slot == 3 ? SlotType.RESULT : SlotType.CRAFTING;
                default -> SlotType.CONTAINER;
            };
        }
        if (getType() == InventoryType.CRAFTING) {
            if (slot < 9) return SlotType.ARMOR;
            if (slot > 35) return SlotType.QUICKBAR;
            return SlotType.CONTAINER;
        }
        return slot >= countSlots() - (9 + 4 + 1) ? SlotType.QUICKBAR : SlotType.CONTAINER;
    }

    @Override
    public void close() {
        getPlayer().closeInventory();
    }

    @Override
    public int countSlots() { return getTopInventory().getSize() + getBottomInventory().getSize(); }
}
