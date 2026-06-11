// NoiseChunk 셀 보간이 바닐라 fixture 의 stone mask 와 비트정확하게 일치하는지 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.Test;

class NoiseChunkTest {

    @Test
    void cellInterpolationMatchesFixtureStoneMask() throws Exception {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings s = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter r = NoiseRouter.build(data, s, 0L);

        byte[] fx;
        try (InputStream in = getClass().getResourceAsStream("/worldgen/fixtures/chunk_0_0_0.bin")) {
            assertNotNull(in, "fixture file not found");
            fx = in.readAllBytes();
        }

        // header = 4(magic) + 8(seed) + 4(cx) + 4(cz) = 20 bytes, then category bytes
        int headerLen = 20;

        NoiseChunk nc = new NoiseChunk(0, 0, r, s);
        boolean[] stone = nc.stoneMask(); // length 384*256, index (y+64)*256+lz*16+lx

        int mismatches = 0;
        int firstX = 0, firstY = 0, firstZ = 0;
        boolean found = false;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int y = -64; y < 320; y++) {
                    int idx = (y + 64) * 256 + lz * 16 + lx;
                    boolean vanillaStone = fx[headerLen + idx] == 1; // STONE = 1
                    boolean ourStone = stone[idx];
                    if (vanillaStone != ourStone) {
                        mismatches++;
                        if (!found) {
                            firstX = lx;
                            firstY = y;
                            firstZ = lz;
                            found = true;
                        }
                    }
                }
            }
        }

        assertEquals(0, mismatches,
                "first mismatch @local(" + firstX + "," + firstY + "," + firstZ + "), total " + mismatches);
    }

    @Test
    void preliminarySurfaceLevelInRange() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter router = NoiseRouter.build(data, settings, 0L);
        NoiseChunk nc = new NoiseChunk(0, 0, router, settings);

        int level = nc.getPreliminarySurfaceLevel(0, 0);
        // overworld seed=0 origin: surface should be roughly sea level range
        assertTrue(level >= -64 && level <= 320,
                "preliminary surface level out of range: " + level);
    }
}
