// 살아 있는 것(플레이어 포함)이 보트·광산 수레(상자 보트·상자 수레 포함)에 타기 직전 — 탈것의 region 스레드에서 불린다.
// 취소하면 타지 못한다. 이 이벤트가 통과하면 이어서 EntityMountEvent 가 불린다.
package org.domi.furnace.api.event.vehicle;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;

public final class VehicleEnterEvent extends VehicleEvent implements Cancellable {

    private final Entity entered;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public VehicleEnterEvent(Entity vehicle, Entity entered) {
        super(vehicle);
        this.entered = entered;
    }

    /** 타는 쪽. */
    public Entity entered() { return entered; }
}
