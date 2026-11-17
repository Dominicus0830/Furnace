package org.bukkit.event.inventory;

import org.bukkit.event.HandlerList;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/** 결과 칸이 있는 창(모루 등)이 결과를 정했을 때. 하위 이벤트(PrepareAnvilEvent 등)는 이 처리기 목록을 함께 쓴다(Paper 와 같다). */
public class PrepareInventoryResultEvent extends InventoryEvent {

    private static final HandlerList handlers = new HandlerList();

    private ItemStack result;

    public PrepareInventoryResultEvent(InventoryView inventory, ItemStack result) {
        super(inventory);
        this.result = result;
    }

    /** 결과 칸에 둘 아이템(비었으면 null). */
    public ItemStack getResult() { return result; }

    public void setResult(ItemStack result) { this.result = result; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
