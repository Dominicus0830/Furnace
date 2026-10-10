package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PristineBench {
    @Test
    void bench() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.profile"));
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(12345L, blocks, registries);
        source.structureEngine().setStructuresEnabled(false);
        for (int i = 0; i < 60; i++) { var g = source.generateWorldChunk(-300 + i, 300); source.applyCarvers(g.col(), -300 + i, 300); }
        for (int round = 0; round < 3; round++) {
            long t = System.nanoTime();
            int n = 0;
            for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) { var g = source.generateWorldChunk(400 + round * 20 + x, z); source.applyCarvers(g.col(), 400 + round * 20 + x, z); n++; }
            System.out.printf("BENCH pristine %.2f ms/chunk%n", (System.nanoTime() - t) / 1e6 / n);
        }
    }
}
