// 몹의 공격 대상이 바뀌기 직전(새 대상 또는 대상 잃음) — 몹의 region 스레드에서 불린다. 취소하면 대상을 바꾸지 않는다.
// 같은 대상을 다시 정할 때는 부르지 않는다. 브레인으로 움직이는 몹(피글린·호글린·워든 등)의 공격 기억은 아직 해당하지 않는다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;

public final class EntityTargetEvent extends EntityEvent implements Cancellable {

    private final Entity target;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntityTargetEvent(Entity entity, Entity target) {
        super(entity);
        this.target = target;
    }

    /** 새 대상(null = 대상을 잃음). */
    public Entity target() { return target; }
}
