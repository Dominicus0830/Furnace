package org.bukkit.event.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.InventoryView;

/** 창이 닫힐 때(Paper 의 닫힌 까닭 포함 — Furnace 는 까닭을 알리지 않아 늘 UNKNOWN). */
public class InventoryCloseEvent extends InventoryEvent {

    private static final HandlerList handlers = new HandlerList();

    private final Reason reason;

    public InventoryCloseEvent(InventoryView transaction) { this(transaction, Reason.UNKNOWN); }

    public InventoryCloseEvent(InventoryView transaction, Reason reason) {
        super(transaction);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }

    public final HumanEntity getPlayer() { return transaction.getPlayer(); }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    /** Paper: 닫힌 까닭. */
    public enum Reason {
        UNKNOWN, TELEPORT, CANT_USE, UNLOADED, OPEN_NEW, PLAYER, DISCONNECT, DEATH, PLUGIN
    }
}
