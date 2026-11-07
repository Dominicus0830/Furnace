// 바닐라 HurtByTargetGoal: 마지막으로 나를 때린 몹을 대상으로 삼고, 같은 종류 몹에게 알린다(alertSameType).
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.TamableAnimal;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class HurtByTargetGoal extends TargetGoal {
    private static final TargetingConditions HURT_BY_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();
    private boolean alertSameType;
    private int timestamp;
    private final Class<?>[] toIgnoreDamage;
    private Class<?>[] toIgnoreAlert;

    public HurtByTargetGoal(PathfinderMob mob, Class<?>... ignoreDamage) {
        super(mob, true);
        this.toIgnoreDamage = ignoreDamage;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        int ts = mob.getLastHurtByMobTimestamp();
        Targetable last = mob.getLastHurtByMob();
        if (ts != timestamp && last != null) {
            if (last.isPlayer() && mob.level().gameRule("universalAnger")) return false;
            for (Class<?> c : toIgnoreDamage) if (c.isAssignableFrom(last.getClass())) return false;
            return canAttack(last, HURT_BY_TARGETING);
        }
        return false;
    }

    public HurtByTargetGoal setAlertOthers(Class<?>... ignoreAlert) {
        alertSameType = true;
        toIgnoreAlert = ignoreAlert;
        return this;
    }

    @Override
    public void start() {
        mob.setTarget(mob.getLastHurtByMob());
        targetMob = mob.getTarget();
        timestamp = mob.getLastHurtByMobTimestamp();
        unseenMemoryTicks = 300;
        if (alertSameType) alertOthers();
        super.start();
    }

    protected void alertOthers() {
        double d = getFollowDistance();
        AABB box = new AABB(mob.x(), mob.y(), mob.z(), mob.x() + 1.0, mob.y() + 1.0, mob.z() + 1.0).inflate(d, 10.0, d);
        for (Mob other : mob.level().getEntitiesOfClass(mob.getClass(), box, x -> true)) {
            if (mob != other && other.getTarget() == null
                    && !(mob instanceof TamableAnimal t && other instanceof TamableAnimal o && t.getOwner() != o.getOwner())   // 주인이 다른 길들인 동물은 돕지 않는다
                    && !other.isAlliedTo(mob.getLastHurtByMob())) {
                boolean ignored = false;
                if (toIgnoreAlert != null) for (Class<?> c : toIgnoreAlert) if (other.getClass() == c) ignored = true;
                if (!ignored) alertOther(other, mob.getLastHurtByMob());
            }
        }
    }

    protected void alertOther(Mob other, Targetable target) { other.setTarget(target); }

    @Override protected double getFollowDistance() { return mob.attributeValue(Attribute.FOLLOW_RANGE); }
}
