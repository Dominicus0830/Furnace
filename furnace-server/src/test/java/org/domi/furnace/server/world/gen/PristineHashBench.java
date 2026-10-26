package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

// 최적화 전후 동일성 확인용: pristine(지형+표면+카버) 블록·바이옴 해시와 청크당 시간. -Dfurnace.profile=true 일 때만 돈다.
class PristineHashBench {
    @Test
    void hashAndTime() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.profile"));
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(12345L, blocks, registries);
        for (int i = 0; i < 40; i++) { var g = source.generateWorldChunk(-300 + i, 300); source.applyCarvers(g, -300 + i, 300); }
        for (int round = 0; round < 2; round++) {
            long hash = 1469598103934665603L;
            long t = System.nanoTime();
            int n = 0;
            for (int x = -6; x < 6; x++) for (int z = -6; z < 6; z++) {
                int cx = x * 7 + round * 1000, cz = z * 5;
                var g = source.generateWorldChunk(cx, cz);
                source.applyCarvers(g, cx, cz);
                ChunkColumn col = g.col();
                for (int y = col.minY(); y <= col.maxY(); y++)
                    for (int bz = 0; bz < 16; bz++) for (int bx = 0; bx < 16; bx++)
                        hash = (hash ^ col.getBlock(bx, y, bz)) * 1099511628211L;
                for (int b : g.biomeIds()) hash = (hash ^ b) * 1099511628211L;
                n++;
            }
            System.out.printf("PRISTINE_HASH round=%d %016x chunks=%d %.2f ms/chunk%n", round, hash, n, (System.nanoTime() - t) / 1e6 / n);
        }
    }
}
