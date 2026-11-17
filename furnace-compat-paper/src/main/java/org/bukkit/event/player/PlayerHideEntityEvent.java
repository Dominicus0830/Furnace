package org.bukkit.event.player;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** Bukkit PlayerHideEntityEvent(취소 불가). */
public class PlayerHideEntityEvent extends PlayerEvent {

    private final Entity entity;

    public PlayerHideEntityEvent(Player who, Entity entity) {
        super(who);
        this.entity = entity;
    }

    public Entity getEntity() { return entity; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
