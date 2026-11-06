// 바닐라 MoveTowardsTargetGoal: 공격 대상이 maxDistance 안에 있으면 그쪽으로 다가가는 위치를 정한다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;

public class MoveTowardsTargetGoal extends Goal {
    private final PathfinderMob mob;
    private Targetable target;
    private double wantedX, wantedY, wantedZ;
    private final double speedModifier;
    private final float within;

    public MoveTowardsTargetGoal(PathfinderMob mob, double speed, float within) {
        this.mob = mob;
        this.speedModifier = speed;
        this.within = within;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        target = mob.getTarget();
        if (target == null) return false;
        if (distSqr(target) > within * within) return false;
        double[] p = LandRandomPos.getPosTowards(mob, 16, 7, new double[] {target.x(), target.y(), target.z()}, (float) (Math.PI / 2));
        if (p == null) return false;
        wantedX = p[0];
        wantedY = p[1];
        wantedZ = p[2];
        return true;
    }

    private double distSqr(Targetable t) {
        double dx = t.x() - mob.x(), dy = t.y() - mob.y(), dz = t.z() - mob.z();
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean canContinueToUse() { return !mob.getNavigation().isDone() && target.isAlive() && distSqr(target) < within * within; }

    @Override public void stop() { target = null; }

    @Override public void start() { mob.getNavigation().moveTo(wantedX, wantedY, wantedZ, speedModifier); }
}
