// 바닐라 LandRandomPos: 땅 위의 무작위 걷기 목표. 범위 밖·제한 구역 밖·불안정한 바닥·비용이 붙는 칸은 버린다.
package org.domi.furnace.server.entity.ai.util;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;

public final class LandRandomPos {
    private LandRandomPos() { }

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
            return generateRandomPosTowardDirection(mob, horizontal, restrict, rel);
        });
    }

    public static double[] getPosTowards(PathfinderMob mob, int horizontal, int vertical, double[] towards, double maxAngle) {
        double[] dir = {towards[0] - mob.x(), towards[1] - mob.y(), towards[2] - mob.z()};
        boolean restrict = GoalUtils.mobRestricted(mob, horizontal);
        var rand = rand(mob);
        return RandomPos.generateRandomPos(mob, () -> {
            Pos rel = RandomPos.generateRandomDirectionWithinRadians(rand, horizontal, vertical, 0, dir[0], dir[2], maxAngle);
            return rel == null ? null : generateRandomPosTowardDirection(mob, horizontal, restrict, rel);
        });
    }

    public static double[] getPosAway(PathfinderMob mob, int horizontal, int vertical, double[] from) {
        double[] dir = {mob.x() - from[0], mob.y() - from[1], mob.z() - from[2]};
        boolean restrict = GoalUtils.mobRestricted(mob, horizontal);
        var rand = rand(mob);
        return RandomPos.generateRandomPos(mob, () -> {
            Pos rel = RandomPos.generateRandomDirectionWithinRadians(rand, horizontal, vertical, 0, dir[0], dir[2], (float) (Math.PI / 2));
            return rel == null ? null : generateRandomPosTowardDirection(mob, horizontal, restrict, rel);
        });
    }

    private static Pos generateRandomPosTowardDirection(PathfinderMob mob, int range, boolean restrict, Pos rel) {
        Pos p = RandomPos.generateRandomPosTowardDirection(mob, range, rand(mob), rel);
        return !GoalUtils.isOutsideLimits(p, mob) && !GoalUtils.isRestricted(restrict, mob, p) && !GoalUtils.isNotStable(mob.getNavigation(), p)
                && !GoalUtils.hasMalus(mob, p) ? p : null;
    }
}
