// noise_threshold_provider의 random 소비순서·분기 parity 검증
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.noise.LegacyNormalNoise;
import org.junit.jupiter.api.Test;

class NoiseThresholdProviderTest {

    private static final int[] LOW = {10, 11, 12, 13};
    private static final int[] HIGH = {20, 21, 22, 23};

    // flower_plain 파라미터(seed=2345, amplitudes=[1.0], firstOctave=0, scale=0.005).
    private NoiseThresholdProvider provider(float threshold, float highChance) {
        return new NoiseThresholdProvider(2345L, 0, new double[]{1.0},
            0.005f, threshold, highChance, 100, LOW, HIGH);
    }

    /** noise < threshold(=2.0, 항상 참) → lowStates[nextInt(4)] 한 번만 소비. */
    @Test
    void lowBranch_consumesOneNextInt() {
        NoiseThresholdProvider p = provider(2.0f, 0.5f);
        WorldgenRandom actual = new WorldgenRandom();
        actual.setFeatureSeed(777L, 1, 4);
        int got = p.getState(actual, 8, 64, 8);

        WorldgenRandom replay = new WorldgenRandom();
        replay.setFeatureSeed(777L, 1, 4);
        assertEquals(LOW[replay.nextInt(4)], got);
        assertEquals(replay.nextInt(1_000_000), actual.nextInt(1_000_000));
    }

    /** noise >= threshold(=-2.0) + highChance=1.0 → nextFloat 먼저, 그다음 highStates[nextInt]. */
    @Test
    void highBranch_consumesNextFloatThenNextInt() {
        NoiseThresholdProvider p = provider(-2.0f, 1.0f);
        WorldgenRandom actual = new WorldgenRandom();
        actual.setFeatureSeed(777L, 1, 4);
        int got = p.getState(actual, 8, 64, 8);

        WorldgenRandom replay = new WorldgenRandom();
        replay.setFeatureSeed(777L, 1, 4);
        replay.nextFloat();
        assertEquals(HIGH[replay.nextInt(4)], got);
        assertEquals(replay.nextInt(1_000_000), actual.nextInt(1_000_000));
    }

    /** highChance=0.0 → nextFloat만 소비, defaultState 반환(nextInt 미소비). */
    @Test
    void highBranch_defaultState_consumesOnlyNextFloat() {
        NoiseThresholdProvider p = provider(-2.0f, 0.0f);
        WorldgenRandom actual = new WorldgenRandom();
        actual.setFeatureSeed(777L, 1, 4);
        assertEquals(100, p.getState(actual, 8, 64, 8));

        WorldgenRandom replay = new WorldgenRandom();
        replay.setFeatureSeed(777L, 1, 4);
        replay.nextFloat();
        assertEquals(replay.nextInt(1_000_000), actual.nextInt(1_000_000));
    }

    /** LegacyNormalNoise는 결정적·범위 내. */
    @Test
    void noiseValue_deterministicInRange() {
        LegacyNormalNoise n = new LegacyNormalNoise(2345L, 0, new double[]{1.0});
        double a = n.getValue(8 * 0.005, 64 * 0.005, 8 * 0.005);
        assertEquals(a, n.getValue(8 * 0.005, 64 * 0.005, 8 * 0.005), 0.0);
        assertTrue(a >= -1.5 && a <= 1.5);
    }
}
