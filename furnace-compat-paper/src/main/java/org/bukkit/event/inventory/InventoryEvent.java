package org.bukkit.event.inventory;

import java.util.List;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/** 창 하나(InventoryView)에 대한 이벤트. getInventory() 는 위쪽 인벤토리다. */
public class InventoryEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    protected InventoryView transaction;

    public InventoryEvent(InventoryView transaction) { this.transaction = transaction; }

    public Inventory getInventory() { return transaction.getTopInventory(); }

    public List<HumanEntity> getViewers() { return transaction.getTopInventory().getViewers(); }

    public InventoryView getView() { return transaction; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
