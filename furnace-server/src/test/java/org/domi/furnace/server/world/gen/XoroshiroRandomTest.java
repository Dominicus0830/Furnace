package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.domi.furnace.server.world.gen.carver.XoroshiroWorldgenRandom;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

class XoroshiroRandomTest {

    @Test
    void wgrMatchesVanillaWorldgenOracle() {
        XoroshiroWorldgenRandom wgr = new XoroshiroWorldgenRandom();
        wgr.setSeed(1234L);
        int[] expectedInts = {
            335790893,
            -1446754737,
            -1516591890,
            1314801618,
            -1753789134
        };
        int[] actualInts = new int[expectedInts.length];
        for (int i = 0; i < actualInts.length; i++) {
            actualInts[i] = wgr.nextInt();
        }
        assertArrayEquals(expectedInts, actualInts);

        wgr.setSeed(1234L);
        long[] expectedLongs = {
            1442210902282880591L,
            -6513712567614027822L,
            -7532466973161383646L,
            2550830343361749420L,
            507979810835868188L
        };
        long[] actualLongs = new long[expectedLongs.length];
        for (int i = 0; i < actualLongs.length; i++) {
            actualLongs[i] = wgr.nextLong();
        }
        assertArrayEquals(expectedLongs, actualLongs);

        wgr.setSeed(1234L);
        double[] expectedDoubles = {
            0.07818240915464103D,
            0.6468909279424861D,
            0.5916641404825437D,
            0.13828078950550238D,
            0.027537633777948045D
        };
        for (int i = 0; i < expectedDoubles.length; i++) {
            assertEquals(expectedDoubles[i], wgr.nextDouble(), 0D, "nextDouble i=" + i);
        }

        wgr.setSeed(1234L);
        float[] expectedFloats = {
            0.0781824F,
            0.66315114F,
            0.6468909F,
            0.30612606F,
            0.59166414F
        };
        for (int i = 0; i < expectedFloats.length; i++) {
            assertEquals(expectedFloats[i], wgr.nextFloat(), 0F, "nextFloat i=" + i);
        }

        wgr.setSeed(1234L);
        boolean[] expectedBooleans = {false, true, true, false, true};
        for (int i = 0; i < expectedBooleans.length; i++) {
            assertEquals(expectedBooleans[i], wgr.nextBoolean(), "nextBoolean i=" + i);
        }
    }

    @Test
    void matchesVanillaOracleValues() {
        XoroshiroRandomSource raw = new XoroshiroRandomSource(1234L);

        long[] expectedLongs = {
            1442210903926503196L,
            -6213764276566792285L,
            -6513712564911937959L,
            5647029953998151295L,
            -7532466972812487698L
        };
        long[] actualLongs = new long[expectedLongs.length];
        for (int i = 0; i < actualLongs.length; i++) {
            actualLongs[i] = raw.nextLong();
        }
        assertArrayEquals(expectedLongs, actualLongs);

        raw.setSeed(1234L);
        int[] expectedBoundedInts = {0, 15, 14, 14, 6};
        int[] actualBoundedInts = new int[expectedBoundedInts.length];
        for (int i = 0; i < actualBoundedInts.length; i++) {
            actualBoundedInts[i] = raw.nextInt(16);
        }
        assertArrayEquals(expectedBoundedInts, actualBoundedInts);

        XoroshiroWorldgenRandom random = new XoroshiroWorldgenRandom();
        long decorationSeed = random.setDecorationSeed(1234L, 0, 0);
        assertEquals(1234L, decorationSeed);

        random.setFeatureSeed(decorationSeed, 0, 0);
        int[] expectedFeatureInts = {1, 10, 10, 4, 9};
        int[] actualFeatureInts = new int[expectedFeatureInts.length];
        for (int i = 0; i < actualFeatureInts.length; i++) {
            actualFeatureInts[i] = random.nextInt(16);
        }
        assertArrayEquals(expectedFeatureInts, actualFeatureInts);
    }

    @Test
    void featureSeed7m2MatchesOracle() {
        // featureSeed for cx=7,cz=-2 step=6 idx=10 (ore_coal_lower)
        // oracle: nextFloat=0.45194376, nextInt(3)=0, nextInt(3)=2, nextInt(16)=10, nextInt(16)=4, nextInt(97)=17, nextInt(97)=21
        long featureSeed = 1031541745140545356L;
        XoroshiroWorldgenRandom r = new XoroshiroWorldgenRandom();
        r.setSeed(featureSeed);
        assertEquals(0.45194376f, r.nextFloat(), 1e-7f);
        assertEquals(0, r.nextInt(3));
        assertEquals(2, r.nextInt(3));
        assertEquals(10, r.nextInt(16));
        assertEquals(4, r.nextInt(16));
        assertEquals(17, r.nextInt(97));
        assertEquals(21, r.nextInt(97));
    }
}
