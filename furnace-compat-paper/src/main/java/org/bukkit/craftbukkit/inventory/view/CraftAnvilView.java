package org.bukkit.craftbukkit.inventory.view;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftInventoryAnvil;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.event.inventory.InventoryType;
import org.domi.furnace.compat.paper.EventInventories;

/** 모루 창(Paper 와 같은 이름 — T-264). 비용·이름은 PrepareAnvilEvent 안에서만 안다(state). */
public class CraftAnvilView extends CraftInventoryView implements org.bukkit.inventory.view.AnvilView {
    private final CraftInventoryAnvil top;

    public CraftAnvilView(CraftPlayer player, CraftInventoryAnvil top, String title, org.domi.furnace.api.item.ItemStack cursor) {
        super(player, top, InventoryType.ANVIL, title, cursor);
        this.top = top;
    }

    @Override
    public org.bukkit.inventory.AnvilInventory getTopInventory() { return top; }

    private EventInventories.AnvilState state(String what) {
        var st = top.furnaceState();
        if (st == null) throw EventInventories.anvilUnknown(what);
        return st;
    }

    @Override
    public String getRenameText() { return state("AnvilView#getRenameText").renameText; }

    @Override
    public int getRepairItemCountCost() { throw EventInventories.anvilUnknown("AnvilView#getRepairItemCountCost"); }

    @Override
    public int getRepairCost() { return state("AnvilView#getRepairCost").repairCost; }

    @Override
    public int getMaximumRepairCost() { return top.getMaximumRepairCost(); }

    @Override
    public void setRepairItemCountCost(int amount) { throw EventInventories.anvilFixed("AnvilView#setRepairItemCountCost"); }

    @Override
    public void setRepairCost(int cost) { state("AnvilView#setRepairCost").repairCost = cost; }

    @Override
    public void setMaximumRepairCost(int levels) { top.setMaximumRepairCost(levels); }

    @Override
    public boolean bypassesEnchantmentLevelRestriction() { return false; }

    @Override
    public void bypassEnchantmentLevelRestriction(boolean bypass) {
        if (bypass) throw EventInventories.anvilFixed("AnvilView#bypassEnchantmentLevelRestriction");
    }
}
