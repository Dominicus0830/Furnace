// 플레이어가 아닌 엔티티가 차원문(지옥문·엔드 포털·엔드 관문)에 충분히 머물러 다른 곳으로 넘어가기 직전 — 엔티티의 region 스레드에서 불린다.
// 목적지 좌표는 목적 차원 쪽에서 정해지므로 목적 차원(toWorld)만 알려 준다. 취소하면 넘어가지 않는다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.player.PlayerPortalEvent;
import org.domi.furnace.api.world.Location;

public final class EntityPortalEvent extends EntityEvent implements Cancellable {

    private final Location from;
    private final String toWorld;
    private final PlayerPortalEvent.Cause cause;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntityPortalEvent(Entity entity, Location from, String toWorld, PlayerPortalEvent.Cause cause) {
        super(entity);
        this.from = from;
        this.toWorld = toWorld;
        this.cause = cause;
    }

    public Location from() { return from; }

    public String toWorld() { return toWorld; }

    public PlayerPortalEvent.Cause cause() { return cause; }
}
