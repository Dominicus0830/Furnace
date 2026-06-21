// dual_noise_provider: variety clampedMap 매핑·random 0소비·결정성 (LegacyNormalNoise는 slice3 검증)
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.gen.MathHelper;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class DualNoiseProviderTest {

    private static final int[] STATES = {0, 1, 2, 3, 4, 5, 6, 7};

    private DualNoiseProvider meadow() {
        return new DualNoiseProvider(1, 3,
            2345L, -10, new double[]{1.0}, 1.0f,
            2345L, -3, new double[]{1.0}, 1.0f, STATES);
    }

    /** i = (int) clampedMap(slowVal, -1,1, varietyMin, varietyMax+1). */
    @Test
    void varietyClampedMap() {
        assertEquals(1, (int) MathHelper.clampedMap(-1.0, -1.0, 1.0, 1.0, 4.0));
        assertEquals(1, (int) MathHelper.clampedMap(-5.0, -1.0, 1.0, 1.0, 4.0)); // 하한 클램프
        assertEquals(4, (int) MathHelper.clampedMap( 1.0, -1.0, 1.0, 1.0, 4.0)); // max+1
        assertEquals(4, (int) MathHelper.clampedMap( 9.0, -1.0, 1.0, 1.0, 4.0)); // 상한 클램프
        assertEquals(2, (int) MathHelper.clampedMap( 0.0, -1.0, 1.0, 1.0, 4.0)); // 2.5→2
    }

    @Test
    void consumesNoRandom() {
        DualNoiseProvider p = meadow();
        WorldgenRandom actual = new WorldgenRandom(); actual.setFeatureSeed(777L, 1, 4);
        p.getState(actual, 8, 64, 8);
        WorldgenRandom replay = new WorldgenRandom(); replay.setFeatureSeed(777L, 1, 4);
        assertEquals(replay.nextInt(1_000_000), actual.nextInt(1_000_000));
    }

    @Test
    void deterministicInRange() {
        DualNoiseProvider p = meadow();
        WorldgenRandom r = new WorldgenRandom();
        int a = p.getState(r, 100, 64, -200);
        assertEquals(a, p.getState(r, 100, 64, -200));
        assertTrue(a >= 0 && a < STATES.length);
    }
}
