// 바닐라 BiomeManager.getBiome 의 좌표 고르기 — 블록 좌표 → 인접 quart 8개 중 시드 지터 거리가 가장 가까운 quart.
// (SurfaceSystem·FeatureWorldView 와 같은 알고리즘. 게임플레이의 바이옴 질의 — 위치 조건 등 — 는 이 값을 쓴다.)
package org.domi.furnace.server.world;

public final class BiomeZoom {
    private BiomeZoom() {}

    /** @param zoomSeed BiomeManager.obfuscateSeed(월드 시드) @return {quartX, quartY, quartZ} */
    public static int[] quart(long zoomSeed, int blockX, int blockY, int blockZ) {
        int x = blockX - 2, y = blockY - 2, z = blockZ - 2;
        int qx = x >> 2, qy = y >> 2, qz = z >> 2;
        double fx = (x & 3) / 4.0, fy = (y & 3) / 4.0, fz = (z & 3) / 4.0;
        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            boolean x0 = (i & 4) == 0, y0 = (i & 2) == 0, z0 = (i & 1) == 0;
            double d = fiddledDistance(zoomSeed, x0 ? qx : qx + 1, y0 ? qy : qy + 1, z0 ? qz : qz + 1,
                    x0 ? fx : fx - 1.0, y0 ? fy : fy - 1.0, z0 ? fz : fz - 1.0);
            if (bestDistance > d) {
                best = i;
                bestDistance = d;
            }
        }
        return new int[]{(best & 4) == 0 ? qx : qx + 1, (best & 2) == 0 ? qy : qy + 1, (best & 1) == 0 ? qz : qz + 1};
    }

    private static double fiddledDistance(long seed, int x, int y, int z, double dx, double dy, double dz) {
        long m = next(seed, x);
        m = next(m, y);
        m = next(m, z);
        m = next(m, x);
        m = next(m, y);
        m = next(m, z);
        double xf = fiddle(m);
        m = next(m, seed);
        double yf = fiddle(m);
        m = next(m, seed);
        double zf = fiddle(m);
        return sq(dz + zf) + sq(dy + yf) + sq(dx + xf);
    }

    private static long next(long seed, long salt) {
        seed *= seed * 6364136223846793005L + 1442695040888963407L;
        return seed + salt;
    }

    private static double fiddle(long seed) { return ((double) Math.floorMod(seed >> 24, 1024) / 1024.0 - 0.5) * 0.9; }

    private static double sq(double v) { return v * v; }
}
