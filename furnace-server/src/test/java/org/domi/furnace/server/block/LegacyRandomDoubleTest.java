// 바닐라 BitRandomSource.nextDouble = (double)((next(26) << 27) + next(27)) * 1.1102230246251565E-16. float 상수로 곱하면 정밀도를 잃는다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LegacyRandomDoubleTest {

    @Test
    void nextDoubleIsFullDoublePrecisionLikeVanilla() {
        // java.util.Random 도 같은 식(next(26), next(27), 2^-53 곱)이다 — 같은 씨앗이면 같은 수열
        for (long seed : new long[] {0L, 1L, 42L, -7L, 123456789012345L}) {
            var mine = new LegacyRandomSource(seed);
            var jdk = new java.util.Random(seed);
            for (int i = 0; i < 20_000; i++) assertEquals(jdk.nextDouble(), mine.nextDouble(), 0.0, "seed " + seed + " #" + i);
        }
    }

    @Test
    void nextDoubleStaysBelowOne() {
        var r = new LegacyRandomSource(99L);
        for (int i = 0; i < 200_000; i++) {
            double d = r.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0, "d=" + d);
        }
    }
}
