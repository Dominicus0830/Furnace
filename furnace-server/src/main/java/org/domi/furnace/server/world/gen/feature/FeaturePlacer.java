package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import java.util.TreeSet;
import org.domi.furnace.server.world.gen.carver.RandomTrace;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.carver.XoroshiroWorldgenRandom;

public final class FeaturePlacer {

    private final long seed;
    private final BiomeFeatureData biomeData;
    private final FeatureSorter sorter;

    public FeaturePlacer(long seed, BiomeFeatureData biomeData, FeatureSorter sorter) {
        this.seed = seed;
        this.biomeData = biomeData;
        this.sorter = sorter;
    }

    // 진단용 플래그 — 디버깅 완료 후 제거
    private static final boolean DEBUG = Boolean.getBoolean("furnace.featureDebug");
    private static final boolean BLOCK_DEBUG = Boolean.getBoolean("furnace.featureBlockDebug");

    public void decorate(FeatureWorldView view, Set<String> biomeSet, int cx, int cz) {
        WorldgenRandom random = new XoroshiroWorldgenRandom();
        long decorationSeed = random.setDecorationSeed(seed, cx * 16, cz * 16);
        WorldgenRandom featureRandom = new XoroshiroWorldgenRandom();

        if (DEBUG) {
            System.out.printf("FURNACE decorate cx=%d cz=%d decorationSeed=%d biomes=%s%n",
                cx, cz, decorationSeed, new java.util.TreeSet<>(biomeSet));
        }

        for (int step = 0; step < BiomeFeatureData.STEP_COUNT; step++) {
            FeatureSorter.StepFeatureData stepData = sorter.featuresPerStep().get(step);
            TreeSet<Integer> indices = new TreeSet<>();
            for (String biome : biomeSet) {
                for (PlacedFeature feature : biomeData.featuresForStep(biome, step)) {
                    int idx = stepData.indexOf(feature);
                    if (idx < 0) {
                        throw new IllegalStateException("PlacedFeature not interned for biome " + biome + " step " + step);
                    }
                    indices.add(idx);
                }
            }
            if (DEBUG && !indices.isEmpty()) {
                System.out.printf("FURNACE step=%d indices=%s%n", step, indices);
            }
            for (int idx : indices) {
                featureRandom.setFeatureSeed(decorationSeed, idx, step);
                PlacedFeature pf = stepData.features().get(idx);
                String featureId = biomeData.idOf(pf);
                long featureSeed = decorationSeed + idx + 10000L * step;
                int[] before = BLOCK_DEBUG ? snapshotCenter(view) : null;
                if (DEBUG) {
                    System.out.printf("FURNACE step=%d idx=%d featureSeed=%d id=%s%n",
                        step, idx, featureSeed, featureId);
                }
                boolean trace = RandomTrace.shouldTrace(cx, cz, step, idx, featureId);
                if (trace) RandomTrace.begin(seed, cx, cz, step, idx, featureId, featureSeed);
                boolean placed;
                try {
                    placed = pf.placeWithBiomeCheck(view, featureRandom, cx * 16, cz * 16,
                            biomeData.validBiomeIds(pf));
                } finally {
                    if (trace) RandomTrace.end();
                }
                if (BLOCK_DEBUG) {
                    int[] after = snapshotCenter(view);
                    int changed = countChanged(before, after);
                    if (placed || changed > 0) {
                        System.out.printf(
                            "  FURNACE step=%d idx=%d id=%s placed=%s changed=%d firstChange=%s%n",
                            step, idx, featureId, placed, changed, firstChange(before, after, cx, cz));
                        logChangedBlocks(before, after, cx, cz, step, idx, featureId);
                    }
                }
            }
        }
    }

    private static int[] snapshotCenter(FeatureWorldView view) {
        int[] snapshot = new int[384 * 16 * 16];
        int index = 0;
        int minX = view.cx() * 16;
        int minZ = view.cz() * 16;
        for (int y = -64; y < 320; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    snapshot[index++] = view.getBlock(minX + lx, y, minZ + lz);
                }
            }
        }
        return snapshot;
    }

    private static int countChanged(int[] before, int[] after) {
        int changed = 0;
        for (int i = 0; i < before.length; i++) {
            if (before[i] != after[i]) changed++;
        }
        return changed;
    }

    private static String firstChange(int[] before, int[] after, int cx, int cz) {
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            int plane = 16 * 16;
            int y = -64 + i / plane;
            int rem = i % plane;
            int lz = rem / 16;
            int lx = rem % 16;
            return String.format("lx=%d y=%d lz=%d %d->%d", lx, y, lz, before[i], after[i]);
        }
        return "-";
    }

    private static void logChangedBlocks(int[] before, int[] after, int cx, int cz, int step, int idx, String id) {
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            int plane = 16 * 16;
            int y = -64 + i / plane;
            int rem = i % plane;
            int lz = rem / 16;
            int lx = rem % 16;
            int wx = cx * 16 + lx;
            int wz = cz * 16 + lz;
            System.out.printf(
                "BLOCK_DELTA source=FURNACE step=%d idx=%d id=%s wx=%d y=%d wz=%d old=%d new=%d%n",
                step, idx, id, wx, y, wz, before[i], after[i]);
        }
    }

    public BiomeFeatureData biomeData() {
        return biomeData;
    }

    public FeatureSorter sorter() {
        return sorter;
    }
}
