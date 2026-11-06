// 바닐라 RandomPos: 무작위 이동 목표 칸 뽑기 — 상대 위치를 뽑고(방향 편향 가능), 10번 시도해 선호도(getWalkTargetValue)가 가장 큰 것을 고른다.
package org.domi.furnace.server.entity.ai.util;

import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;

public final class RandomPos {
    private static final int RANDOM_POS_ATTEMPTS = 10;

    private RandomPos() { }

    public interface Rand {
        int nextInt(int bound);

        float nextFloat();

        double nextDouble();
    }

    public static Pos generateRandomDirection(Rand r, int horizontalRange, int verticalRange) {
        int x = r.nextInt(2 * horizontalRange + 1) - horizontalRange;
        int y = r.nextInt(2 * verticalRange + 1) - verticalRange;
        int z = r.nextInt(2 * horizontalRange + 1) - horizontalRange;
        return new Pos(x, y, z);
    }

    /** 방향(dirX, dirZ) 쪽으로 maxAngleDiff 안에서 뽑는다. 범위를 벗어나면 null. */
    public static Pos generateRandomDirectionWithinRadians(Rand r, int maxHorizontal, int maxVertical, int yOffset, double dirX, double dirZ, double maxAngleDiff) {
        double angleToDir = org.domi.furnace.server.entity.ai.control.Mth.atan2(dirZ, dirX) - (float) (Math.PI / 2);
        double angle = angleToDir + (2.0F * r.nextFloat() - 1.0F) * maxAngleDiff;
        double dist = Math.sqrt(r.nextDouble()) * (float) Math.sqrt(2.0) * maxHorizontal;
        double dx = -dist * Math.sin(angle), dz = dist * Math.cos(angle);
        if (!(Math.abs(dx) > maxHorizontal) && !(Math.abs(dz) > maxHorizontal)) {
            int dy = r.nextInt(2 * maxVertical + 1) - maxVertical + yOffset;
            return new Pos((int) Math.floor(dx), dy, (int) Math.floor(dz));
        }
        return null;
    }

    public static Pos moveUpOutOfSolid(Pos pos, int maxY, Predicate<Pos> solid) {
        if (!solid.test(pos)) return pos;
        Pos p = pos.above();
        while (p.y() < maxY && solid.test(p)) p = p.above();
        return p;
    }

    public static Pos moveUpToAboveSolid(Pos pos, int aboveSolidAmount, int maxY, Predicate<Pos> solid) {
        if (aboveSolidAmount < 0) throw new IllegalArgumentException("aboveSolidAmount was " + aboveSolidAmount + ", expected >= 0");
        if (!solid.test(pos)) return pos;
        Pos p = pos.above();
        while (p.y() < maxY && solid.test(p)) p = p.above();
        Pos q = p;
        while (q.y() < maxY && q.y() - p.y() < aboveSolidAmount) {
            Pos above = q.above();
            if (solid.test(above)) break;
            q = above;
        }
        return q;
    }

    /** generateRandomPos(mob, supplier): 10번 뽑아 getWalkTargetValue 가 가장 큰 칸의 아래쪽 가운데 점(Vec3.atBottomCenterOf)을 돌려준다. */
    public static double[] generateRandomPos(PathfinderMob mob, Supplier<Pos> supplier) { return generateRandomPos(supplier, mob::getWalkTargetValue); }

    public static double[] generateRandomPos(Supplier<Pos> supplier, ToDoubleFunction<Pos> weight) {
        double best = Double.NEGATIVE_INFINITY;
        Pos bestPos = null;
        for (int i = 0; i < RANDOM_POS_ATTEMPTS; i++) {
            Pos p = supplier.get();
            if (p != null) {
                double w = weight.applyAsDouble(p);
                if (w > best) {
                    best = w;
                    bestPos = p;
                }
            }
        }
        return bestPos != null ? new double[] {bestPos.x() + 0.5, bestPos.y(), bestPos.z() + 0.5} : null;   // Vec3.atBottomCenterOf
    }

    /** generateRandomPosTowardDirection의 최종 보정: 몹 위치를 더하고 제한 구역 쪽으로 치우친다. */
    public static Pos generateRandomPosTowardDirection(PathfinderMob mob, int range, org.domi.furnace.server.entity.ai.util.RandomPos.Rand r, Pos rel) {
        int x = rel.x(), z = rel.z();
        if (mob.hasRestriction() && range > 1) {
            Pos c = mob.getRestrictCenter();
            if (mob.x() > c.x()) x -= r.nextInt(range / 2);
            else x += r.nextInt(range / 2);
            if (mob.z() > c.z()) z -= r.nextInt(range / 2);
            else z += r.nextInt(range / 2);
        }
        return new Pos((int) Math.floor(x + mob.x()), (int) Math.floor(rel.y() + mob.y()), (int) Math.floor(z + mob.z()));
    }
}
