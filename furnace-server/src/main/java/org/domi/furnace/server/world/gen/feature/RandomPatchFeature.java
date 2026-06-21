// random_patch feature — 바닐라 시도 루프(try당 6 nextInt 오프셋)로 내부 placed feature 반복 배치
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class RandomPatchFeature implements ConfiguredFeature {

    private final int tries;
    private final int xzSpread;
    private final int ySpread;
    private final PlacedFeature feature;

    public RandomPatchFeature(int tries, int xzSpread, int ySpread, PlacedFeature feature) {
        this.tries = tries;
        this.xzSpread = xzSpread;
        this.ySpread = ySpread;
        this.feature = feature;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int i1 = xzSpread + 1;
        int i2 = ySpread + 1;
        int placed = 0;
        // 바닐라 RandomPatchFeature: setWithOffset(origin, x=nextInt(i1)-nextInt(i1), y=…(i2), z=…(i1))
        for (int t = 0; t < tries; t++) {
            int bx = x + random.nextInt(i1) - random.nextInt(i1);
            int by = y + random.nextInt(i2) - random.nextInt(i2);
            int bz = z + random.nextInt(i1) - random.nextInt(i1);
            if (feature.placeAt(view, random, new BlockPos(bx, by, bz))) {
                placed++;
            }
        }
        return placed > 0;
    }
}
