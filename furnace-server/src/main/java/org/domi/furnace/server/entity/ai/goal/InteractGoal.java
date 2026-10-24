// 바닐라 InteractGoal: LookAtPlayerGoal 과 같지만 바라보는 동안 이동도 멈춘다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Mob;

public class InteractGoal extends LookAtPlayerGoal {
    public InteractGoal(Mob mob, float lookDistance) {
        super(mob, lookDistance);
        setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
    }

    public InteractGoal(Mob mob, float lookDistance, float probability) {
        super(mob, lookDistance, probability);
        setFlags(EnumSet.of(Flag.LOOK, Flag.MOVE));
    }
}
