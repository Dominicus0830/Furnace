// 몹이 겨냥하고 때릴 수 있는 것(바닐라 LivingEntity 로서의 플레이어·몹). 플레이어는 Entity 가 아니므로 공통 인터페이스로 묶는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.Pos;

public interface Targetable {
    int id();

    double x();

    double y();

    double z();

    double eyeY();

    float bbWidth();

    float bbHeight();

    AABB boundingBox();

    Pos blockPosition();

    boolean isAlive();

    /** canBeSeenAsEnemy: 무적(크리에이티브)·관전자가 아니고 살아 있다. */
    boolean canBeSeenAsEnemy();

    /** getVisibilityPercent(looker): 웅크리면 0.8, 투명하면 방어구에 가려진 정도. */
    double visibilityPercent(Entity looker);

    boolean isPlayer();

    /** 겨냥한 몹이 때린다(hurt). 받았으면 true. */
    boolean hurtByMob(Entity.Damage source, float amount, Mob attacker);

    /** setDeltaMovement(getDeltaMovement().add(...)) — 플레이어는 움직임 패킷으로 보낸다. */
    default void addDeltaMovement(double dx, double dy, double dz) {}

    /** knockback(strength, dx, dz). */
    void knockback(double strength, double dx, double dz);

    /** 사망 메시지·로그에 쓰는 이름. */
    org.domi.furnace.api.text.Text displayName();

    /** getLastHurtByMob/타임스탬프, getLastHurtMob/타임스탬프 — 주인의 싸움을 따라 하는 목표들이 읽는다. */
    default Targetable getLastHurtByMob() { return null; }

    default int getLastHurtByMobTimestamp() { return 0; }

    default Targetable getLastHurtMob() { return null; }

    default int getLastHurtMobTimestamp() { return 0; }

    /** Entity.deflection(projectile): 맞은 투사체를 튕겨 내는 방식(브리즈만 다르다). */
    default Projectile.Deflection projectileDeflection(Projectile p) { return Projectile.Deflection.NONE; }

    /** 투사체가 맞출 수 있나(Entity.isPickable & !spectator). */
    default boolean isPickable() { return isAlive(); }
}
