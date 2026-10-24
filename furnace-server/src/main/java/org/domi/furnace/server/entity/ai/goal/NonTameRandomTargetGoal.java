// 바닐라 NonTameRandomTargetGoal: 길들여지지 않은 동안만 무작위로 대상을 찾는다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.function.Predicate;
import org.domi.furnace.server.entity.TamableAnimal;
import org.domi.furnace.server.entity.Targetable;

public class NonTameRandomTargetGoal extends NearestAttackableTargetGoal {
    private final TamableAnimal tamableMob;

    public NonTameRandomTargetGoal(TamableAnimal mob, Class<?> type, boolean mustSee, Predicate<Targetable> selector) {
        super(mob, type, 10, mustSee, false, selector);
        this.tamableMob = mob;
    }

    @Override public boolean canUse() { return !tamableMob.isTame() && super.canUse(); }

    @Override
    public boolean canContinueToUse() {
        return targetConditions != null ? targetConditions.test(mob, target) : super.canContinueToUse();
    }
}
