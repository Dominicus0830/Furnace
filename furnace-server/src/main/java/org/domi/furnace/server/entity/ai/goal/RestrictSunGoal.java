// 바닐라 RestrictSunGoal: 낮에 머리 방어구가 없으면 길찾기가 햇빛 드는 칸을 피하게 한다.
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation;
import org.domi.furnace.server.entity.ai.util.GoalUtils;

public class RestrictSunGoal extends Goal {
    private final PathfinderMob mob;

    public RestrictSunGoal(PathfinderMob mob) { this.mob = mob; }

    @Override
    public boolean canUse() { return mob.level().isDay() && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && GoalUtils.hasGroundPathNavigation(mob); }

    @Override public void start() { ((GroundPathNavigation) mob.getNavigation()).setAvoidSun(true); }

    @Override
    public void stop() {
        if (GoalUtils.hasGroundPathNavigation(mob)) ((GroundPathNavigation) mob.getNavigation()).setAvoidSun(false);
    }
}
