// 엔티티가 월드에 들어가기 직전 — 넣는 레벨의 region 스레드에서 불린다. 취소하면 넣지 않는다. 청크 로드로 복원되는 엔티티는 해당하지 않는다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;

public final class EntitySpawnEvent extends EntityEvent implements Cancellable {

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntitySpawnEvent(Entity entity) { super(entity); }
}
