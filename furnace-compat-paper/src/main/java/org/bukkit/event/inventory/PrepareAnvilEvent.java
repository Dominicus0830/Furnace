package org.bukkit.event.inventory;

import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

/** 모루가 결과를 정했을 때(재료·이름이 바뀔 때마다). 비용은 {@code getView().setRepairCost(…)}. */
public class PrepareAnvilEvent extends com.destroystokyo.paper.event.inventory.PrepareResultEvent {

    public PrepareAnvilEvent(AnvilView inventory, ItemStack result) { super(inventory, result); }

    @Override
    public AnvilInventory getInventory() { return (AnvilInventory) super.getInventory(); }

    @Override
    public void setResult(ItemStack result) { super.setResult(result); }

    @Override
    public AnvilView getView() { return (AnvilView) super.getView(); }
}
