// 바닐라 MoveTowardsRestrictionGoal: 제한 구역 밖으로 나가 있으면 구역 중심 쪽으로 돌아간다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.util.DefaultRandomPos;

public class MoveTowardsRestrictionGoal extends Goal {
    private final PathfinderMob mob;
    private double wantedX, wantedY, wantedZ;
    private final double speedModifier;

    public MoveTowardsRestrictionGoal(PathfinderMob mob, double speed) {
        this.mob = mob;
        this.speedModifier = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.isWithinRestriction(mob.blockPosition())) return false;
        Pos c = mob.getRestrictCenter();
        double[] p = DefaultRandomPos.getPosTowards(mob, 16, 7, new double[] {c.x() + 0.5, c.y(), c.z() + 0.5}, (float) (Math.PI / 2));
        if (p == null) return false;
        wantedX = p[0];
        wantedY = p[1];
        wantedZ = p[2];
        return true;
    }

    @Override
    public boolean canContinueToUse() { return !mob.getNavigation().isDone(); }

    @Override
    public void start() { mob.getNavigation().moveTo(wantedX, wantedY, wantedZ, speedModifier); }
}
