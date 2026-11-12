// 바닐라 ThreadSafeLegacyRandomSource — LegacyRandomSource 와 같은 48비트 LCG 를 CAS 로 돌려 여러 스레드가 함께 써도 상태가 깨지지 않는다.
// (가우스 보관값은 잠금으로 지킨다. 바닐라는 이 부분이 스레드 안전하지 않다.)
package org.domi.furnace.server.block;

import java.util.concurrent.atomic.AtomicLong;

public final class ThreadSafeLegacyRandomSource implements RandomSource {
    private static final long MULT = 0x5DEECE66DL, ADD = 0xBL, MASK = (1L << 48) - 1;

    private final AtomicLong seed = new AtomicLong();
    private final Object gaussianLock = new Object();
    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    public ThreadSafeLegacyRandomSource(long seed) { setSeed(seed); }

    public void setSeed(long s) {
        this.seed.set((s ^ MULT) & MASK);
        synchronized (gaussianLock) { haveNextNextGaussian = false; }
    }

    private int next(int bits) {
        long cur, nxt;
        do {
            cur = seed.get();
            nxt = cur * MULT + ADD & MASK;
        } while (!seed.compareAndSet(cur, nxt));
        return (int) (nxt >> 48 - bits);
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
        synchronized (gaussianLock) {
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
            double m = Math.sqrt(-2.0 * Math.log(s) / s);
            nextNextGaussian = v2 * m;
            haveNextNextGaussian = true;
            return v1 * m;
        }
    }
}
