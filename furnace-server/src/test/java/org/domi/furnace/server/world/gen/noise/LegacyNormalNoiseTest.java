// LegacyRandomSource 경로 NormalNoise 비트정확성을 오라클 fixture로 검증(state provider noise)
package org.domi.furnace.server.world.gen.noise;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.*;

import org.junit.jupiter.api.Test;

class LegacyNormalNoiseTest {

    @Test
    void matchesVanillaLegacyPath() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/worldgen/fixtures/legacy_normal_2345.bin")) {
            assertNotNull(in, "fixture 없음 - 오라클 'legacynormal:2345' 먼저 실행");
            ByteBuffer bb = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            int firstOctave = bb.getInt();
            int ampCount = bb.getInt();
            double[] amps = new double[ampCount];
            for (int i = 0; i < ampCount; i++) amps[i] = bb.getDouble();
            long seed = bb.getLong();
            int n = bb.getInt();

            LegacyNormalNoise noise = new LegacyNormalNoise(seed, firstOctave, amps);
            for (int i = 0; i < n; i++) {
                double x = bb.getDouble(), y = bb.getDouble(), z = bb.getDouble(), expected = bb.getDouble();
                assertEquals(expected, noise.getValue(x, y, z), 0.0,
                    "sample " + i + " @(" + x + "," + y + "," + z + ")");
            }
        }
    }
}
