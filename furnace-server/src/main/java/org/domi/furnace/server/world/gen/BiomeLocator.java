// 바닐라 BiomeSource.findClosestBiome3d(/locate biome): 가로는 BlockPos.spiralAround 나선, 세로는 Mth.outFromOrigin 순서.
// 난수는 쓰지 않는다(3d 판). 고정 바이옴(FixedBiomeSource)은 맞으면 출발점 그대로.
package org.domi.furnace.server.world.gen;

import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

public final class BiomeLocator {
    private BiomeLocator() { }

    /** quart 좌표 → 바이옴 레지스트리 id(BiomeSource.getNoiseBiome). */
    @FunctionalInterface
    public interface NoiseBiome {
        int at(int qx, int qy, int qz);
    }

    /** 찾은 블록 좌표와 바이옴 id. */
    public record Found(int x, int y, int z, int biomeId) {}

    /** 나선 한 칸을 받는다. false 를 돌려주면 멈춘다. */
    @FunctionalInterface
    public interface SpiralVisitor {
        boolean visit(int dx, int dz);
    }

    /**
     * findClosestBiome3d(origin, radius, horizontalStep, verticalStep, predicate, sampler, level).
     * minBuildHeight·maxBuildHeight 는 LevelHeightAccessor 값(최소 y, 최소 y + 높이).
     */
    public static Found findClosestBiome3d(int ox, int oy, int oz, int radius, int horizontalStep, int verticalStep,
                                           Set<Integer> possibleBiomes, IntPredicate predicate, NoiseBiome sampler,
                                           int minBuildHeight, int maxBuildHeight) {
        java.util.Set<Integer> set = new java.util.HashSet<>();
        for (int b : possibleBiomes) if (predicate.test(b)) set.add(b);
        if (set.isEmpty()) return null;
        int steps = Math.floorDiv(radius, horizontalStep);
        int[] ys = outFromOrigin(oy, minBuildHeight + 1, maxBuildHeight, verticalStep).toArray();
        Found[] out = {null};
        spiralAround(steps, (dx, dz) -> {
            int x = ox + dx * horizontalStep, z = oz + dz * horizontalStep;
            int qx = x >> 2, qz = z >> 2;
            for (int y : ys) {
                int b = sampler.at(qx, y >> 2, qz);
                if (set.contains(b)) {
                    out[0] = new Found(x, y, z, b);
                    return false;
                }
            }
            return true;
        });
        return out[0];
    }

    /** FixedBiomeSource.findClosestBiome3d: 바이옴이 맞으면 출발점. */
    public static Found fixed(int ox, int oy, int oz, int biomeId, IntPredicate predicate) {
        return predicate.test(biomeId) ? new Found(ox, oy, oz, biomeId) : null;
    }

    /** Mth.outFromOrigin(origin, lower, upper, step) — 출발점부터 위아래로 번갈아. 범위 밖이면 빈 흐름. */
    public static IntStream outFromOrigin(int origin, int lower, int upper, int step) {
        if (lower > upper) throw new IllegalArgumentException(String.format(java.util.Locale.ROOT, "upperbound %d expected to be > lowerBound %d", upper, lower));
        if (step < 1) throw new IllegalArgumentException(String.format(java.util.Locale.ROOT, "steps expected to be >= 1, was %d", step));
        if (origin < lower || origin > upper) return IntStream.empty();
        return IntStream.iterate(origin, v -> {
            int d = Math.abs(origin - v);
            return origin - d >= lower || origin + d <= upper;
        }, v -> {
            boolean below = v <= origin;
            int d = Math.abs(origin - v);
            boolean upOk = origin + d + step <= upper;
            if (!below || !upOk) {
                int n = origin - d - (below ? step : 0);
                if (n >= lower) return n;
            }
            return origin + d + step;
        });
    }

    /**
     * BlockPos.spiralAround(ZERO, radius, EAST, SOUTH) 의 (x, z) 순서. 바닐라 반복자를 그대로 옮겼다:
     * 방향 [동, 남, 서, 북], 첫 칸은 출발점, 변 길이는 h/2+1, 4·radius 번째 변에서 끝난다.
     */
    public static void spiralAround(int radius, SpiralVisitor v) {
        final int[] dx = {1, 0, -1, 0}, dz = {0, 1, 0, -1};
        int x = 0, z = 1;   // origin.mutable().move(SOUTH)
        int g = 4 * radius, h = -1, i = 0, j = 0;
        while (true) {
            int d = (h + 4) % 4;
            x += dx[d];
            z += dz[d];
            if (j >= i) {
                if (h >= g) return;
                h++;
                j = 0;
                i = h / 2 + 1;
            }
            j++;
            if (!v.visit(x, z)) return;
        }
    }
}
