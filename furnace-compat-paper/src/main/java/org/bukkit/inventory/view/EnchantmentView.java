package org.bukkit.inventory.view;

import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.InventoryView;

/** 마법 부여대 창(1.21.1). 제안은 Furnace 가 제안을 정할 때(PrepareItemEnchantEvent)만 안다 — 그 밖에서는 UnsupportedFeature. */
public interface EnchantmentView extends InventoryView {

    @Override
    EnchantingInventory getTopInventory();

    int getEnchantmentSeed();

    void setEnchantmentSeed(int seed);

    EnchantmentOffer[] getOffers();

    void setOffers(EnchantmentOffer[] offers);
}
