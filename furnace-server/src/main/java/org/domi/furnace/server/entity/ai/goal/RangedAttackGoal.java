// 바닐라 RangedAttackGoal: 대상이 보이면(5틱 이상) 멈추고, 아니면 다가가며 거리에 비례한 간격(min~max)으로 원거리 공격을 한다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.RangedAttackMob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;

public class RangedAttackGoal extends Goal {
    private final Mob mob;
    private final RangedAttackMob rangedAttackMob;
    private Targetable target;
    private int attackTime = -1;
    private final double speedModifier;
    private int seeTime;
    private final int attackIntervalMin, attackIntervalMax;
    private final float attackRadius, attackRadiusSqr;

    public RangedAttackGoal(RangedAttackMob mob, double speed, int interval, float radius) { this(mob, speed, interval, interval, radius); }

    public RangedAttackGoal(RangedAttackMob mob, double speed, int minInterval, int maxInterval, float radius) {
        if (!(mob instanceof Mob m)) throw new IllegalArgumentException("ArrowAttackGoal requires Mob implements RangedAttackMob");
        this.rangedAttackMob = mob;
        this.mob = m;
        this.speedModifier = speed;
        this.attackIntervalMin = minInterval;
        this.attackIntervalMax = maxInterval;
        this.attackRadius = radius;
        this.attackRadiusSqr = radius * radius;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Targetable t = mob.getTarget();
        if (t != null && t.isAlive()) {
            target = t;
            return true;
        }
        return false;
    }

    @Override public boolean canContinueToUse() { return canUse() || target.isAlive() && !mob.getNavigation().isDone(); }

    @Override
    public void stop() {
        target = null;
        seeTime = 0;
        attackTime = -1;
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        double dx = mob.x() - target.x(), dy = mob.y() - target.y(), dz = mob.z() - target.z();
        double d = dx * dx + dy * dy + dz * dz;
        boolean see = mob.hasLineOfSight(target);
        if (see) seeTime++;
        else seeTime = 0;
        if (!(d > attackRadiusSqr) && seeTime >= 5) mob.getNavigation().stop();
        else mob.getNavigation().moveTo(target, speedModifier);
        mob.getLookControl().setLookAtPlayerOrEntity(target, 30.0F, 30.0F);
        if (--attackTime == 0) {
            if (!see) return;
            float f = (float) Math.sqrt(d) / attackRadius;
            float f1 = Math.clamp(f, 0.1F, 1.0F);
            rangedAttackMob.performRangedAttack(target, f1);
            attackTime = (int) Math.floor(f * (attackIntervalMax - attackIntervalMin) + attackIntervalMin);
        } else if (attackTime < 0) {
            attackTime = (int) Math.floor(attackIntervalMin + Math.sqrt(d) / attackRadius * (attackIntervalMax - attackIntervalMin));
        }
    }
}
