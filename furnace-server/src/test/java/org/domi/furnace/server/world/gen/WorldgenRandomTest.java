package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class WorldgenRandomTest {

    @Test
    void decorationSeed_setsExpectedSeedAndReturnsIt() {
        WorldgenRandom r = new WorldgenRandom();

        long decorSeed = r.setDecorationSeed(123456789L, 112, -48);
        WorldgenRandom expected = new WorldgenRandom();
        expected.setSeed(123456789L);
        long a = expected.nextLong() | 1L;
        long b = expected.nextLong() | 1L;
        long expectedSeed = ((long) 112 * a + (long) -48 * b) ^ 123456789L;

        assertEquals(expectedSeed, decorSeed);
        expected.setSeed(expectedSeed);
        assertEquals(expected.nextLong(), r.nextLong(), "returned seed must also be installed");
    }

    @Test
    void featureSeed_usesStepScopedFeatureIndex() {
        WorldgenRandom r = new WorldgenRandom();
        WorldgenRandom expected = new WorldgenRandom();

        r.setFeatureSeed(12345L, 3, 6);
        expected.setSeed(12345L + 3L + 10000L * 6L);

        assertEquals(expected.nextLong(), r.nextLong());
    }
}
