// 바닐라 decoration용 Xoroshiro128++ 기반 WorldgenRandom — feature 배치에 사용
package org.domi.furnace.server.world.gen.carver;

/**
 * Vanilla applyBiomeDecoration은 WorldgenRandom(XoroshiroRandomSource)를 사용한다.
 * 같은 seed라도 LegacyRandom과 값이 달라지므로 feature 배치가 달라진다.
 * 여기서는 raw XoroshiroRandomSource가 아니라 WorldgenRandom 래퍼의 bit-consumption 규칙까지 맞춘다.
 */
public final class XoroshiroWorldgenRandom extends WorldgenRandom {

    private long seedLo;
    private long seedHi;

    public XoroshiroWorldgenRandom() {
        super();
    }

    @Override
    public void setSeed(long seed) {
        // RandomSupport.upgradeSeedTo128bit(seed) → Xoroshiro128PlusPlus(lo, hi)
        long lo = seed ^ 7640891576956012809L;
        long hi = lo + -7046029254386353131L;
        this.seedLo = mixStafford13(lo);
        this.seedHi = mixStafford13(hi);
        if ((this.seedLo | this.seedHi) == 0L) {
            this.seedLo = -7046029254386353131L;
            this.seedHi =  7640891576956012809L;
        }
    }

    // RandomSupport.mixStafford13
    private static long mixStafford13(long n) {
        n = (n ^ n >>> 30) * -4658895280553007687L;
        n = (n ^ n >>> 27) * -7723592293110705685L;
        return n ^ n >>> 31;
    }

    private long nextRawLong() {
        long lo = this.seedLo;
        long hi = this.seedHi;
        long out = Long.rotateLeft(lo + hi, 17) + lo;
        hi ^= lo;
        this.seedLo = Long.rotateLeft(lo, 49) ^ hi ^ (hi << 21);
        this.seedHi = Long.rotateLeft(hi, 28);
        return out;
    }

    private long nextBits(int bits) {
        return nextRawLong() >>> (64 - bits);
    }

    // vanilla WorldgenRandom(XoroshiroRandomSource).nextInt() = Java Random.nextInt() via next(32)
    // next(32) = (int)(xoro.nextLong() >>> 32) = top 32 bits of one advance
    @Override
    public int nextInt() {
        int value = (int) nextBits(32);
        RandomTrace.nextInt(value);
        return value;
    }

    // vanilla WorldgenRandom.nextInt(bound) = Java Random.nextInt(bound) via next(31) rejection loop
    // next(31) = (int)(xoro.nextLong() >>> 33) = top 31 bits of one advance
    @Override
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
        if ((bound & -bound) == bound) {
            int value = (int) ((bound * (long) ((int) nextBits(31))) >> 31);
            RandomTrace.nextInt(bound, value);
            return value;
        }
        int bits;
        int val;
        do {
            bits = (int) nextBits(31);
            val = bits % bound;
        } while (bits - val + (bound - 1) < 0);
        RandomTrace.nextInt(bound, val);
        return val;
    }

    // vanilla WorldgenRandom.nextLong() = Java Random.nextLong() = ((long)next(32)<<32)+next(32)
    // = TWO advances: top 32 bits each, concatenated
    @Override
    public long nextLong() {
        int hi = (int) nextBits(32);
        int lo = (int) nextBits(32);
        return ((long) hi << 32) + lo;
    }

    @Override
    public float nextFloat() {
        float value = (float) nextBits(24) * 5.9604645E-8F;
        RandomTrace.nextFloat(value);
        return value;
    }

    // vanilla WorldgenRandom.nextDouble() = Java Random.nextDouble() via next(26)+next(27)
    // = TWO advances: top 26 bits and top 27 bits
    @Override
    public double nextDouble() {
        double value = (((long) nextBits(26) << 27) + nextBits(27)) * 1.1102230246251565E-16D;
        RandomTrace.nextDouble(value);
        return value;
    }

    // vanilla WorldgenRandom.nextBoolean() = Java Random.nextBoolean() = next(1) != 0
    // next(1) = (int)(xoro.nextLong() >>> 63) = top bit of one advance
    @Override
    public boolean nextBoolean() {
        boolean value = nextBits(1) != 0L;
        RandomTrace.nextBoolean(value);
        return value;
    }
}
