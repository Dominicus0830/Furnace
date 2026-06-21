// 바닐라 SimplexNoise(=Biome.BIOME_INFO_NOISE) 비트정확성을 오라클 fixture로 검증
package org.domi.furnace.server.world.gen.noise;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.*;

import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.junit.jupiter.api.Test;

class SimplexNoiseTest {

    @Test
    void biomeInfoNoise_matchesVanilla() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/worldgen/fixtures/biome_info_noise.bin")) {
            assertNotNull(in, "fixture 없음 - 오라클 'biomeinfonoise' 먼저 실행");
            ByteBuffer bb = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            int n = bb.getInt();
            // Biome.BIOME_INFO_NOISE = PerlinSimplexNoise(LegacyRandomSource(2345), octaves=[0])
            //   = 단일 SimplexNoise(2345)의 2D getValue (highestFreq factor 둘 다 1.0)
            SimplexNoise noise = new SimplexNoise(new LegacyRandom(2345L));
            for (int i = 0; i < n; i++) {
                int bx = bb.getInt();
                int bz = bb.getInt();
                double expected = bb.getDouble();
                assertEquals(expected, noise.getValue(bx / 200.0, bz / 200.0), 0.0,
                    "sample " + i + " @(" + bx + "," + bz + ")");
            }
        }
    }
}
