package org.bukkit.craftbukkit.inventory;

import org.bukkit.inventory.InventoryHolder;

/** 양조대의 살아 있는 인벤토리(Paper 와 같은 이름 — T-264): 0~2 병, 3 재료, 4 연료. */
public class CraftInventoryBrewer extends CraftInventory implements org.bukkit.inventory.BrewerInventory {
    public CraftInventoryBrewer(org.domi.furnace.api.inventory.Inventory handle, java.util.function.Supplier<InventoryHolder> holder) {
        super(handle, org.bukkit.event.inventory.InventoryType.BREWING, holder);
    }

    @Override
    public org.bukkit.inventory.ItemStack getIngredient() { return getItem(3); }

    @Override
    public void setIngredient(org.bukkit.inventory.ItemStack ingredient) { setItem(3, ingredient); }

    @Override
    public org.bukkit.inventory.ItemStack getFuel() { return getItem(4); }

    @Override
    public void setFuel(org.bukkit.inventory.ItemStack fuel) { setItem(4, fuel); }

    @Override
    public org.bukkit.block.BrewingStand getHolder() {
        return super.getHolder() instanceof org.bukkit.block.BrewingStand b ? b : null;
    }
}
