// 바닐라 RangedBowAttackGoal: 활을 든 몹이 대상과 거리를 두며 옆걸음치고, 시선이 닿으면 활을 당겨(20틱 이상) 쏜다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Monster;
import org.domi.furnace.server.entity.RangedAttackMob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;

public class RangedBowAttackGoal<T extends Monster & RangedAttackMob> extends Goal {
    private final T mob;
    private final double speedModifier;
    private int attackIntervalMin;
    private final float attackRadiusSqr;
    private int attackTime = -1;
    private int seeTime;
    private boolean strafingClockwise, strafingBackwards;
    private int strafingTime = -1;

    public RangedBowAttackGoal(T mob, double speed, int attackInterval, float attackRadius) {
        this.mob = mob;
        this.speedModifier = speed;
        this.attackIntervalMin = attackInterval;
        this.attackRadiusSqr = attackRadius * attackRadius;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public void setMinAttackInterval(int i) { attackIntervalMin = i; }

    @Override
    public boolean canUse() { return mob.getTarget() == null ? false : isHoldingBow(); }

    protected boolean isHoldingBow() { return mob.isHolding("minecraft:bow"); }

    @Override
    public boolean canContinueToUse() { return (canUse() || !mob.getNavigation().isDone()) && isHoldingBow(); }

    @Override
    public void start() {
        super.start();
        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        super.stop();
        mob.setAggressive(false);
        seeTime = 0;
        attackTime = -1;
        mob.stopUsingItem();
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        Targetable target = mob.getTarget();
        if (target == null) return;
        double dx = mob.x() - target.x(), dy = mob.y() - target.y(), dz = mob.z() - target.z();
        double d = dx * dx + dy * dy + dz * dz;
        boolean see = mob.hasLineOfSight(target);
        boolean seeing = seeTime > 0;
        if (see != seeing) seeTime = 0;
        if (see) seeTime++;
        else seeTime--;
        if (!(d > attackRadiusSqr) && seeTime >= 20) {
            mob.getNavigation().stop();
            strafingTime++;
        } else {
            mob.getNavigation().moveTo(target, speedModifier);
            strafingTime = -1;
        }
        if (strafingTime >= 20) {
            if (mob.random().nextFloat() < 0.3) strafingClockwise = !strafingClockwise;
            if (mob.random().nextFloat() < 0.3) strafingBackwards = !strafingBackwards;
            strafingTime = 0;
        }
        if (strafingTime > -1) {
            if (d > attackRadiusSqr * 0.75F) strafingBackwards = false;
            else if (d < attackRadiusSqr * 0.25F) strafingBackwards = true;
            mob.getMoveControl().strafe(strafingBackwards ? -0.5F : 0.5F, strafingClockwise ? 0.5F : -0.5F);
            mob.getLookControl().setLookAtPlayerOrEntity(target, 30.0F, 30.0F);
            mob.faceTarget(target, 30.0F, 30.0F);
        } else {
            mob.getLookControl().setLookAtPlayerOrEntity(target, 30.0F, 30.0F);
        }
        if (mob.isUsingItem()) {
            if (!see && seeTime < -60) {
                mob.stopUsingItem();
            } else if (see) {
                int ticks = mob.getTicksUsingItem();
                if (ticks >= 20) {
                    mob.stopUsingItem();
                    mob.performRangedAttack(target, powerForTime(ticks));
                    attackTime = attackIntervalMin;
                }
            }
        } else if (--attackTime <= 0 && seeTime >= -60) {
            mob.startUsingItem(mob.getWeaponHoldingHand("minecraft:bow"));
        }
    }

    /** BowItem.getPowerForTime: 당긴 틱 → 0~1 힘. */
    public static float powerForTime(int ticks) {
        float f = ticks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        return Math.min(f, 1.0F);
    }
}
