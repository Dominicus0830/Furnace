// 엔티티(플레이어 포함)가 다른 엔티티에 타기 직전 — 탈것의 region 스레드에서 불린다. entity() 가 타는 쪽이다.
// 취소하면 타지 못한다. 보트·광산 수레에 살아 있는 것이 탈 때는 먼저 VehicleEnterEvent 가 불리고, 그것이 취소되면 이 이벤트는 불리지 않는다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;

public final class EntityMountEvent extends EntityEvent implements Cancellable {

    private final Entity mount;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntityMountEvent(Entity entity, Entity mount) {
        super(entity);
        this.mount = mount;
    }

    /** 타게 될 탈것. */
    public Entity mount() { return mount; }
}
