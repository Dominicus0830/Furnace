// 바닐라 RandomLookAroundGoal: 가끔(2%/틱) 무작위 방향을 20~39틱 바라본다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.Goal;

public class RandomLookAroundGoal extends Goal {
    private final Mob mob;
    private double relX, relZ;
    private int lookTime;

    public RandomLookAroundGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() { return mob.random().nextFloat() < 0.02F; }

    @Override public boolean canContinueToUse() { return lookTime >= 0; }

    @Override
    public void start() {
        double angle = (Math.PI * 2) * mob.random().nextDouble();
        relX = Math.cos(angle);
        relZ = Math.sin(angle);
        lookTime = 20 + mob.random().nextInt(20);
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        lookTime--;
        mob.getLookControl().setLookAt(mob.x() + relX, mob.eyeY(), mob.z() + relZ);
    }
}
