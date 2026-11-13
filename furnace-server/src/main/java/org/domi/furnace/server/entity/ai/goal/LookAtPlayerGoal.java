// 바닐라 LookAtPlayerGoal(플레이어 대상): 조건(비전투·거리·투명 보정·시야·나를 타고 있지 않음)에 맞는 가장 가까운 플레이어를 40~79틱 바라본다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class LookAtPlayerGoal extends Goal {
    protected final Mob mob;
    protected Player lookAt;
    protected final float lookDistance;
    private int lookTime;
    protected final float probability;
    private final boolean onlyHorizontal;
    /** forNonCombat().range(거리).selector(EntitySelector.notRiding(mob)). */
    protected final TargetingConditions lookAtContext;

    public LookAtPlayerGoal(Mob mob, float lookDistance) { this(mob, lookDistance, 0.02F, false); }

    public LookAtPlayerGoal(Mob mob, float lookDistance, float probability) { this(mob, lookDistance, probability, false); }

    public LookAtPlayerGoal(Mob mob, float lookDistance, float probability, boolean onlyHorizontal) {
        this.mob = mob;
        this.lookDistance = lookDistance;
        this.probability = probability;
        this.onlyHorizontal = onlyHorizontal;
        setFlags(EnumSet.of(Flag.LOOK));
        this.lookAtContext = TargetingConditions.forNonCombat().range(lookDistance).selector(t -> notRiding(mob, t));
    }

    /** EntitySelector.notRiding(mob): 대상이 (탈것을 거슬러 올라가며) 이 몹을 타고 있지 않다. */
    static boolean notRiding(Mob mob, Targetable target) {
        Entity v = target instanceof Player p ? p.vehicle() : target instanceof Entity e ? e.vehicle() : null;
        for (; v != null; v = v.vehicle()) if (v == mob) return false;
        return true;
    }

    @Override
    public boolean canUse() {
        if (mob.random().nextFloat() >= probability) return false;
        lookAt = mob.level().getNearestPlayer(lookAtContext, mob, mob.x(), mob.eyeY(), mob.z());
        return lookAt != null;
    }

    /** 바닐라는 살아 있는지·거리·남은 시간만 본다(바라보는 중에 관전자가 되어도 계속 본다). */
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
