// 바닐라 TargetingConditions: 몹이 대상을 고를 때의 조건 — 볼 수 있는 적인지, 사거리(투명·웅크림 보정), 시야, 추가 조건.
package org.domi.furnace.server.entity.ai.targeting;

import java.util.function.Predicate;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.entity.ai.targeting.TargetingConditions")
public final class TargetingConditions {
    private final boolean isCombat;
    private double range = -1.0;
    private boolean checkLineOfSight = true;
    private boolean testInvisible = true;
    private Predicate<Targetable> selector;
    /** 조건에 플러그인 코드가 있다 — 바닐라처럼 모든 후보에 부른다(X1 사거리 건너뛰기를 끈다, 몹 목표 M2b-2 검토 1). */
    private boolean everyCandidate;

    private TargetingConditions(boolean isCombat) { this.isCombat = isCombat; }

    public static TargetingConditions forCombat() { return new TargetingConditions(true); }

    public static TargetingConditions forNonCombat() { return new TargetingConditions(false); }

    public TargetingConditions copy() {
        TargetingConditions c = new TargetingConditions(isCombat);
        c.range = range;
        c.checkLineOfSight = checkLineOfSight;
        c.testInvisible = testInvisible;
        c.selector = selector;
        c.everyCandidate = everyCandidate;
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
        if (isPlugin(p)) everyCandidate = true;
        return this;
    }

    /** 플러그인 코드를 부르는 판정의 표시(부수 효과가 있을 수 있다 — 호출 횟수도 바닐라와 같아야 한다). */
    public interface PluginCode<T> extends Predicate<T> {}

    /** 판정 {@code p} 를 플러그인 코드 표시로 감싼다(이미 표시면 그대로). */
    public static <T> PluginCode<T> pluginCode(Predicate<T> p) { return p instanceof PluginCode<T> c ? c : p::test; }

    public static boolean isPlugin(Object p) { return p instanceof PluginCode<?>; }

    /** 판정에 플러그인 코드가 있다(합친 판정 등 표시가 사라지는 곳에서 부른다). */
    public TargetingConditions everyCandidate() {
        everyCandidate = true;
        return this;
    }

    /**
     * test 가 출처(source)에서 이 제곱 거리보다 먼 대상을 늘 거짓으로 돌려주는 한도(X1: 많은 플레이어 중 가까운 한 명을 고를 때 먼 사람의
     * 조건 검사를 건너뛰는 데 쓴다). 사거리 검사는 max(range × 보임비율, 2) 이고 보임비율은 1 을 넘지 않으므로 range ≥ 2 면 range² 가 한도다.
     * 사거리가 없거나 2 미만이면 한도 없음(+무한). 판정에 플러그인 코드가 있으면 한도 없음(바닐라는 거리보다 먼저 모든 후보에 판정을 부른다).
     */
    public double farLimitSqr() { return range >= 2.0 && !everyCandidate ? range * range : Double.POSITIVE_INFINITY; }

    public boolean test(Mob source, Targetable target) {
        if (source != null && source.id() == target.id()) return false;
        if (!target.canBeSeenByAnyone()) return false;   // 바닐라 btn.eq — 적으로 보일 수 있는가(ep)는 전투 조건에서만 본다
        if (selector != null && !selector.test(target)) return false;
        if (source == null) return !isCombat || target.canBeSeenAsEnemy();   // TODO(평화 난이도): 출처 없는 조건
        if (isCombat && (!target.canBeSeenAsEnemy() || !source.canAttackType(target) || source.isAlliedTo(target))) return false;
        if (range > 0.0) {
            double vis = testInvisible ? target.visibilityPercent(source) : 1.0;
            double max = Math.max(range * vis, 2.0);
            double dx = source.x() - target.x(), dy = source.y() - target.y(), dz = source.z() - target.z();
            if (dx * dx + dy * dy + dz * dz > max * max) return false;
        }
        return !(checkLineOfSight && !source.hasLineOfSight(target));
    }
}
