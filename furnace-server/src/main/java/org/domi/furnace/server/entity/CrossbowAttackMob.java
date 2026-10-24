// 바닐라 CrossbowAttackMob: 석궁을 쓰는 몹(약탈자·피글린). 장전 중 표시와 쏜 뒤 처리를 맡는다.
package org.domi.furnace.server.entity;

public interface CrossbowAttackMob extends RangedAttackMob {
    void setChargingCrossbow(boolean charging);

    Targetable getTarget();

    void onCrossbowAttackPerformed();

    /** CrossbowAttackMob.performCrossbowAttack(shooter, speed): 석궁을 든 손으로 쏘고 후처리. */
    default void performCrossbowAttack(Mob shooter, float speed) {
        int hand = shooter.getWeaponHoldingHand("minecraft:crossbow");
        if (shooter.isHolding("minecraft:crossbow")) {
            Crossbow.performShooting(shooter, hand, speed, 14 - shooter.level().difficulty() * 4, getTarget());
        }
        onCrossbowAttackPerformed();
    }
}
