// 엔티티가 월드에 들어가기 직전 — 넣는 레벨의 region 스레드에서 불린다. 취소하면 넣지 않는다. 청크 로드로 복원되는 엔티티는 해당하지 않는다.
// 투사체는 ProjectileLaunchEvent, 떨어진 아이템은 ItemSpawnEvent 로 온다(둘 다 이 이벤트의 하위 — 이 이벤트로 받으면 모두 온다, Bukkit 과 같다).
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;

public class EntitySpawnEvent extends EntityEvent implements Cancellable, org.domi.furnace.api.event.cause.Caused {

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntitySpawnEvent(Entity entity) { super(entity); }

    /** 이 엔티티를 만든 원인 = {@link Entity#origin()}(점화된 TNT 면 켠 쪽, 투사체면 쏜 쪽). 엔티티 자신은 들어 있지 않다. */
    @Override
    public org.domi.furnace.api.event.cause.CauseChain causeChain() { return entity().origin(); }
}
