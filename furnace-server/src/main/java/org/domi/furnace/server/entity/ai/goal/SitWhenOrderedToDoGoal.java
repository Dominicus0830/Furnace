// 바닐라 SitWhenOrderedToDoGoal: 앉으라고 시키면(조건이 맞을 때) 멈춰 앉는다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.TamableAnimal;
import org.domi.furnace.server.entity.ai.Goal;

public class SitWhenOrderedToDoGoal extends Goal {
    private final TamableAnimal mob;

    public SitWhenOrderedToDoGoal(TamableAnimal mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override public boolean canContinueToUse() { return mob.isOrderedToSit(); }

    @Override
    public boolean canUse() {
        if (!mob.isTame()) return false;
        if (mob.isInWaterOrBubble()) return false;
        if (!mob.onGround()) return false;
        Player o = mob.getOwner();
        if (o == null) return true;
        double dx = mob.x() - o.x(), dy = mob.y() - o.y(), dz = mob.z() - o.z();
        return dx * dx + dy * dy + dz * dz < 144.0 && o.getLastHurtByMob() != null ? false : mob.isOrderedToSit();
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
        mob.setInSittingPose(true);
    }

    @Override public void stop() { mob.setInSittingPose(false); }
}
