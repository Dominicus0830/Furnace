// 탈것(보트·광산 수레)이 주체인 이벤트의 바탕.
package org.domi.furnace.api.event.vehicle;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Event;

public abstract class VehicleEvent extends Event {

    private final Entity vehicle;

    protected VehicleEvent(Entity vehicle) { this.vehicle = vehicle; }

    public Entity vehicle() { return vehicle; }
}
