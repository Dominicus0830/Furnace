// 엔티티가 주체인 이벤트의 바탕. entity() 는 이벤트 시점 스냅샷 핸들이다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Event;

public abstract class EntityEvent extends Event {

    private final Entity entity;

    protected EntityEvent(Entity entity) { this.entity = entity; }

    public Entity entity() { return entity; }
}
