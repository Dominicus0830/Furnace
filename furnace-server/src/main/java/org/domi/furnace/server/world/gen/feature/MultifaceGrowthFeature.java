// 바닐라 MultifaceGrowthFeature 이식 (multiface_growth: glow_lichen, sculk_vein).
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class MultifaceGrowthFeature implements ConfiguredFeature {

    private static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

    private final MultifaceSpreader spreader;
    private final int searchRange;
    private final float chanceOfSpreading;
    private final boolean[] canBePlacedOn;
    private final int[] validDirections;

    public MultifaceGrowthFeature(MultifaceSpreader spreader, int searchRange, boolean canPlaceOnFloor, boolean canPlaceOnCeiling,
                                  boolean canPlaceOnWall, float chanceOfSpreading, boolean[] canBePlacedOn) {
        this.spreader = spreader;
        this.searchRange = searchRange;
        this.chanceOfSpreading = chanceOfSpreading;
        this.canBePlacedOn = canBePlacedOn;
        int[] dirs = new int[6];
        int n = 0;
        if (canPlaceOnCeiling) dirs[n++] = UP;
        if (canPlaceOnFloor) dirs[n++] = DOWN;
        if (canPlaceOnWall) { dirs[n++] = NORTH; dirs[n++] = EAST; dirs[n++] = SOUTH; dirs[n++] = WEST; }   // Plane.HORIZONTAL
        this.validDirections = java.util.Arrays.copyOf(dirs, n);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        MultifaceSpreader.Env env = spreader.env;
        if (!env.isAirOrWaterBlock(view.getBlock(x, y, z))) return false;
        int[] dirs = shuffled(random, -1);
        if (placeGrowthIfPossible(view, x, y, z, view.getBlock(x, y, z), random, dirs)) return true;
        for (int dir : dirs) {
            int[] except = shuffled(random, dir ^ 1);
            // 바닐라: setWithOffset(origin, dir) 이 매 반복 origin 기준 — origin+dir 한 칸을 searchRange 번 재시도한다.
            int px = x + MultifaceSpreader.DX[dir], py = y + MultifaceSpreader.DY[dir], pz = z + MultifaceSpreader.DZ[dir];
            for (int i = 0; i < searchRange; i++) {
                int s = view.getBlock(px, py, pz);
                if (!env.isAirOrWaterBlock(s) && !spreader.is(s)) break;
                if (placeGrowthIfPossible(view, px, py, pz, s, random, except)) return true;
            }
        }
        return false;
    }

    private boolean placeGrowthIfPossible(FeatureWorldView view, int x, int y, int z, int state, WorldgenRandom random, int[] dirs) {
        for (int dir : dirs) {
            int n = view.getBlock(x + MultifaceSpreader.DX[dir], y + MultifaceSpreader.DY[dir], z + MultifaceSpreader.DZ[dir]);
            if (n < 0 || n >= canBePlacedOn.length || !canBePlacedOn[n]) continue;
            int ns = spreader.getStateForPlacement(view, state, x, y, z, dir);
            if (ns == -1) return false;
            view.trySetBlock(x, y, z, ns);
            view.markPosForPostprocessing(x, y, z);
            if (random.nextFloat() < chanceOfSpreading) spreader.spreadFromFaceTowardRandomDirection(view, ns, x, y, z, dir, random, true);
            return true;
        }
        return false;
    }

    /** MultifaceGrowthConfiguration.getShuffledDirections(Except) — Util.toShuffledList. */
    private int[] shuffled(WorldgenRandom random, int except) {
        int[] d = new int[validDirections.length];
        int n = 0;
        for (int v : validDirections) if (v != except) d[n++] = v;
        MultifaceSpreader.shuffle(d, n, random);
        return n == d.length ? d : java.util.Arrays.copyOf(d, n);
    }
}
