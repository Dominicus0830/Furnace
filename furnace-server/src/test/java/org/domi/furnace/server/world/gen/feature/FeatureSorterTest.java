package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class FeatureSorterTest {

    @Test
    void featureSorter_assignsStablePerStepIdentityIndex() {
        PlacedFeature shared = new PlacedFeature(new NoopFeature(), List.of());
        PlacedFeature other = new PlacedFeature(new NoopFeature(), List.of());
        BiomeFeatureData data = new BiomeFeatureData(Map.of(
            "minecraft:plains", steps(List.of(shared, other)),
            "minecraft:forest", steps(List.of(shared))
        ));

        FeatureSorter sorter = FeatureSorter.build(data);
        FeatureSorter.StepFeatureData step6 = sorter.featuresPerStep().get(6);

        assertEquals(0, step6.indexOf(shared));
        assertEquals(1, step6.indexOf(other));
        assertTrue(step6.indexOf(new PlacedFeature(new NoopFeature(), List.of())) < 0);
    }

    private static List<List<PlacedFeature>> steps(List<PlacedFeature> step6) {
        java.util.ArrayList<List<PlacedFeature>> steps = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) steps.add(List.of());
        steps.set(6, step6);
        return steps;
    }

    private static final class NoopFeature implements ConfiguredFeature {
        @Override
        public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
            return false;
        }
    }
}
