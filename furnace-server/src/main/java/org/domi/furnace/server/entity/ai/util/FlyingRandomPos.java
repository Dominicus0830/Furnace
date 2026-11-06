// 바닐라 AirRandomPos·HoverRandomPos: 나는 몹의 무작위 목표 — 방향 쪽으로 뽑은 뒤 땅 위로 끌어올리거나(Air) 고체 속이면 위로 올린다(Hover).
package org.domi.furnace.server.entity.ai.util;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;

public final class FlyingRandomPos {
    private FlyingRandomPos() { }

    private static RandomPos.Rand rand(PathfinderMob mob) {
        var r = mob.random();
        return new RandomPos.Rand() {
            public int nextInt(int b) { return r.nextInt(b); }
            public float nextFloat() { return r.nextFloat(); }
            public double nextDouble() { return r.nextDouble(); }
        };
    }

    private static Pos towardDirection(PathfinderMob mob, int range, boolean restrict, Pos rel) {
        Pos p = RandomPos.generateRandomPosTowardDirection(mob, range, rand(mob), rel);
        return !GoalUtils.isOutsideLimits(p, mob) && !GoalUtils.isRestricted(restrict, mob, p) && !GoalUtils.isNotStable(mob.getNavigation(), p) ? p : null;
    }

    /** AirRandomPos.getPosTowards(mob, h, v, x, z, maxAngle, flyHeight, minHeight). */
    public static double[] getAirPosTowards(PathfinderMob mob, int horizontal, int vertical, double x, double z, double maxAngle, int flyHeight, int minHeight) {
        boolean restrict = GoalUtils.mobRestricted(mob, horizontal);
        var rand = rand(mob);
        return RandomPos.generateRandomPos(mob, () -> {
            Pos rel = RandomPos.generateRandomDirectionWithinRadians(rand, horizontal, vertical, 0, x, z, maxAngle);
            if (rel == null) return null;
            Pos p = towardDirection(mob, horizontal, restrict, rel);
            if (p == null) return null;
            p = RandomPos.moveUpToAboveSolid(p, mob.random().nextInt(flyHeight - minHeight + 1) + minHeight, mob.level().maxBuildHeight(), q -> GoalUtils.isSolid(mob, q));
            return !GoalUtils.isWater(mob, p) && !GoalUtils.hasMalus(mob, p) ? p : null;
        });
    }

    /** HoverRandomPos.getPos(mob, h, v, yOffset, x, z, maxAngle). */
    public static double[] getHoverPos(PathfinderMob mob, int horizontal, int vertical, int yOffset, double x, double z, double maxAngle) {
        boolean restrict = GoalUtils.mobRestricted(mob, horizontal);
        var rand = rand(mob);
        return RandomPos.generateRandomPos(mob, () -> hoverPosOnce(mob, horizontal, vertical, yOffset, x, z, maxAngle, restrict, rand));
    }

    /** AirAndWaterRandomPos.generateRandomPos 한 번. */
    private static Pos hoverPosOnce(PathfinderMob mob, int horizontal, int vertical, int yOffset, double x, double z, double maxAngle, boolean restrict, RandomPos.Rand rand) {
        Pos rel = RandomPos.generateRandomDirectionWithinRadians(rand, horizontal, vertical, yOffset, x, z, maxAngle);
        if (rel == null) return null;
        Pos p = RandomPos.generateRandomPosTowardDirection(mob, horizontal, rand, rel);
        if (GoalUtils.isOutsideLimits(p, mob) || GoalUtils.isRestricted(restrict, mob, p)) return null;
        p = RandomPos.moveUpOutOfSolid(p, mob.level().maxBuildHeight(), q -> GoalUtils.isSolid(mob, q));
        return GoalUtils.hasMalus(mob, p) ? null : p;
    }

    /** AirRandomPos.getPosTowards(mob, h, v, yOffset, target, maxAngle): 목표 쪽 방향으로 AirAndWater 한 번씩, 물이 아닌 칸. */
    public static double[] getAirPosTowardsTarget(PathfinderMob mob, int horizontal, int vertical, int yOffset, double tx, double ty, double tz, double maxAngle) {
        double x = tx - mob.x(), z = tz - mob.z();
        boolean restrict = GoalUtils.mobRestricted(mob, horizontal);
        var rand = rand(mob);
        return RandomPos.generateRandomPos(mob, () -> {
            Pos p = hoverPosOnce(mob, horizontal, vertical, yOffset, x, z, maxAngle, restrict, rand);
            return p != null && !GoalUtils.isWater(mob, p) ? p : null;
        });
    }
}
