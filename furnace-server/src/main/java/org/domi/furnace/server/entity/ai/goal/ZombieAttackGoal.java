// 바닐라 ZombieAttackGoal: 근접 공격 + 팔을 드는 자세(공격 중 플래그)를 공격 직전 절반 구간에 켠다.
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.entity.Zombie;

public class ZombieAttackGoal extends MeleeAttackGoal {
    private final Zombie zombie;
    private int raiseArmTicks;

    public ZombieAttackGoal(Zombie zombie, double speed, boolean followEvenIfNotSeen) {
        super(zombie, speed, followEvenIfNotSeen);
        this.zombie = zombie;
    }

    @Override
    public void start() {
        super.start();
        raiseArmTicks = 0;
    }

    @Override
    public void stop() {
        super.stop();
        zombie.setAggressive(false);
    }

    @Override
    public void tick() {
        super.tick();
        raiseArmTicks++;
        if (raiseArmTicks >= 5 && getTicksUntilNextAttack() < getAttackInterval() / 2) zombie.setAggressive(true);
        else zombie.setAggressive(false);
    }
}
