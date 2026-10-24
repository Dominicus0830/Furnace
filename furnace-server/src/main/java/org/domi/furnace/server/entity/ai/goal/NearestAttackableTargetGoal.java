// 바닐라 NearestAttackableTargetGoal: 가까운 대상 종류(플레이어 또는 몹 클래스)를 가끔(interval) 찾아 공격 대상으로 삼는다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import java.util.function.Predicate;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class NearestAttackableTargetGoal extends TargetGoal {
    protected final Class<?> targetType;
    protected final int randomInterval;
    protected Targetable target;
    protected TargetingConditions targetConditions;

    public NearestAttackableTargetGoal(Mob mob, Class<?> type, boolean mustSee) { this(mob, type, 10, mustSee, false, null); }

    public NearestAttackableTargetGoal(Mob mob, Class<?> type, boolean mustSee, Predicate<Targetable> selector) { this(mob, type, 10, mustSee, false, selector); }

    public NearestAttackableTargetGoal(Mob mob, Class<?> type, int interval, boolean mustSee, boolean mustReach, Predicate<Targetable> selector) {
        super(mob, mustSee, mustReach);
        this.targetType = type;
        this.randomInterval = reducedTickDelay(interval);
        setFlags(EnumSet.of(Flag.TARGET));
        this.targetConditions = TargetingConditions.forCombat().range(getFollowDistance()).selector(selector);
    }

    @Override
    public boolean canUse() {
        if (randomInterval > 0 && mob.random().nextInt(randomInterval) != 0) return false;
        findTarget();
        return target != null;
    }

    protected AABB getTargetSearchArea(double range) { return mob.boundingBox().inflate(range, 4.0, range); }

    protected void findTarget() {
        if (targetType == Player.class) {
            target = mob.level().getNearestPlayer(targetConditions, mob, mob.x(), mob.eyeY(), mob.z());
        } else {
            @SuppressWarnings("unchecked")
            Class<? extends LivingEntity> cls = (Class<? extends LivingEntity>) targetType;
            double best = -1.0;
            Targetable result = null;
            for (LivingEntity e : mob.level().getEntitiesOfClass(cls, getTargetSearchArea(getFollowDistance()), x -> true)) {
                if (!targetConditions.test(mob, e)) continue;
                double dx = e.x() - mob.x(), dy = e.y() - mob.eyeY(), dz = e.z() - mob.z();
                double d = dx * dx + dy * dy + dz * dz;
                if (best == -1.0 || d < best) {
                    best = d;
                    result = e;
                }
            }
            if (targetType == LivingEntity.class) {   // 바닐라에서는 플레이어도 LivingEntity — 플레이어도 후보
                final double[] bestBox = {best};
                final Targetable[] res = {result};
                mob.level().forEachPlayerIn(getTargetSearchArea(getFollowDistance()), p -> {
                    if (!targetConditions.test(mob, p)) return;
                    double dx = p.x() - mob.x(), dy = p.y() - mob.eyeY(), dz = p.z() - mob.z();
                    double d = dx * dx + dy * dy + dz * dz;
                    if (bestBox[0] == -1.0 || d < bestBox[0]) {
                        bestBox[0] = d;
                        res[0] = p;
                    }
                });
                result = res[0];
            }
            target = result;
        }
    }

    @Override
    public void start() {
        mob.setTarget(target);
        super.start();
    }

    public void setTarget(Targetable t) { target = t; }

    @Override protected double getFollowDistance() { return mob.attributeValue(Attribute.FOLLOW_RANGE); }
}
