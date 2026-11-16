package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.level.levelgen.feature.ConfiguredFeature")
public interface ConfiguredFeature {
    boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z);
}
