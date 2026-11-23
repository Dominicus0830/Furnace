// 바닐라 RangedAttackMob: 원거리 공격을 하는 몹(활·석궁·삼지창 등).
package org.domi.furnace.server.entity;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.entity.monster.RangedAttackMob")
public interface RangedAttackMob {
    void performRangedAttack(Targetable target, float power);
}
