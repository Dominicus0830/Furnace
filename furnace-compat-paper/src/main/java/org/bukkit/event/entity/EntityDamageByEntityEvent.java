package org.bukkit.event.entity;

import org.bukkit.entity.Entity;

/**
 * 엔티티가 다른 엔티티에게 피해를 받기 직전. 처리기 목록은 EntityDamageEvent 의 것을 나눠 쓴다(Bukkit 과 같다 — 그래서 EntityDamageEvent
 * 처리기도 이 이벤트를 받는다). damager 는 직접 맞힌 엔티티(화살·TNT·때린 몹·플레이어)다.
 */
public class EntityDamageByEntityEvent extends EntityDamageEvent {

    private final Entity damager;
    private final boolean critical;

    public EntityDamageByEntityEvent(Entity damager, Entity damagee, DamageCause cause, double damage) { this(damager, damagee, cause, damage, false); }

    /** 호환 모듈 전용(치명타 여부를 아는 다리). */
    public EntityDamageByEntityEvent(Entity damager, Entity damagee, DamageCause cause, double damage, boolean critical) {
        super(damagee, cause, damage);
        this.damager = damager;
        this.critical = critical;
    }

    public Entity getDamager() { return damager; }

    /** 치명타(점프 공격)였는가. Furnace 이벤트가 알리지 않아 다리에서는 늘 false. */
    public boolean isCritical() { return critical; }
}
