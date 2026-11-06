// 바닐라 FollowParentGoal: 아기가 가까운 어른(같은 종류)을 따라간다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Animal;
import org.domi.furnace.server.entity.ai.Goal;

public class FollowParentGoal extends Goal {
    private final Animal animal;
    private Animal parent;
    private final double speedModifier;
    private int timeToRecalcPath;

    public FollowParentGoal(Animal animal, double speed) {
        this.animal = animal;
        this.speedModifier = speed;
    }

    @Override
    public boolean canUse() {
        if (animal.getAge() >= 0) return false;
        var list = animal.level().getEntitiesOfClass(animal.getClass(), animal.boundingBox().inflate(8.0, 4.0, 8.0), e -> true);
        Animal best = null;
        double bestD = Double.MAX_VALUE;
        for (var o : list) {
            Animal a = (Animal) o;
            if (a.getAge() < 0) continue;
            double d = dist(a);
            if (d > bestD) continue;
            bestD = d;
            best = a;
        }
        if (best == null) return false;
        if (bestD < 9.0) return false;
        parent = best;
        return true;
    }

    private double dist(Animal a) {
        double dx = animal.x() - a.x(), dy = animal.y() - a.y(), dz = animal.z() - a.z();
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public boolean canContinueToUse() {
        if (animal.getAge() >= 0) return false;
        if (!parent.isAlive()) return false;
        double d = dist(parent);
        return !(d < 9.0) && !(d > 256.0);
    }

    @Override public void start() { timeToRecalcPath = 0; }

    @Override public void stop() { parent = null; }

    @Override
    public void tick() {
        if (--timeToRecalcPath <= 0) {
            timeToRecalcPath = adjustedTickDelay(10);
            animal.getNavigation().moveTo(parent, speedModifier);
        }
    }
}
