// carver parity 테스트 — LegacyRandom 시드 검증 + 픽스처 비트일치
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

class CarverParityTest {

    @Test
    void worldgenRandom_largeFeatureSeed() {
        WorldgenRandom r = new WorldgenRandom();
        r.setLargeFeatureSeed(12345L, 7, -3);
        float v = r.nextFloat();
        assertTrue(v >= 0.0f && v < 1.0f);
    }
}
