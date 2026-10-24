// 바닐라 GoalUtils: 목표들이 쓰는 칸 판정 도우미.
package org.domi.furnace.server.entity.ai.util;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.WalkNodeEvaluator;

public final class GoalUtils {
    private GoalUtils() { }

    public static boolean hasGroundPathNavigation(Mob mob) { return mob.getNavigation() instanceof GroundPathNavigation; }

    /** mobRestricted: 제한 구역이 있고, 몹이 구역 중심에서 (반경 + radius + 1) 안에 있다(closerToCenterThan). */
    public static boolean mobRestricted(PathfinderMob mob, int radius) {
        if (!mob.hasRestriction()) return false;
        Pos c = mob.getRestrictCenter();
        double dx = c.x() + 0.5 - mob.x(), dy = c.y() + 0.5 - mob.y(), dz = c.z() + 0.5 - mob.z();
        double r = mob.getRestrictRadius() + radius + 1.0;
        return dx * dx + dy * dy + dz * dz < r * r;
    }

    public static boolean isOutsideLimits(Pos pos, PathfinderMob mob) {
        return pos.y() < mob.level().minBuildHeight() || pos.y() > mob.level().maxBuildHeight();
    }

    public static boolean isRestricted(boolean restrict, PathfinderMob mob, Pos pos) { return restrict && !mob.isWithinRestriction(pos); }

    public static boolean isNotStable(PathNavigation nav, Pos pos) { return !nav.isStableDestination(pos); }

    public static boolean isWater(PathfinderMob mob, Pos pos) { return Fluids.isWater(mob.level().getFluidState(pos)); }

    public static boolean hasMalus(PathfinderMob mob, Pos pos) {
        return mob.getPathfindingMalus(WalkNodeEvaluator.getPathTypeFromState(
                new org.domi.furnace.server.entity.ai.navigation.PathfindingContext(mob.level(), mob), pos)) != 0.0F;
    }

    public static boolean isSolid(PathfinderMob mob, Pos pos) { return B.isSolid(mob.level().getBlockState(pos)); }
}
