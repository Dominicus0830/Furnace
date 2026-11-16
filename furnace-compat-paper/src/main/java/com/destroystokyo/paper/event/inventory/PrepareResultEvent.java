package com.destroystokyo.paper.event.inventory;

import org.bukkit.event.inventory.PrepareInventoryResultEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/** Paper: 결과 칸 이벤트의 바탕. 처리기 목록은 {@link PrepareInventoryResultEvent} 의 것을 쓴다. */
public class PrepareResultEvent extends PrepareInventoryResultEvent {

    public PrepareResultEvent(InventoryView inventory, ItemStack result) { super(inventory, result); }

    @Override
    public ItemStack getResult() { return super.getResult(); }

    @Override
    public void setResult(ItemStack result) { super.setResult(result); }
}
