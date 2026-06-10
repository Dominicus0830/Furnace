// Xoroshiro RNG가 바닐라 nextLong 시퀀스와 비트일치 검증
package org.domi.furnace.server.world.gen.noise;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.*;
import org.junit.jupiter.api.Test;

class XoroshiroRandomSourceTest {
    private static long[] readRngFixture(long seed) throws IOException {
        try (InputStream in = XoroshiroRandomSourceTest.class.getResourceAsStream("/worldgen/fixtures/rng_" + seed + ".bin")) {
            assertNotNull(in, "fixture 없음");
            byte[] b = in.readAllBytes();
            ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
            int n = bb.getInt();
            long[] out = new long[n];
            for (int i = 0; i < n; i++) out[i] = bb.getLong();
            return out;
        }
    }
    @Test void matchesVanillaSequence() throws Exception {
        long[] expected = readRngFixture(0L);
        XoroshiroRandomSource r = new XoroshiroRandomSource(0L);
        for (int i = 0; i < expected.length; i++) assertEquals(expected[i], r.nextLong(), "idx " + i);
    }
}
