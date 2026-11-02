// 월드(차원)가 주체인 이벤트의 바탕.
package org.domi.furnace.api.event.world;

import org.domi.furnace.api.event.Event;

public abstract class WorldEvent extends Event {

    private final String world;

    protected WorldEvent(String world) { this.world = world; }

    /** 차원 키(minecraft:overworld). */
    public String world() { return world; }
}
