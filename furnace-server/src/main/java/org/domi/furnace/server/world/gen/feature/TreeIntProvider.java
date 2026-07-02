package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public sealed interface TreeIntProvider permits TreeIntProvider.Constant, TreeIntProvider.Uniform {

    int sample(WorldgenRandom random);

    record Constant(int value) implements TreeIntProvider {
        @Override
        public int sample(WorldgenRandom random) {
            return value;
        }
    }

    record Uniform(int minInclusive, int maxInclusive) implements TreeIntProvider {
        @Override
        public int sample(WorldgenRandom random) {
            return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
        }
    }
}
