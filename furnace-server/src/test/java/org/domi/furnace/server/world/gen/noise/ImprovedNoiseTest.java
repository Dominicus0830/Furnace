// 바닐라 ImprovedNoise 비트정확성을 오라클 fixture로 검증하는 테스트
package org.domi.furnace.server.world.gen.noise;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.*;

import org.junit.jupiter.api.Test;

class ImprovedNoiseTest {
    @Test
    void matchesVanillaSamples() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/worldgen/fixtures/noise_improved_0.bin")) {
            assertNotNull(in, "fixture 없음 - 오라클 먼저 실행");
            ByteBuffer bb = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            int n = bb.getInt();
            ImprovedNoise noise = new ImprovedNoise(new XoroshiroRandomSource(0L));
            for (int i = 0; i < n; i++) {
                double x = bb.getDouble(), y = bb.getDouble(), z = bb.getDouble(), expected = bb.getDouble();
                assertEquals(expected, noise.noise(x, y, z), 0.0, "sample " + i + " @(" + x + "," + y + "," + z + ")");
            }
        }
    }
}
