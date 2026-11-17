package org.bukkit.inventory;

/** 마법 부여대 칸: 0 아이템, 1 청금석. */
public interface EnchantingInventory extends Inventory {

    void setItem(ItemStack item);

    ItemStack getItem();

    void setSecondary(ItemStack item);

    ItemStack getSecondary();
}
