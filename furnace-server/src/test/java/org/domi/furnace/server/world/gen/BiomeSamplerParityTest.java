// BiomeSampler multi-noise 바이옴 배치 바닐라 fixture 일치 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.net.registry.RegistrySet;

class BiomeSamplerParityTest {

    private void assertBiomeParity(long seed, int cx, int cz) throws Exception {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter router = NoiseRouter.build(data, settings, seed);
        BiomeSampler sampler = BiomeSampler.load(data, registries);

        NoiseChunk noiseChunk = new NoiseChunk(cx, cz, router, settings);
        int[] got = sampler.sampleChunk(cx, cz, noiseChunk);

        // Load fixture
        String fixtureName = "/worldgen/fixtures/biome_" + seed + "_" + cx + "_" + cz + ".bin";
        byte[] fx;
        try (InputStream in = getClass().getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture not found: " + fixtureName);
            fx = in.readAllBytes();
        }

        // Parse header
        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x42494F4D, header.getInt(), "magic mismatch");

        // Parse body: 1536 × (int16 nameLen + UTF-8 name)
        int mismatch = 0;
        int skipped = 0;
        String firstMismatch = null;
        int pos = 20;
        for (int i = 0; i < 1536; i++) {
            int len = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            pos += 2;
            String biomeName = new String(fx, pos, len, StandardCharsets.UTF_8);
            pos += len;

            // Skip biomes not in our 1.21.1 registry (e.g. pale_garden from oracle's 1.21.4 jar)
            int expectedId = registries.indexOf("minecraft:worldgen/biome", biomeName);
            if (expectedId < 0) {
                skipped++;
                continue;
            }

            int qy = i / 16;
            int rem = i % 16;
            int qz = rem / 4;
            int qx = rem % 4;

            if (expectedId != got[i]) {
                mismatch++;
                if (firstMismatch == null) {
                    firstMismatch = "quart(" + qx + "," + qy + "," + qz + ") want="
                        + biomeName + "(id=" + expectedId + ") got=" + got[i];
                }
            }
        }

        System.out.printf("seed=%d cx=%d cz=%d: %d mismatches, %d skipped (unknown biomes)%n",
            seed, cx, cz, mismatch, skipped);

        assertEquals(0, mismatch,
            "seed=" + seed + " cx=" + cx + " cz=" + cz
            + " mismatches=" + mismatch + " first=" + firstMismatch);
    }

    @Test void seed0_spawn() throws Exception { assertBiomeParity(0L, 0, 0); }
    @Test void seed0_far()   throws Exception { assertBiomeParity(0L, 5, 5); }
    @Test void seedB()       throws Exception { assertBiomeParity(123456789L, 0, 0); }

    @Test void seed1234_coord1() throws Exception { assertBiomeParity(1234L, 0,  0); }
    @Test void seed1234_coord2() throws Exception { assertBiomeParity(1234L, -5, 3); }
    @Test void seed1234_coord3() throws Exception { assertBiomeParity(1234L, 7, -2); }
}
