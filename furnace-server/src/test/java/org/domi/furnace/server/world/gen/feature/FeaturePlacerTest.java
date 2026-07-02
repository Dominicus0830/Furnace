package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class FeaturePlacerTest {

    @Test
    void decorate_deduplicatesSharedBiomeFeaturesBySorterIndex() {
        CountingFeature feature = new CountingFeature();
        PlacedFeature shared = new PlacedFeature(feature, List.of());
        BiomeFeatureData data = new BiomeFeatureData(Map.of(
            "minecraft:plains", steps(List.of(shared)),
            "minecraft:forest", steps(List.of(shared))
        ));
        FeaturePlacer placer = new FeaturePlacer(1234L, data, FeatureSorter.build(data), null);
        FeatureWorldView view = new FeatureWorldView(
            new ChunkColumn(6), 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);

        placer.decorate(view, Set.of("minecraft:plains", "minecraft:forest"), 0, 0);

        assertEquals(1, feature.calls.get());
    }

    private static List<List<PlacedFeature>> steps(List<PlacedFeature> step6) {
        java.util.ArrayList<List<PlacedFeature>> steps = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) steps.add(List.of());
        steps.set(6, step6);
        return steps;
    }

    private static final class CountingFeature implements ConfiguredFeature {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
            calls.incrementAndGet();
            return true;
        }
    }
}
