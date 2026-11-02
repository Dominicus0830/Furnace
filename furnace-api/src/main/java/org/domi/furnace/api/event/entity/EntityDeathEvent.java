// 엔티티가 죽음 처리에 들어간 직후 — 그 엔티티를 가진 region 스레드에서 불린다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.LivingEntity;

public final class EntityDeathEvent extends EntityEvent {

    public EntityDeathEvent(LivingEntity entity) { super(entity); }

    @Override
    public LivingEntity entity() { return (LivingEntity) super.entity(); }
}
