// 바닐라 DefaultRandomPos: 일반 무작위 목표 — 땅 위 칸을 뽑은 뒤 고체 속이면 위로 올리고(movePosUpOutOfSolid), 범위·제한·불안정·비용을 확인.
package org.domi.furnace.server.entity.ai.util;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;

public final class DefaultRandomPos {
    private DefaultRandomPos() { }

    private static RandomPos.Rand rand(PathfinderMob mob) {
        var r = mob.random();
        return new RandomPos.Rand() {
            public int nextInt(int b) { return r.nextInt(b); }
            public float nextFloat() { return r.nextFloat(); }
            public double nextDouble() { return r.nextDouble(); }
        };
    }

    public static double[] getPos(PathfinderMob mob, int horizontal, int vertical) {
        boolean restrict = GoalUtils.mobRestricted(mob, horizontal);
        var rand = rand(mob);
        return RandomPos.generateRandomPos(mob, () -> {
            Pos rel = RandomPos.generateRandomDirection(rand, horizontal, vertical);
            Pos p = generateRandomPosTowardDirection(mob, horizontal, restrict, rel);
            return p == null ? null : movePosUpOutOfSolid(mob, p);
        });
    }

    public static double[] getPosTowards(PathfinderMob mob, int horizontal, int vertical, double[] towards, double maxAngle) {
        double[] dir = {towards[0] - mob.x(), towards[1] - mob.y(), towards[2] - mob.z()};
        boolean restrict = GoalUtils.mobRestricted(mob, horizontal);
        return getPosInDirection(mob, horizontal, vertical, dir, restrict, maxAngle);
    }

    public static double[] getPosAway(PathfinderMob mob, int horizontal, int vertical, double[] from) {
        double[] dir = {mob.x() - from[0], mob.y() - from[1], mob.z() - from[2]};
        boolean restrict = GoalUtils.mobRestricted(mob, horizontal);
        return getPosInDirection(mob, horizontal, vertical, dir, restrict, (float) (Math.PI / 2));
    }

    private static double[] getPosInDirection(PathfinderMob mob, int horizontal, int vertical, double[] dir, boolean restrict, double maxAngle) {
        var rand = rand(mob);
        return RandomPos.generateRandomPos(mob, () -> {
            Pos rel = RandomPos.generateRandomDirectionWithinRadians(rand, horizontal, vertical, 0, dir[0], dir[2], maxAngle);
            if (rel == null) return null;
            Pos p = generateRandomPosTowardDirection(mob, horizontal, restrict, rel);
            return p == null ? null : movePosUpOutOfSolid(mob, p);
        });
    }

    public static Pos movePosUpOutOfSolid(PathfinderMob mob, Pos pos) {
        pos = RandomPos.moveUpOutOfSolid(pos, mob.level().maxBuildHeight(), p -> GoalUtils.isSolid(mob, p));
        return !GoalUtils.isOutsideLimits(pos, mob) && !GoalUtils.hasMalus(mob, pos) ? pos : null;
    }

    private static Pos generateRandomPosTowardDirection(PathfinderMob mob, int range, boolean restrict, Pos rel) {
        Pos p = RandomPos.generateRandomPosTowardDirection(mob, range, rand(mob), rel);
        return !GoalUtils.isOutsideLimits(p, mob) && !GoalUtils.isRestricted(restrict, mob, p) && !GoalUtils.isNotStable(mob.getNavigation(), p) ? p : null;
    }
}
