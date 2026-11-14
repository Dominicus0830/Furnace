package org.bukkit.block;

import org.bukkit.inventory.Inventory;

/** Paper 1.21.1 Chest. getInventory 는 큰 상자면 두 칸을 합친 것, getBlockInventory 는 이 칸의 것. */
public interface Chest extends Container, com.destroystokyo.paper.loottable.LootableBlockInventory, Lidded {

    Inventory getBlockInventory();
}
