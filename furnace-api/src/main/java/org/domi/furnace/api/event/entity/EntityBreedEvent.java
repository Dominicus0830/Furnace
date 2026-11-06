// 두 동물이 번식해 새끼를 낳기 직전 — 부모의 region 스레드에서 불린다. entity() 는 아직 월드에 없는 새끼다.
// 취소하면 새끼를 낳지 않고 부모의 사랑 상태도 그대로 둔다(다음 틱에 다시 시도할 수 있다).
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.LivingEntity;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;

public final class EntityBreedEvent extends EntityEvent implements Cancellable {

    private final LivingEntity mother;
    private final LivingEntity father;
    private final Player breeder;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntityBreedEvent(Entity child, LivingEntity mother, LivingEntity father, Player breeder) {
        super(child);
        this.mother = mother;
        this.father = father;
        this.breeder = breeder;
    }

    public LivingEntity mother() { return mother; }

    public LivingEntity father() { return father; }

    /** 먹이를 준 플레이어(없거나 접속 중이 아니면 null). */
    public Player breeder() { return breeder; }
}
