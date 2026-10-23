// 바닐라 LegacyRandomSource — java.util.Random 과 같은 48비트 LCG(스레드 하나에서만 쓴다).
package org.domi.furnace.server.block;

public final class LegacyRandomSource implements RandomSource {
    private static final long MULT = 0x5DEECE66DL, ADD = 0xBL, MASK = (1L << 48) - 1;

    private long seed;
    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    public LegacyRandomSource(long seed) { setSeed(seed); }

    public void setSeed(long s) {
        seed = (s ^ MULT) & MASK;
        haveNextNextGaussian = false;
    }

    private int next(int bits) {
        seed = seed * MULT + ADD & MASK;
        return (int) (seed >> 48 - bits);
    }

    public int nextInt() { return next(32); }

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
        if ((bound & -bound) == bound) return (int) ((long) bound * next(31) >> 31);
        int bits, val;
        do {
            bits = next(31);
            val = bits % bound;
        } while (bits - val + (bound - 1) < 0);
        return val;
    }

    public long nextLong() { return ((long) next(32) << 32) + next(32); }

    public float nextFloat() { return next(24) * 5.9604645E-8F; }

    public double nextDouble() { return (((long) next(26) << 27) + next(27)) * 1.110223E-16F; }

    public boolean nextBoolean() { return next(1) != 0; }

    public double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        }
        double v1, v2, s;
        do {
            v1 = 2 * nextDouble() - 1;
            v2 = 2 * nextDouble() - 1;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1 || s == 0);
        double m = Math.sqrt(-2.0 * Math.log(s) / s);   // 바닐라 MarsagliaPolarGaussian
        nextNextGaussian = v2 * m;
        haveNextNextGaussian = true;
        return v1 * m;
    }
}
