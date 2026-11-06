// 바닐라 TemptGoal: 먹이를 든 플레이어(10칸 안)를 따라간다. 가까이 가면 멈춘다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import java.util.function.Predicate;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class TemptGoal extends Goal {
    private static final TargetingConditions TEMP_TARGETING = TargetingConditions.forNonCombat().range(10.0).ignoreLineOfSight();
    private final TargetingConditions targetingConditions;
    protected final PathfinderMob mob;
    private final double speedModifier;
    private double px, py, pz, pRotX, pRotY;
    protected Player player;
    private int calmDown;
    private boolean isRunning;
    private final Predicate<ItemStack> items;
    private final boolean canScare;

    public TemptGoal(PathfinderMob mob, double speed, Predicate<ItemStack> items, boolean canScare) {
        this.mob = mob;
        this.speedModifier = speed;
        this.items = items;
        this.canScare = canScare;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.targetingConditions = TEMP_TARGETING.copy().selector(t -> t instanceof Player p && shouldFollow(p));
    }

    private boolean shouldFollow(Player p) {
        var inv = p.inventory();
        return items.test(inv.get(inv.heldWindowSlot())) || items.test(inv.get(45));
    }

    @Override
    public boolean canUse() {
        if (calmDown > 0) {
            calmDown--;
            return false;
        }
        player = mob.level().getNearestPlayer(targetingConditions, mob, mob.x(), mob.y(), mob.z());
        return player != null;
    }

    private double distSqr(Player p) {
        double dx = mob.x() - p.x(), dy = mob.y() - p.y(), dz = mob.z() - p.z();
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean canContinueToUse() {
        if (canScare()) {
            if (distSqr(player) < 36.0) {
                double dx = player.x() - px, dy = player.y() - py, dz = player.z() - pz;
                if (dx * dx + dy * dy + dz * dz > 0.010000000000000002) return false;
                if (Math.abs(player.pitch() - pRotX) > 5.0 || Math.abs(player.yaw() - pRotY) > 5.0) return false;
            } else {
                px = player.x();
                py = player.y();
                pz = player.z();
            }
            pRotX = player.pitch();
            pRotY = player.yaw();
        }
        return canUse();
    }

    protected boolean canScare() { return canScare; }

    @Override
    public void start() {
        px = player.x();
        py = player.y();
        pz = player.z();
        isRunning = true;
    }

    @Override
    public void stop() {
        player = null;
        mob.getNavigation().stop();
        calmDown = reducedTickDelay(100);
        isRunning = false;
    }

    @Override
    public void tick() {
        mob.getLookControl().setLookAtPlayer(player, mob.getMaxHeadYRot() + 20, mob.getMaxHeadXRot());
        if (distSqr(player) < 6.25) mob.getNavigation().stop();
        else mob.getNavigation().moveTo(player, speedModifier);
    }

    public boolean isRunning() { return isRunning; }
}
