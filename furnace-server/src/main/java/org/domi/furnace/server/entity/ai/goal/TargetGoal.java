// 바닐라 TargetGoal: 공격 대상을 고르고 유지하는 목표의 기반 — 계속 조건(사거리·시야 기억·팀), 경로 도달 가능성 캐시.
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.attribute.Attribute;

public abstract class TargetGoal extends Goal {
    private static final int EMPTY_REACH_CACHE = 0, CAN_REACH_CACHE = 1, CANT_REACH_CACHE = 2;
    protected final Mob mob;
    protected final boolean mustSee;
    private final boolean mustReach;
    private int reachCache;
    private int reachCacheTime;
    private int unseenTicks;
    protected Targetable targetMob;
    protected int unseenMemoryTicks = 60;

    public TargetGoal(Mob mob, boolean mustSee) { this(mob, mustSee, false); }

    public TargetGoal(Mob mob, boolean mustSee, boolean mustReach) {
        this.mob = mob;
        this.mustSee = mustSee;
        this.mustReach = mustReach;
    }

    @Override
    public boolean canContinueToUse() {
        Targetable target = mob.getTarget();
        if (target == null) target = targetMob;
        if (target == null) return false;
        if (!target.canBeSeenAsEnemy()) return false;   // Mob.canAttack(target)
        // TODO(팀): 같은 팀이면 계속하지 않는다
        double follow = getFollowDistance();
        double dx = mob.x() - target.x(), dy = mob.y() - target.y(), dz = mob.z() - target.z();
        if (dx * dx + dy * dy + dz * dz > follow * follow) return false;
        if (mustSee) {
            if (mob.hasLineOfSight(target)) unseenTicks = 0;
            else if (++unseenTicks > reducedTickDelay(unseenMemoryTicks)) return false;
        }
        mob.setTarget(target);
        return true;
    }

    protected double getFollowDistance() { return mob.attributeValue(Attribute.FOLLOW_RANGE); }

    @Override
    public void start() {
        reachCache = 0;
        reachCacheTime = 0;
        unseenTicks = 0;
    }

    @Override
    public void stop() {
        mob.setTarget(null);
        targetMob = null;
    }

    protected boolean canAttack(Targetable target, TargetingConditions conditions) {
        if (target == null) return false;
        if (!conditions.test(mob, target)) return false;
        if (!mob.isWithinRestriction(target.blockPosition())) return false;
        if (mustReach) {
            if (--reachCacheTime <= 0) reachCache = 0;
            if (reachCache == 0) reachCache = canReach(target) ? CAN_REACH_CACHE : CANT_REACH_CACHE;
            if (reachCache == CANT_REACH_CACHE) return false;
        }
        return true;
    }

    private boolean canReach(Targetable target) {
        reachCacheTime = reducedTickDelay(10 + mob.random().nextInt(5));
        var path = mob.getNavigation().createPath(target, 0);
        if (path == null) return false;
        var end = path.getEndNode();
        if (end == null) return false;
        int dx = end.x - (int) Math.floor(target.x()), dz = end.z - (int) Math.floor(target.z());
        return dx * dx + dz * dz <= 2.25;
    }

    public TargetGoal setUnseenMemoryTicks(int t) {
        unseenMemoryTicks = t;
        return this;
    }
}
