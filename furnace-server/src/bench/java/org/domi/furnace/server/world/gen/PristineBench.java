package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Test;

class PristineBench {
    @Test
    void bench() throws Exception {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(12345L, blocks, registries);
        source.structureEngine().setStructuresEnabled(false);
        for (int i = 0; i < 60; i++) { var g = source.generateWorldChunk(-300 + i, 300); source.applyCarvers(g, -300 + i, 300); }
        for (int round = 0; round < 3; round++) {
            long t = System.nanoTime();
            int n = 0;
            for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) { var g = source.generateWorldChunk(400 + round * 20 + x, z); source.applyCarvers(g, 400 + round * 20 + x, z); n++; }
            System.out.printf("BENCH pristine %.2f ms/chunk%n", (System.nanoTime() - t) / 1e6 / n);
        }
        // ImprovedNoise 한 번 비용
        var in = new org.domi.furnace.server.world.gen.noise.ImprovedNoise(new org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource(1L));
        double acc = 0;
        for (int w = 0; w < 3; w++) {
            long t = System.nanoTime();
            for (int i = 0; i < 5_000_000; i++) acc += in.noise(i * 0.137, i * 0.0071, i * 0.31, 0, 0);
            System.out.printf("BENCH ImprovedNoise %.1f ns/call (%f)%n", (System.nanoTime() - t) / 5e6, acc);
        }
    }
}
