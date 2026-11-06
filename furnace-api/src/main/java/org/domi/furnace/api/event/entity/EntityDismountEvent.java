// 엔티티(플레이어 포함)가 탈것에서 내리기 직전 — 탈것의 region 스레드에서 불린다. entity() 가 내리는 쪽이다.
// 스스로 내리는 경우(웅크림·AI·명령)만 취소할 수 있다. 탈것이 사라지거나 죽어 내려야 하거나, 순간이동·차원 이동·접속 종료로 내릴 때는 isCancellable() 이 false 이고
// 취소해도 무시된다. 보트·광산 수레에서 살아 있는 것이 내릴 때는 먼저 VehicleExitEvent 가 불리고, 그것이 취소되면 이 이벤트는 불리지 않는다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;

public final class EntityDismountEvent extends EntityEvent implements Cancellable {

    private final Entity dismounted;
    private final boolean cancellable;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntityDismountEvent(Entity entity, Entity dismounted, boolean cancellable) {
        super(entity);
        this.dismounted = dismounted;
        this.cancellable = cancellable;
    }

    /** 내리게 될 탈것. */
    public Entity dismounted() { return dismounted; }

    /** false 면 취소해도 소용없다. */
    public boolean isCancellable() { return cancellable; }
}
