package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import java.util.TreeSet;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class FeaturePlacer {

    private final long seed;
    private final BiomeFeatureData biomeData;
    private final FeatureSorter sorter;

    public FeaturePlacer(long seed, BiomeFeatureData biomeData, FeatureSorter sorter) {
        this.seed = seed;
        this.biomeData = biomeData;
        this.sorter = sorter;
    }

    public void decorate(FeatureWorldView view, Set<String> biomeSet, int cx, int cz) {
        WorldgenRandom random = new WorldgenRandom();
        long decorationSeed = random.setDecorationSeed(seed, cx * 16, cz * 16);
        WorldgenRandom featureRandom = new WorldgenRandom();

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
            for (int idx : indices) {
                featureRandom.setFeatureSeed(decorationSeed, idx, step);
                stepData.features().get(idx).place(view, featureRandom, cx * 16, cz * 16);
            }
        }
    }
}
