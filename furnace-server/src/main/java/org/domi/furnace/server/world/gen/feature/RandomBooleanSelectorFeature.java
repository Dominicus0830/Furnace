package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public record RandomBooleanSelectorFeature(PlacedFeature trueFeature, PlacedFeature falseFeature) implements ConfiguredFeature {
    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        return (random.nextBoolean() ? trueFeature : falseFeature).placeAt(view, random, new BlockPos(x, y, z));
    }
}
