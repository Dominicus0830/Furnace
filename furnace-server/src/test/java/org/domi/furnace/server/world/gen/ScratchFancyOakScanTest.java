package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Test;

class ScratchFancyOakScanTest {
    @Test void scan() throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        long seed = 51532L;
        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg, registries);
        for (int cx = 0; cx <= 4; cx++) {
            for (int cz = 0; cz <= 4; cz++) {
                ChunkColumn col = source.generateWithFeatures(cx, cz);
                boolean found = false;
                for (int y = -64; y < 100 && !found; y++) {
                    for (int lz = 0; lz < 16 && !found; lz++) {
                        for (int lx = 0; lx < 16 && !found; lx++) {
                            int id = col.getBlock(lx, y, lz);
                            if (id == 130 || id == 132) { // oak_log axis=x / axis=z
                                System.out.printf("FOUND cx=%d cz=%d lx=%d y=%d lz=%d id=%d%n", cx, cz, lx, y, lz, id);
                                found = true;
                            }
                        }
                    }
                }
            }
        }
    }
}
