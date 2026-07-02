package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public record SimpleRandomSelectorFeature(List<PlacedFeature> features) implements ConfiguredFeature {
    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        if (features.isEmpty()) return false;
        return features.get(random.nextInt(features.size())).placeAt(view, random, new BlockPos(x, y, z));
    }
}
