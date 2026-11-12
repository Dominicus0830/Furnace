// 바닐라 LookAtPlayerGoal(플레이어 대상): 가까운 플레이어를 40~79틱 바라본다. 대상은 TargetingConditions(비전투·사거리·투명 보정·시야, 이 몹을 탄 플레이어 제외)로 고른다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class LookAtPlayerGoal extends Goal {
    protected final Mob mob;
    protected Player lookAt;
    protected final float lookDistance;
    private int lookTime;
    protected final float probability;
    private final boolean onlyHorizontal;
    protected final TargetingConditions lookAtContext;

    public LookAtPlayerGoal(Mob mob, float lookDistance) { this(mob, lookDistance, 0.02F, false); }

    public LookAtPlayerGoal(Mob mob, float lookDistance, float probability) { this(mob, lookDistance, probability, false); }

    public LookAtPlayerGoal(Mob mob, float lookDistance, float probability, boolean onlyHorizontal) {
        this.mob = mob;
        this.lookDistance = lookDistance;
        this.probability = probability;
        this.onlyHorizontal = onlyHorizontal;
        setFlags(EnumSet.of(Flag.LOOK));
        // 바닐라: forNonCombat().range(거리).selector(EntitySelector.notRiding(mob))
        this.lookAtContext = TargetingConditions.forNonCombat().range(lookDistance).selector(t -> notRiding(mob, t));
    }

    @Override
    public boolean canUse() {
        if (mob.random().nextFloat() >= probability) return false;
        lookAt = mob.level().getNearestPlayer(lookAtContext, mob, mob.x(), mob.eyeY(), mob.z());
        return lookAt != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (lookAt == null || lookAt.vitals().isDeadOrDying()) return false;   // 바닐라는 !isAlive 만 본다(도중에 관전자가 돼도 이어서 본다)
        double dx = lookAt.x() - mob.x(), dy = lookAt.y() - mob.y(), dz = lookAt.z() - mob.z();
        return dx * dx + dy * dy + dz * dz <= (double) (lookDistance * lookDistance) && lookTime > 0;
    }

    /** EntitySelector.notRiding(mob): 대상이 (몇 단 위든) 이 몹의 탈것 사슬에 이 몹을 탄 채가 아니다. */
    private static boolean notRiding(Mob mob, org.domi.furnace.server.entity.Targetable t) {
        if (!(t instanceof Rider r)) return true;
        for (Entity v = r.vehicle(); v != null; v = v.vehicle()) if (v == mob) return false;
        return true;
    }

    @Override public void start() { lookTime = adjustedTickDelay(40 + mob.random().nextInt(40)); }

    @Override public void stop() { lookAt = null; }

    @Override
    public void tick() {
        if (lookAt != null && !lookAt.vitals().isDeadOrDying()) {
            double y = onlyHorizontal ? mob.eyeY() : lookAt.eyeY();
            mob.getLookControl().setLookAt(lookAt.x(), y, lookAt.z());
            lookTime--;
        }
    }
}
