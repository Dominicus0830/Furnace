// 바닐라 LookAtPlayerGoal(몹 대상): 가까운 같은 종류 몹을 40~79틱 바라본다(플레이어가 아닌 대상 종류).
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class LookAtMobGoal extends Goal {
    protected final Mob mob;
    protected Targetable lookAt;
    protected final Class<? extends LivingEntity> lookAtType;
    protected final float lookDistance;
    private int lookTime;
    protected final float probability;
    private final TargetingConditions lookAtContext;

    public LookAtMobGoal(Mob mob, Class<? extends LivingEntity> type, float lookDistance, float probability) {
        this.mob = mob;
        this.lookAtType = type;
        this.lookDistance = lookDistance;
        this.probability = probability;
        setFlags(EnumSet.of(Flag.LOOK));
        this.lookAtContext = TargetingConditions.forNonCombat().range(lookDistance).selector(t -> true);
    }

    @Override
    public boolean canUse() {
        if (mob.random().nextFloat() >= probability) return false;
        if (mob.getTarget() != null) {
            lookAt = mob.getTarget();
        } else {
            double best = -1.0;
            Targetable result = null;
            for (LivingEntity e : mob.level().getEntitiesOfClass(lookAtType, mob.boundingBox().inflate(lookDistance, 3.0, lookDistance), x -> true)) {
                if (!lookAtContext.test(mob, e)) continue;
                double dx = e.x() - mob.x(), dy = e.y() - mob.eyeY(), dz = e.z() - mob.z();
                double d = dx * dx + dy * dy + dz * dz;
                if (best == -1.0 || d < best) {
                    best = d;
                    result = e;
                }
            }
            lookAt = result;
        }
        return lookAt != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (lookAt == null || !lookAt.isAlive()) return false;
        double dx = lookAt.x() - mob.x(), dy = lookAt.y() - mob.y(), dz = lookAt.z() - mob.z();
        return dx * dx + dy * dy + dz * dz <= (double) (lookDistance * lookDistance) && lookTime > 0;
    }

    @Override public void start() { lookTime = adjustedTickDelay(40 + mob.random().nextInt(40)); }

    @Override public void stop() { lookAt = null; }

    @Override
    public void tick() {
        if (lookAt != null && lookAt.isAlive()) {
            mob.getLookControl().setLookAt(lookAt.x(), lookAt.eyeY(), lookAt.z());
            lookTime--;
        }
    }
}
