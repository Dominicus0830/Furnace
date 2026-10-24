// 바닐라 RangedAttackMob: 원거리 공격을 하는 몹(활·석궁·삼지창 등).
package org.domi.furnace.server.entity;

public interface RangedAttackMob {
    void performRangedAttack(Targetable target, float power);
}
