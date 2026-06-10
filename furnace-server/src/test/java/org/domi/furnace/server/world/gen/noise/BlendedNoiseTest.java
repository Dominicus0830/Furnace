// 바닐라 BlendedNoise 비트정확성을 오라클 fixture로 검증하는 테스트
package org.domi.furnace.server.world.gen.noise;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.*;

import org.junit.jupiter.api.Test;

class BlendedNoiseTest {
    @Test
    void matchesVanillaSamples() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/worldgen/fixtures/noise_blended_0.bin")) {
            assertNotNull(in, "fixture 없음 - 오라클 먼저 실행: ./gradlew :worldgen-oracle:run --args=\"0 blended:0\"");
            ByteBuffer bb = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            double xzScale = bb.getDouble();
            double yScale  = bb.getDouble();
            double xzFactor = bb.getDouble();
            double yFactor  = bb.getDouble();
            double smear    = bb.getDouble();
            int n = bb.getInt();

            BlendedNoise noise = new BlendedNoise(new XoroshiroRandomSource(0L),
                xzScale, yScale, xzFactor, yFactor, smear);

            for (int i = 0; i < n; i++) {
                int x = bb.getInt();
                int y = bb.getInt();
                int z = bb.getInt();
                double expected = bb.getDouble();
                assertEquals(expected, noise.calculate(x, y, z), 0.0,
                    "sample " + i + " @(" + x + "," + y + "," + z + ")");
            }
        }
    }
}
