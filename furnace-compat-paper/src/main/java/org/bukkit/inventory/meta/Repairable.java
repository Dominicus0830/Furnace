package org.bukkit.inventory.meta;

public interface Repairable extends ItemMeta {

    boolean hasRepairCost();

    int getRepairCost();

    void setRepairCost(int cost);
}
