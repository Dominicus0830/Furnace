// 바닐라 SwellGoal: 크리퍼가 대상 3칸 안(제곱 9)이거나 이미 부풀었으면 멈춰 서서 부풀고, 멀어지거나 보이지 않으면 줄어든다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Creeper;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;

public class SwellGoal extends Goal {
    private final Creeper creeper;
    private Targetable target;

    public SwellGoal(Creeper creeper) {
        this.creeper = creeper;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private double distSqr(Targetable t) {
        double dx = creeper.x() - t.x(), dy = creeper.y() - t.y(), dz = creeper.z() - t.z();
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean canUse() {
        Targetable t = creeper.getTarget();
        return creeper.getSwellDir() > 0 || t != null && distSqr(t) < 9.0;
    }

    @Override
    public void start() {
        creeper.getNavigation().stop();
        target = creeper.getTarget();
    }

    @Override public void stop() { target = null; }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        if (target == null) creeper.setSwellDir(-1);
        else if (distSqr(target) > 49.0) creeper.setSwellDir(-1);
        else if (!creeper.hasLineOfSight(target)) creeper.setSwellDir(-1);
        else creeper.setSwellDir(1);
    }
}
