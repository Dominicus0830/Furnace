// 바닐라 OcelotAttackGoal: 대상에게 거리에 따라 속도(0.8/1.33/0.6)를 바꿔 다가가 20틱마다 문다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;

public class OcelotAttackGoal extends Goal {
    private final Mob mob;
    private Targetable target;
    private int attackTime;

    public OcelotAttackGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Targetable t = mob.getTarget();
        if (t == null) return false;
        target = t;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!target.isAlive()) return false;
        double dx = mob.x() - target.x(), dy = mob.y() - target.y(), dz = mob.z() - target.z();
        return dx * dx + dy * dy + dz * dz > 225.0 ? false : !mob.getNavigation().isDone() || canUse();
    }

    @Override
    public void stop() {
        target = null;
        mob.getNavigation().stop();
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        mob.getLookControl().setLookAtPlayerOrEntity(target, 30.0F, 30.0F);
        double reach = mob.bbWidth() * 2.0F * (mob.bbWidth() * 2.0F);
        double dx = mob.x() - target.x(), dy = mob.y() - target.y(), dz = mob.z() - target.z();
        double d = dx * dx + dy * dy + dz * dz;
        double speed = 0.8;
        if (d > reach && d < 16.0) speed = 1.33;
        else if (d < 225.0) speed = 0.6;
        mob.getNavigation().moveTo(target, speed);
        attackTime = Math.max(attackTime - 1, 0);
        if (!(d > reach) && attackTime <= 0) {
            attackTime = 20;
            mob.doHurtTarget(target);
        }
    }
}
