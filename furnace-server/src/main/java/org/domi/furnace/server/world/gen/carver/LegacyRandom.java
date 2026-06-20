// 바닐라 Java 48-bit LCG — WorldgenRandom(carver 시드)과 createTunnel branchRandom 공용
package org.domi.furnace.server.world.gen.carver;

public class LegacyRandom {

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND     = 0xBL;
    private static final long MASK       = (1L << 48) - 1;

    private long seed;

    public LegacyRandom(long seed) {
        setSeed(seed);
    }

    public void setSeed(long seed) {
        this.seed = (seed ^ MULTIPLIER) & MASK;
    }

    private int next(int bits) {
        seed = (seed * MULTIPLIER + ADDEND) & MASK;
        return (int)(seed >>> (48 - bits));
    }

    public int nextInt() {
        return next(32);
    }

    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException();
        if ((bound & -bound) == bound) return (int)((bound * (long)next(31)) >> 31);
        int bits, val;
        do {
            bits = next(31);
            val  = bits % bound;
        } while (bits - val + (bound - 1) < 0);
        return val;
    }

    public long nextLong() {
        int hi = next(32);
        int lo = next(32);
        return ((long)hi << 32) + lo;  // lo is signed (matches vanilla BitRandomSource)
    }

    public float nextFloat() {
        return next(24) / (float)(1 << 24);
    }

    public double nextDouble() {
        return (((long)next(26) << 27) + next(27)) * (1.0 / (1L << 53));
    }

    public boolean nextBoolean() {
        return next(1) != 0;
    }
}
