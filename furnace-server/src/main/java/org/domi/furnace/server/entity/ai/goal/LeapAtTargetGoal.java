// 바닐라 LeapAtTargetGoal: 4~16 칸 거리의 대상에게 땅에서 뛰어 덮친다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;

public class LeapAtTargetGoal extends Goal {
    private final Mob mob;
    private Targetable target;
    private final float yd;

    public LeapAtTargetGoal(Mob mob, float yd) {
        this.mob = mob;
        this.yd = yd;
        setFlags(EnumSet.of(Flag.JUMP, Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.isPassenger()) return false;
        target = mob.getTarget();
        if (target == null) return false;
        double dx = mob.x() - target.x(), dy = mob.y() - target.y(), dz = mob.z() - target.z();
        double d = dx * dx + dy * dy + dz * dz;
        if (d < 4.0 || d > 16.0) return false;
        return mob.onGround() && mob.random().nextInt(reducedTickDelay(5)) == 0;
    }

    @Override public boolean canContinueToUse() { return !mob.onGround(); }

    @Override
    public void start() {
        double vx = target.x() - mob.x(), vz = target.z() - mob.z();
        double len2 = vx * vx + vz * vz;
        double mx = mob.vx(), mz = mob.vz();
        if (len2 > 1.0E-7) {
            double len = Math.sqrt(len2);
            vx = vx / len * 0.4 + mx * 0.2;
            vz = vz / len * 0.4 + mz * 0.2;
        }
        mob.setDeltaMovement(vx, yd, vz);
    }
}
