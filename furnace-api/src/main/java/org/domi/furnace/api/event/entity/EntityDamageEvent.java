// 엔티티가 피해를 받기 직전 — 맞는 엔티티를 가진 region 스레드에서 불린다. 취소하면 피해를 받지 않는다.
// damage 는 방어력·인챈트 적용 전 원본 피해다(최종 피해는 다음 단계). setDamage 로 바꾼 값이 이후 계산에 쓰인다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;

public final class EntityDamageEvent extends EntityEvent implements Cancellable, org.domi.furnace.api.event.cause.Caused {

    private final String cause;
    private float damage;
    private boolean cancelled;
    private org.domi.furnace.api.event.cause.CauseChain causeChain = org.domi.furnace.api.event.cause.CauseChain.NONE;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntityDamageEvent(Entity entity, String cause, float damage) {
        super(entity);
        this.cause = cause;
        this.damage = damage;
    }

    public EntityDamageEvent(Entity entity, String cause, float damage, org.domi.furnace.api.event.cause.CauseChain causeChain) {
        this(entity, cause, damage);
        if (causeChain != null) this.causeChain = causeChain;
    }

    /**
     * 누가·무엇이 피해를 줬나: 맨 앞이 직접 맞힌 것(action damage — 화살·TNT·때린 몹), 그 뒤가 그것의 원인(쏜 쪽·켠 플레이어).
     * 환경 피해(낙하·용암·굶주림 …)는 NONE — 종류는 {@link #cause()}.
     */
    @Override
    public org.domi.furnace.api.event.cause.CauseChain causeChain() { return causeChain; }

    /** 피해 종류 키(minecraft:fall, minecraft:player_attack …). */
    public String cause() { return cause; }

    public float damage() { return damage; }

    public void setDamage(float damage) {
        if (!(damage >= 0f)) throw new IllegalArgumentException("damage 는 0 이상: " + damage);
        this.damage = damage;
    }
}
