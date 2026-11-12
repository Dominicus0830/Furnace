// LegacyRandomSource 가 바닐라 LegacyRandomSource(BitRandomSource)와 비트까지 같은지 본다.
// 바닐라는 java.util.Random 과 같은 48비트 LCG 이고 nextDouble 은 (double)l * 1.1102230246251565E-16(=2^-53) 이다.
// 그래서 독립 기준으로 JDK 의 java.util.Random 을 쓰고(JDK 21 의 nextDouble/nextFloat/nextInt/nextLong/nextBoolean 은 같은 식),
// nextGaussian 은 바닐라 MarsagliaPolarGaussian(Math.log/Math.sqrt)을 직접 옮긴 기준과 비교한다.
// 예전 버그: 상수가 float(1.110223E-16F)라서 long×float 가 float 곱셈이 되어 24비트로 깎이고 최댓값이 1.0 이 됐다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class LegacyRandomSourceVanillaTest {
    private static final long[] SEEDS = {0L, 1L, 42L, -1L, 0x5DEECE66DL, Long.MIN_VALUE, Long.MAX_VALUE, 123456789012345L, 8675309L, -7046029254386353131L};
    private static final double TWO_POW_53 = (double) (1L << 53);

    @Test
    void firstDoubleMatchesKnownJavaUtilRandomValue() {
        // new Random(42).nextDouble() — 널리 알려진 값. 바닐라 LegacyRandomSource(42)도 같다.
        assertEquals(0.7275636800328681, new LegacyRandomSource(42).nextDouble());
        assertEquals(0.730967787376657, new LegacyRandomSource(0).nextDouble());
    }

    @Test
    void nextDoubleSequenceIsBitIdenticalToJavaUtilRandom() {
        for (long seed : SEEDS) {
            LegacyRandomSource f = new LegacyRandomSource(seed);
            Random ref = new Random(seed);
            for (int i = 0; i < 200_000; i++) {
                assertEquals(Double.doubleToRawLongBits(ref.nextDouble()), Double.doubleToRawLongBits(f.nextDouble()), "seed " + seed + " #" + i);
            }
        }
    }

    @Test
    void mixedCallSequenceMatchesJavaUtilRandom() {
        for (long seed : SEEDS) {
            LegacyRandomSource f = new LegacyRandomSource(seed);
            Random ref = new Random(seed);
            Random pick = new Random(seed ^ 0xABCDEF);
            for (int i = 0; i < 100_000; i++) {
                String where = "seed " + seed + " #" + i;
                switch (pick.nextInt(8)) {
                    case 0 -> assertEquals(ref.nextInt(), f.nextInt(), where);
                    case 1 -> {
                        int bound = 1 + pick.nextInt(1 << (1 + pick.nextInt(30)));
                        assertEquals(ref.nextInt(bound), f.nextInt(bound), where);
                    }
                    case 2 -> assertEquals(ref.nextLong(), f.nextLong(), where);
                    case 3 -> assertEquals(ref.nextBoolean(), f.nextBoolean(), where);
                    case 4 -> assertEquals(Float.floatToRawIntBits(ref.nextFloat()), Float.floatToRawIntBits(f.nextFloat()), where);
                    default -> assertEquals(Double.doubleToRawLongBits(ref.nextDouble()), Double.doubleToRawLongBits(f.nextDouble()), where);
                }
            }
        }
    }

    @Test
    void nextDoubleHasFull53BitsAndStaysBelowOne() {
        // 24비트로 깎인 값이면 2^-53 단위의 하위 29비트가 항상 0 이다. 진짜 값은 그렇지 않다.
        LegacyRandomSource f = new LegacyRandomSource(2024);
        int lowBitsSet = 0;
        for (int i = 0; i < 1_000_000; i++) {
            double d = f.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0, "범위 밖: " + d);
            double scaled = d * TWO_POW_53;
            assertEquals(Math.rint(scaled), scaled, "2^-53 의 배수가 아님: " + d);
            if ((((long) scaled) & ((1L << 29) - 1)) != 0) lowBitsSet++;
        }
        assertTrue(lowBitsSet > 990_000, "하위 비트가 거의 항상 채워져야 한다: " + lowBitsSet);
    }

    @Test
    void largestDrawIsBelowOne() {
        // 다음 두 번의 next(26)·next(27)이 거의 최댓값이 되게 상태를 맞춘다.
        // 첫 단계 뒤 상태의 위 26비트가 전부 1 이 되도록 역으로 구한다(LCG 곱셈 역원).
        long mask = (1L << 48) - 1;
        long afterFirst = mask; // 위 26비트 전부 1, 아래 22비트도 1
        long inv = modInverse48(0x5DEECE66DL);
        long state = ((afterFirst - 0xBL) * inv) & mask;
        LegacyRandomSource f = new LegacyRandomSource(0);
        f.restoreState(state, false, 0.0);
        double d = f.nextDouble();
        // 첫 26비트가 전부 1 → 값은 1 에 아주 가깝지만 1.0 보다는 작아야 한다(예전 float 곱은 1.0 이 됐다).
        assertTrue(d < 1.0 && d > 1.0 - 1e-7, "값 " + d);
        // 같은 상태에서 java.util.Random 도 같은 값
        Random ref = new Random(0);
        java.lang.reflect.Field fld;
        try {
            fld = Random.class.getDeclaredField("seed");
            fld.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicLong) fld.get(ref)).set(state);
            assertEquals(ref.nextDouble(), d);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 모듈 접근이 막힌 JVM 에서는 위 비교만 건너뛴다. 바닐라 식 직접 계산으로 확인한다.
            long s1 = (state * 0x5DEECE66DL + 0xBL) & mask;
            long s2 = (s1 * 0x5DEECE66DL + 0xBL) & mask;
            long l = (((long) (int) (s1 >> 22)) << 27) + (int) (s2 >> 21);
            assertEquals((double) l * 1.1102230246251565E-16, d);
        }
    }

    @Test
    void gaussianMatchesVanillaMarsagliaPolar() {
        for (long seed : SEEDS) {
            LegacyRandomSource f = new LegacyRandomSource(seed);
            VanillaRef ref = new VanillaRef(seed);
            for (int i = 0; i < 100_000; i++) {
                assertEquals(Double.doubleToRawLongBits(ref.nextGaussian()), Double.doubleToRawLongBits(f.nextGaussian()), "seed " + seed + " #" + i);
            }
        }
    }

    @Test
    void trianglePreservesVanillaOrder() {
        for (long seed : SEEDS) {
            LegacyRandomSource f = new LegacyRandomSource(seed);
            Random ref = new Random(seed);
            for (int i = 0; i < 10_000; i++) {
                double a = ref.nextDouble();
                double b = ref.nextDouble();
                assertEquals(3.0 + 0.5 * (a - b), f.triangle(3.0, 0.5));
            }
        }
    }

    /** 바닐라 LegacyRandomSource + MarsagliaPolarGaussian 을 옮긴 기준(nextDouble 은 double 상수). */
    private static final class VanillaRef {
        private long seed;
        private double nextNextGaussian;
        private boolean have;

        VanillaRef(long s) { seed = (s ^ 25214903917L) & ((1L << 48) - 1); }

        int next(int bits) {
            seed = seed * 25214903917L + 11L & ((1L << 48) - 1);
            return (int) (seed >> 48 - bits);
        }

        double nextDouble() {
            int i = next(26);
            int j = next(27);
            long l = ((long) i << 27) + j;
            return (double) l * 1.1102230246251565E-16;
        }

        double nextGaussian() {
            if (have) {
                have = false;
                return nextNextGaussian;
            }
            while (true) {
                double d = 2.0 * nextDouble() - 1.0;
                double e = 2.0 * nextDouble() - 1.0;
                double f = d * d + e * e;
                if (!(f >= 1.0) && f != 0.0) {
                    double g = Math.sqrt(-2.0 * Math.log(f) / f);
                    nextNextGaussian = e * g;
                    have = true;
                    return d * g;
                }
            }
        }
    }

    private static long modInverse48(long a) {
        long x = a; // Newton 반복: x = x*(2 - a*x) 를 mod 2^64 로 돌리면 역원
        for (int i = 0; i < 6; i++) x *= 2 - a * x;
        return x & ((1L << 48) - 1);
    }
}
