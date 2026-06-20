package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public interface ConfiguredFeature {
    boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z);
}
