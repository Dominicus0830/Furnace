// 바닐라 FloatGoal: 물·용암에 잠기면 가끔 뛰어 떠오른다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.Goal;

public class FloatGoal extends Goal {
    private final Mob mob;

    public FloatGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.JUMP));
        mob.getNavigation().setCanFloat(true);
    }

    @Override
    public boolean canUse() {
        return mob.isInWater() && mob.fluidHeight(true) > mob.getFluidJumpThreshold() || mob.isInLava();
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        if (mob.random().nextFloat() < 0.8F) mob.getJumpControl().jump();
    }
}
