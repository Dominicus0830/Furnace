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
    /** 바닐라 대상 클래스를 서버 클래스 + 종류 조건으로 옮긴 것(몹 목표 M2b — 서버 몹은 늘 참). */
    private final java.util.function.Predicate<? super LivingEntity> typeFilter;
    private final boolean onlyHorizontal;

    public LookAtMobGoal(Mob mob, Class<? extends LivingEntity> type, float lookDistance, float probability) {
        this(mob, type, e -> true, lookDistance, probability, false);
    }

    public LookAtMobGoal(Mob mob, Class<? extends LivingEntity> type, java.util.function.Predicate<? super LivingEntity> typeFilter, float lookDistance,
            float probability, boolean onlyHorizontal) {
        this.mob = mob;
        this.lookAtType = type;
        this.typeFilter = typeFilter;
        this.lookDistance = lookDistance;
        this.probability = probability;
        this.onlyHorizontal = onlyHorizontal;
        setFlags(EnumSet.of(Flag.LOOK));
        this.lookAtContext = TargetingConditions.forNonCombat().range(lookDistance).selector(t -> true);
    }

    /** 바닐라: 공격 대상이 있으면 lookAt 에 넣지만 곧바로 가장 가까운 그 종류 엔티티로 덮어쓴다(Paper 1.21.1 jar javap) — 늘 찾는다. */
    @Override
    public boolean canUse() {
        if (mob.random().nextFloat() >= probability) return false;
        double best = -1.0;
        Targetable result = null;
        for (LivingEntity e : mob.level().getEntitiesOfClass(lookAtType, mob.boundingBox().inflate(lookDistance, 3.0, lookDistance), x -> true)) {
            if (!typeFilter.test(e) || !lookAtContext.test(mob, e)) continue;
            double dx = e.x() - mob.x(), dy = e.y() - mob.eyeY(), dz = e.z() - mob.z();
            double d = dx * dx + dy * dy + dz * dz;
            if (best == -1.0 || d < best) {
                best = d;
                result = e;
            }
        }
        lookAt = result;
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
            double y = onlyHorizontal ? mob.eyeY() : lookAt.eyeY();
            mob.getLookControl().setLookAt(lookAt.x(), y, lookAt.z());
            lookTime--;
        }
    }
}
