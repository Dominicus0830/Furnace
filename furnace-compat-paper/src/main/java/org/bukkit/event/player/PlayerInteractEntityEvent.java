package org.bukkit.event.player;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;

/** 플레이어가 엔티티를 오른쪽 클릭했다(손마다 한 번). 취소하면 상호작용(거래·탑승·이름표 등)이 일어나지 않는다. */
public class PlayerInteractEntityEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    protected Entity clickedEntity;
    private final EquipmentSlot hand;
    private boolean cancelled;

    public PlayerInteractEntityEvent(Player who, Entity clickedEntity) { this(who, clickedEntity, EquipmentSlot.HAND); }

    public PlayerInteractEntityEvent(Player who, Entity clickedEntity, EquipmentSlot hand) {
        super(who);
        this.clickedEntity = clickedEntity;
        this.hand = hand;
    }

    public Entity getRightClicked() { return clickedEntity; }

    /** 클릭한 손(HAND 또는 OFF_HAND). */
    public EquipmentSlot getHand() { return hand; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
