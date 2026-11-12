package org.bukkit.inventory.view;

import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;

/** 모루 창(1.21.1). 비용·이름 값은 Furnace 가 모루 결과를 정할 때(PrepareAnvilEvent)만 안다 — 그 밖에서는 UnsupportedFeature. */
public interface AnvilView extends InventoryView {

    @Override
    AnvilInventory getTopInventory();

    String getRenameText();

    int getRepairItemCountCost();

    int getRepairCost();

    int getMaximumRepairCost();

    void setRepairItemCountCost(int amount);

    void setRepairCost(int cost);

    void setMaximumRepairCost(int levels);

    boolean bypassesEnchantmentLevelRestriction();

    void bypassEnchantmentLevelRestriction(boolean bypassEnchantmentLevelRestriction);
}
