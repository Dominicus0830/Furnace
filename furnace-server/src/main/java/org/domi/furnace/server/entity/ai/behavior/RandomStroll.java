// 바닐라 RandomStroll: WALK_TARGET 이 없으면 무작위 근처 점을 걷는 목표로 둔다(땅/수영/비행 변형).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.Function;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.util.DefaultRandomPos;
import org.domi.furnace.server.entity.ai.util.FlyingRandomPos;

public final class RandomStroll {
    private static final int MAX_XZ_DIST = 10;
    private static final int MAX_Y_DIST = 7;
    private static final int[][] SWIM_XY_DISTANCE_TIERS = {{1, 1}, {3, 3}, {5, 5}, {6, 5}, {7, 7}, {10, 7}};

    private RandomStroll() {}

    public static OneShot<PathfinderMob> stroll(float speed) { return stroll(speed, true); }

    public static OneShot<PathfinderMob> stroll(float speed, boolean mayStrollFromWater) {
        return strollFlat(speed, m -> DefaultRandomPos.getPos(m, 10, 7), mayStrollFromWater ? m -> true : m -> !m.isInWater());
    }

    public static OneShot<PathfinderMob> stroll(float speed, int xz, int y) { return strollFlat(speed, m -> DefaultRandomPos.getPos(m, xz, y), m -> true); }

    public static OneShot<PathfinderMob> fly(float speed) { return strollFlat(speed, m -> getTargetFlyPos(m, 10, 7), m -> true); }

    public static OneShot<PathfinderMob> swim(float speed) { return strollFlat(speed, RandomStroll::getTargetSwimPos, PathfinderMob::isInWater); }

    private static OneShot<PathfinderMob> strollFlat(float speed, Function<PathfinderMob, double[]> target, Predicate<PathfinderMob> canStroll) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, PathfinderMob mob, long gameTime) {
                if (!absent(mob, MemoryModuleType.WALK_TARGET)) return false;
                if (!canStroll.test(mob)) return false;
                double[] v = target.apply(mob);
                mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, v == null ? java.util.Optional.empty() : java.util.Optional.of(new WalkTarget(v, speed, 0)));
                return true;
            }
        };
    }

    private static double[] getTargetSwimPos(PathfinderMob mob) {
        double[] prev = null, next = null;
        for (int[] tier : SWIM_XY_DISTANCE_TIERS) {
            if (prev == null) {
                next = getRandomSwimmablePos(mob, tier[0], tier[1]);
            } else {
                double dx = prev[0] - mob.x(), dy = prev[1] - mob.y(), dz = prev[2] - mob.z();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double ux = len < 1.0E-5F ? 0.0 : dx / len, uy = len < 1.0E-5F ? 0.0 : dy / len, uz = len < 1.0E-5F ? 0.0 : dz / len;
                next = new double[] {mob.x() + ux * tier[0], mob.y() + uy * tier[1], mob.z() + uz * tier[0]};
            }
            if (next == null || mob.level().getFluidState(new org.domi.furnace.server.block.Pos((int) Math.floor(next[0]), (int) Math.floor(next[1]), (int) Math.floor(next[2]))) == 0) return prev;
            prev = next;
        }
        return next;
    }

    /** BehaviorUtils.getRandomSwimmablePos(mob, h, v) */
    static double[] getRandomSwimmablePos(PathfinderMob mob, int h, int v) {
        double[] view = org.domi.furnace.server.block.Clip.viewVector(mob.xRot(), mob.yRot());
        return FlyingRandomPos.getHoverPos(mob, h, v, -2, view[0], view[2], (float) (Math.PI / 2));
    }

    private static double[] getTargetFlyPos(PathfinderMob mob, int xz, int y) {
        double[] view = org.domi.furnace.server.block.Clip.viewVector(mob.xRot(), mob.yRot());
        return FlyingRandomPos.getAirPosTowards(mob, xz, y, view[0], view[2], (float) (Math.PI / 2), 0, 0);
    }
}
