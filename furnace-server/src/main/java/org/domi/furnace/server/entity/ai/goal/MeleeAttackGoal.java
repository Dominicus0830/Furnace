// 바닐라 MeleeAttackGoal: 대상에게 다가가(경로 다시 계산 간격 4~10틱) 사거리에 들면 20틱마다 휘두르며 때린다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.world.GameMode;

public class MeleeAttackGoal extends Goal {
    protected final PathfinderMob mob;
    private final double speedModifier;
    private final boolean followingTargetEvenIfNotSeen;
    private Path path;
    private double pathedTargetX, pathedTargetY, pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private long lastCanUseCheck;
    private static final long COOLDOWN_BETWEEN_CAN_USE_CHECKS = 20L;

    public MeleeAttackGoal(PathfinderMob mob, double speed, boolean followEvenIfNotSeen) {
        this.mob = mob;
        this.speedModifier = speed;
        this.followingTargetEvenIfNotSeen = followEvenIfNotSeen;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        long now = mob.level().gameTime();
        if (now - lastCanUseCheck < COOLDOWN_BETWEEN_CAN_USE_CHECKS) return false;
        lastCanUseCheck = now;
        Targetable target = mob.getTarget();
        if (target == null) return false;
        if (!target.isAlive()) return false;
        path = mob.getNavigation().createPath(target, 0);
        return path != null ? true : mob.isWithinMeleeAttackRange(target);
    }

    @Override
    public boolean canContinueToUse() {
        Targetable target = mob.getTarget();
        if (target == null) return false;
        if (!target.isAlive()) return false;
        if (!followingTargetEvenIfNotSeen) return !mob.getNavigation().isDone();
        if (!mob.isWithinRestriction(target.blockPosition())) return false;
        return !(target instanceof Player p) || p.gameMode() != GameMode.SPECTATOR && p.gameMode() != GameMode.CREATIVE;
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(path, speedModifier);
        mob.setAggressive(true);
        ticksUntilNextPathRecalculation = 0;
        ticksUntilNextAttack = 0;
    }

    @Override
    public void stop() {
        Targetable target = mob.getTarget();
        if (target instanceof Player p && (p.gameMode() == GameMode.SPECTATOR || p.gameMode() == GameMode.CREATIVE)) mob.setTarget(null);
        mob.setAggressive(false);
        mob.getNavigation().stop();
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        Targetable target = mob.getTarget();
        if (target == null) return;
        mob.getLookControl().setLookAt(target.x(), target.eyeY(), target.z(), 30.0F, 30.0F);
        ticksUntilNextPathRecalculation = Math.max(ticksUntilNextPathRecalculation - 1, 0);
        double dxp = target.x() - pathedTargetX, dyp = target.y() - pathedTargetY, dzp = target.z() - pathedTargetZ;
        if ((followingTargetEvenIfNotSeen || mob.hasLineOfSight(target)) && ticksUntilNextPathRecalculation <= 0
                && (pathedTargetX == 0.0 && pathedTargetY == 0.0 && pathedTargetZ == 0.0 || dxp * dxp + dyp * dyp + dzp * dzp >= 1.0 || mob.random().nextFloat() < 0.05F)) {
            pathedTargetX = target.x();
            pathedTargetY = target.y();
            pathedTargetZ = target.z();
            ticksUntilNextPathRecalculation = 4 + mob.random().nextInt(7);
            double dx = mob.x() - target.x(), dy = mob.y() - target.y(), dz = mob.z() - target.z();
            double d = dx * dx + dy * dy + dz * dz;
            if (d > 1024.0) ticksUntilNextPathRecalculation += 10;
            else if (d > 256.0) ticksUntilNextPathRecalculation += 5;
            if (!mob.getNavigation().moveTo(target, speedModifier)) ticksUntilNextPathRecalculation += 15;
            ticksUntilNextPathRecalculation = adjustedTickDelay(ticksUntilNextPathRecalculation);
        }
        ticksUntilNextAttack = Math.max(ticksUntilNextAttack - 1, 0);
        checkAndPerformAttack(target);
    }

    protected void checkAndPerformAttack(Targetable target) {
        if (canPerformAttack(target)) {
            resetAttackCooldown();
            mob.swing(0);
            mob.doHurtTarget(target);
        }
    }

    protected void resetAttackCooldown() { ticksUntilNextAttack = adjustedTickDelay(20); }

    protected boolean isTimeToAttack() { return ticksUntilNextAttack <= 0; }

    protected boolean canPerformAttack(Targetable target) { return isTimeToAttack() && mob.isWithinMeleeAttackRange(target) && mob.hasLineOfSight(target); }

    protected int getTicksUntilNextAttack() { return ticksUntilNextAttack; }

    protected int getAttackInterval() { return adjustedTickDelay(20); }
}
