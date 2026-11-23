package org.bukkit.craftbukkit.inventory;

import org.bukkit.inventory.ItemStack;

/** 마법 부여대(Paper 와 같은 이름 — T-264): 0 아이템, 1 청금석. */
public class CraftInventoryEnchanting extends CraftInventory implements org.bukkit.inventory.EnchantingInventory {
    public CraftInventoryEnchanting(org.domi.furnace.api.inventory.Inventory handle) {
        super(handle, org.bukkit.event.inventory.InventoryType.ENCHANTING, () -> null);
    }

    @Override
    public void setItem(ItemStack item) { setItem(0, item); }

    @Override
    public ItemStack getItem() { return getItem(0); }

    @Override
    public void setSecondary(ItemStack item) { setItem(1, item); }

    @Override
    public ItemStack getSecondary() { return getItem(1); }
}
