// 바닐라 TargetingConditions: 몹이 대상을 고를 때의 조건 — 볼 수 있는 적인지, 사거리(투명·웅크림 보정), 시야, 추가 조건.
package org.domi.furnace.server.entity.ai.targeting;

import java.util.function.Predicate;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;

public final class TargetingConditions {
    private final boolean isCombat;
    private double range = -1.0;
    private boolean checkLineOfSight = true;
    private boolean testInvisible = true;
    private Predicate<Targetable> selector;

    private TargetingConditions(boolean isCombat) { this.isCombat = isCombat; }

    public static TargetingConditions forCombat() { return new TargetingConditions(true); }

    public static TargetingConditions forNonCombat() { return new TargetingConditions(false); }

    public TargetingConditions copy() {
        TargetingConditions c = new TargetingConditions(isCombat);
        c.range = range;
        c.checkLineOfSight = checkLineOfSight;
        c.testInvisible = testInvisible;
        c.selector = selector;
        return c;
    }

    public TargetingConditions range(double r) {
        range = r;
        return this;
    }

    public TargetingConditions ignoreLineOfSight() {
        checkLineOfSight = false;
        return this;
    }

    public TargetingConditions ignoreInvisibilityTesting() {
        testInvisible = false;
        return this;
    }

    public TargetingConditions selector(Predicate<Targetable> p) {
        selector = p;
        return this;
    }

    public boolean test(Mob source, Targetable target) {
        if (source != null && source.id() == target.id()) return false;
        if (!target.canBeSeenAsEnemy()) return false;
        if (selector != null && !selector.test(target)) return false;
        if (source == null) return !isCombat || target.canBeSeenAsEnemy();   // TODO(평화 난이도): 출처 없는 조건
        if (isCombat && (!target.canBeSeenAsEnemy() || !source.canAttackType(target))) return false;   // TODO(팀): isAlliedTo
        if (range > 0.0) {
            double vis = testInvisible ? target.visibilityPercent(source) : 1.0;
            double max = Math.max(range * vis, 2.0);
            double dx = source.x() - target.x(), dy = source.y() - target.y(), dz = source.z() - target.z();
            if (dx * dx + dy * dy + dz * dz > max * max) return false;
        }
        return !(checkLineOfSight && !source.hasLineOfSight(target));
    }
}
