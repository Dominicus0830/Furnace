package org.bukkit.craftbukkit.inventory;

import org.bukkit.inventory.InventoryHolder;

/** 화로류의 살아 있는 인벤토리(Paper 와 같은 이름 — T-264): 0 굽는 것, 1 연료, 2 결과. */
public class CraftInventoryFurnace extends CraftInventory implements org.bukkit.inventory.FurnaceInventory {
    private final org.domi.furnace.api.recipe.Recipe.CookingKind kind;

    public CraftInventoryFurnace(org.domi.furnace.api.inventory.Inventory handle, org.bukkit.event.inventory.InventoryType type,
            org.domi.furnace.api.recipe.Recipe.CookingKind kind, java.util.function.Supplier<InventoryHolder> holder) {
        super(handle, type, holder);
        this.kind = kind;
    }

    @Override
    public org.bukkit.inventory.ItemStack getResult() { return getItem(2); }

    @Override
    public org.bukkit.inventory.ItemStack getFuel() { return getItem(1); }

    @Override
    public org.bukkit.inventory.ItemStack getSmelting() { return getItem(0); }

    @Override
    public void setFuel(org.bukkit.inventory.ItemStack stack) { setItem(1, stack); }

    @Override
    public void setResult(org.bukkit.inventory.ItemStack stack) { setItem(2, stack); }

    @Override
    public void setSmelting(org.bukkit.inventory.ItemStack stack) { setItem(0, stack); }

    @Override
    public boolean isFuel(org.bukkit.inventory.ItemStack item) { return org.domi.furnace.compat.paper.BlockInventories.isFuel(item); }

    @Override
    public boolean canSmelt(org.bukkit.inventory.ItemStack item) { return org.domi.furnace.compat.paper.BlockInventories.canSmelt(kind, item); }

    @Override
    public org.bukkit.block.Furnace getHolder() {
        return super.getHolder() instanceof org.bukkit.block.Furnace f ? f : null;
    }
}
