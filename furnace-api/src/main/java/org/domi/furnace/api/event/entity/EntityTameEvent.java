// 늑대·고양이·앵무새·말 등을 길들이기 직전(길들이기 확률에 성공한 뒤) — 그 엔티티의 region 스레드에서 불린다. 취소하면 실패로 처리한다(연기 입자).
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;

public final class EntityTameEvent extends EntityEvent implements Cancellable {

    private final Player owner;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntityTameEvent(Entity entity, Player owner) {
        super(entity);
        this.owner = owner;
    }

    public Player owner() { return owner; }
}
