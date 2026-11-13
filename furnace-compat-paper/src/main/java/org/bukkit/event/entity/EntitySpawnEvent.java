package org.bukkit.event.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 엔티티가 월드에 나타나려 한다(플레이어 제외). ItemSpawnEvent·CreatureSpawnEvent 도 이 처리기 목록으로 온다. */
public class EntitySpawnEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public EntitySpawnEvent(Entity spawnee) { super(spawnee); }

    public Location getLocation() { return getEntity().getLocation(); }
}
