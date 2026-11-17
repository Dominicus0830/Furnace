package org.bukkit.event.inventory;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 깔때기(블록·광산 수레)가 용기에서 용기로 아이템을 옮기기 직전. 취소하면 옮기지 않는다. setItem 으로 옮길 아이템을 바꾼다.
 * Furnace: 두 인벤토리는 이벤트 순간의 읽기 전용 사본이다(쓰기는 UnsupportedFeature, getHolder 는 null, getLocation 은 그 블록·수레 위치).
 */
public class InventoryMoveItemEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private boolean cancelled;
    private final Inventory sourceInventory;
    private final Inventory destinationInventory;
    private ItemStack itemStack;
    private final boolean didSourceInitiate;

    public InventoryMoveItemEvent(Inventory sourceInventory, ItemStack itemStack, Inventory destinationInventory, boolean didSourceInitiate) {
        if (itemStack == null) throw new IllegalArgumentException("ItemStack cannot be null");
        this.sourceInventory = sourceInventory;
        this.itemStack = itemStack;
        this.destinationInventory = destinationInventory;
        this.didSourceInitiate = didSourceInitiate;
    }

    public Inventory getSource() { return sourceInventory; }

    public ItemStack getItem() { return itemStack; }

    public void setItem(ItemStack itemStack) {
        if (itemStack == null) throw new IllegalArgumentException("ItemStack cannot be null. Cancel the event if you want nothing to be transferred.");
        this.itemStack = itemStack.clone();
    }

    public Inventory getDestination() { return destinationInventory; }

    /** 옮기기를 시작한 쪽(깔때기가 끌어오면 목적지, 밀어내면 출발지). */
    public Inventory getInitiator() { return didSourceInitiate ? sourceInventory : destinationInventory; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
