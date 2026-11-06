// 살아 있는 것(플레이어 포함)이 보트·광산 수레에서 내리기 직전 — 탈것의 region 스레드에서 불린다.
// 스스로 내리는 경우만 취소할 수 있다(isCancellable). 이 이벤트가 통과하면 이어서 EntityDismountEvent 가 불린다.
package org.domi.furnace.api.event.vehicle;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;

public final class VehicleExitEvent extends VehicleEvent implements Cancellable {

    private final Entity exited;
    private final boolean cancellable;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public VehicleExitEvent(Entity vehicle, Entity exited, boolean cancellable) {
        super(vehicle);
        this.exited = exited;
        this.cancellable = cancellable;
    }

    /** 내리는 쪽. */
    public Entity exited() { return exited; }

    /** false 면 취소해도 소용없다. */
    public boolean isCancellable() { return cancellable; }
}
