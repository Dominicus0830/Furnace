package org.bukkit.event.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * 플레이어가 아닌 엔티티가 텔레포트하려 한다(Paper: Entity#teleport 가 부른다). 다른 월드로 가면 Paper 처럼 두 번 불린다.
 * 취소하거나 setTo(null) 하면 옮기지 않는다.
 */
public class EntityTeleportEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private Location from;
    private Location to;
    private boolean cancelled;

    public EntityTeleportEvent(Entity what, Location from, Location to) {
        super(what);
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public Location getFrom() { return from; }

    public void setFrom(Location from) { this.from = from; }

    public Location getTo() { return to; }

    public void setTo(Location to) { this.to = to; }
}
