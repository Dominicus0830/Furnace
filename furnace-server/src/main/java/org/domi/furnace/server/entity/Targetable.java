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

    /** knockback(strength, dx, dz). */
    void knockback(double strength, double dx, double dz);

    /** 사망 메시지·로그에 쓰는 이름. */
    org.domi.furnace.api.text.Text displayName();
}
