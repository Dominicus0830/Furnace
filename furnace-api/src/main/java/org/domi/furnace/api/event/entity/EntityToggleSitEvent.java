// 길들인 동물(늑대·고양이·앵무새)이나 여우가 앉거나 일어서기 직전에(Paper EntityToggleSitEvent) 그 엔티티의 region 스레드에서 불린다.
// 취소하면 앉은 자세가 그대로다. 저장된 엔티티를 불러올 때는 내지 않는다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;

public final class EntityToggleSitEvent extends EntityEvent implements Cancellable {

    private final boolean sitting;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntityToggleSitEvent(Entity entity, boolean sitting) {
        super(entity);
        this.sitting = sitting;
    }

    /** 앉으려 하면 true, 일어서려 하면 false. */
    public boolean sitting() { return sitting; }
}
