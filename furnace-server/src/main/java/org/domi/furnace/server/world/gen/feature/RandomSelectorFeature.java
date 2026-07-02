package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.RandomTrace;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public record RandomSelectorFeature(List<Entry> features, PlacedFeature defaultFeature) implements ConfiguredFeature {
    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        BlockPos origin = new BlockPos(x, y, z);
        for (Entry entry : features) {
            float roll = random.nextFloat();
            RandomTrace.log("random_selector roll=" + roll + " chance=" + entry.chance()
                + " picked=" + (roll < entry.chance()));
            if (roll < entry.chance()) {
                return entry.feature().placeAt(view, random, origin);
            }
        }
        RandomTrace.log("random_selector default");
        return defaultFeature.placeAt(view, random, origin);
    }

    public record Entry(float chance, PlacedFeature feature) {}
}
