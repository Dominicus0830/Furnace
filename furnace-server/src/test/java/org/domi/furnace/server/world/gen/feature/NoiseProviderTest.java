// noise_provider index 매핑 산술·random 0소비·결정성 (LegacyNormalNoise 자체는 slice3서 byte-exact 검증됨)
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class NoiseProviderTest {

    private static final int[] STATES = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

    @Test
    void consumesNoRandom() {
        NoiseProvider p = new NoiseProvider(2345L, 0, new double[]{1.0}, 0.020833334f, STATES);
        WorldgenRandom actual = new WorldgenRandom(); actual.setFeatureSeed(777L, 1, 4);
        p.getState(actual, 8, 64, 8);
        WorldgenRandom replay = new WorldgenRandom(); replay.setFeatureSeed(777L, 1, 4);
        assertEquals(replay.nextInt(1_000_000), actual.nextInt(1_000_000));
    }

    /** d=clamp((1+v)/2,0,0.9999); idx=(int)(d*size). s size 4. */
    @Test
    void indexStateArithmetic() {
        int[] s = {10, 20, 30, 40};
        assertEquals(10, NoiseProvider.indexState(s, -2.0)); // clamp→0 → idx0
        assertEquals(10, NoiseProvider.indexState(s, -1.0)); // (1-1)/2=0 → idx0
        assertEquals(30, NoiseProvider.indexState(s,  0.0)); // 0.5*4=2.0 → idx2
        assertEquals(40, NoiseProvider.indexState(s,  2.0)); // clamp 0.9999*4=3.9996 → idx3
        assertEquals(40, NoiseProvider.indexState(s,  5.0)); // 상한 → 마지막 index
    }

    @Test
    void deterministic() {
        NoiseProvider p = new NoiseProvider(2345L, 0, new double[]{1.0}, 0.020833334f, STATES);
        WorldgenRandom r = new WorldgenRandom();
        assertEquals(p.getState(r, 12, 70, -34), p.getState(r, 12, 70, -34));
    }
}
