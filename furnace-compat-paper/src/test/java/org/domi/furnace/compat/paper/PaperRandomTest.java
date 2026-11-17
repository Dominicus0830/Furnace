// T-217 PaperRandom(RandomWrapper(WorldgenRandom(LegacyRandomSource)) 흉내)이 java.util.Random 과 다른 부분을 고정한다.
package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class PaperRandomTest {

    static BukkitChunkGenerators.PaperRandom seeded(long seed) {
        BukkitChunkGenerators.PaperRandom r = new BukkitChunkGenerators.PaperRandom();
        r.setSeed(seed);
        return r;
    }

    @Test
    void sameStreamAsJavaRandomForPlainMethods() {
        Random ref = new Random(42L);
        BukkitChunkGenerators.PaperRandom r = seeded(42L);
        for (int i = 0; i < 64; i++) {
            assertEquals(ref.nextInt(), r.nextInt());
            assertEquals(ref.nextInt(90), r.nextInt(90));
            assertEquals(ref.nextInt(16), r.nextInt(16));
            assertEquals(ref.nextLong(), r.nextLong());
            assertEquals(ref.nextBoolean(), r.nextBoolean());
            assertEquals(ref.nextFloat(), r.nextFloat());
            assertEquals(ref.nextDouble(), r.nextDouble());
        }
    }

    @Test
    void boundedNextIntIsOriginPlusNextInt() {
        // RandomWrapper#nextInt(a, b) = a + nextInt(b - a). 2의 거듭제곱 폭에서는 자바 21 Random#nextInt(a, b)(아래 비트)와 갈린다.
        BukkitChunkGenerators.PaperRandom r = seeded(42L);
        int[] got = new int[6];
        for (int i = 0; i < got.length; i++) got[i] = r.nextInt(4, 20);
        assertArrayEquals(new int[] {15, 4, 14, 4, 8, 19}, got);

        Random java = new Random(42L);
        int[] javaGot = new int[6];
        for (int i = 0; i < javaGot.length; i++) javaGot[i] = java.nextInt(4, 20);
        assertArrayEquals(new int[] {9, 11, 5, 12, 8, 15}, javaGot, "비교 기준(자바 Random) — 이 값과 달라야 Paper 와 같다");

        assertThrows(IllegalArgumentException.class, () -> seeded(1L).nextInt(5, 5));
    }

    @Test
    void gaussianPairSurvivesSetSeed() {
        // 바닐라 WorldgenRandom#setSeed 는 안쪽 원천만 다시 심는다 — 바깥의 남은 가우스 값은 그대로 다음 nextGaussian 이 꺼낸다.
        BukkitChunkGenerators.PaperRandom r = seeded(42L);
        double first = r.nextGaussian();
        r.setSeed(42L);
        double carried = r.nextGaussian();
        double fresh = r.nextGaussian();

        double[] pair = marsaglia(new Random(42L));
        assertEquals(pair[0], first);
        assertEquals(pair[1], carried, "setSeed 뒤 첫 가우스는 남아 있던 짝이다");
        assertEquals(marsaglia(new Random(42L))[0], fresh, "그다음은 다시 심은 씨앗에서 새로 뽑는다");

        Random java = new Random(42L);
        double j1 = java.nextGaussian();
        java.setSeed(42L);
        assertEquals(j1, java.nextGaussian(), "비교 기준: 자바 Random 은 setSeed 가 남은 가우스를 지운다");
        assertNotEquals(first, carried);
    }

    /** 바닐라 MarsagliaPolarGaussian(Math.log·Math.sqrt) — 같은 LCG 의 nextDouble 로 짝을 만든다. */
    static double[] marsaglia(Random src) {
        while (true) {
            double d = 2.0 * src.nextDouble() - 1.0;
            double e = 2.0 * src.nextDouble() - 1.0;
            double f = d * d + e * e;
            if (f < 1.0 && f != 0.0) {
                double g = Math.sqrt(-2.0 * Math.log(f) / f);
                return new double[] {d * g, e * g};
            }
        }
    }

    @Test
    void decorationSeedMatchesWorldgenRandom() {
        // WorldgenRandom#setDecorationSeed: 시드로 심고 nextLong|1 두 개 → x*l + z*m ^ 시드.
        long seed = 12345L;
        Random ref = new Random(seed);
        long l = ref.nextLong() | 1L, m = ref.nextLong() | 1L;
        long expected = 3L * l + -7L * m ^ seed;
        BukkitChunkGenerators.PaperRandom r = new BukkitChunkGenerators.PaperRandom();
        assertEquals(expected, r.setDecorationSeed(seed, 3, -7));
        assertEquals(new Random(expected).nextInt(), r.nextInt());
    }
}
