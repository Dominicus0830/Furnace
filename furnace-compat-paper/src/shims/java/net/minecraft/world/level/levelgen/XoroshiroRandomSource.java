package net.minecraft.world.level.levelgen;

import net.minecraft.util.RandomSource;

/** Xoroshiro 난수원(Mojang 이름 shim) — 바닐라 1.21.1 의 호출 순서 그대로(난수 한 번 = {@code nextLong} 한 번, 정수 범위는 곱셈 거절 표집). 코덱은 껍데기(T-261)다. */
class XoroshiroRandomSource {

    private static final float FLOAT_UNIT = 5.9604645E-8F;
    private static final double DOUBLE_UNIT = 1.1102230246251565E-16;

    private Xoroshiro128PlusPlus randomNumberGenerator;
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public XoroshiroRandomSource(long seed) { this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed)); }

    public XoroshiroRandomSource(RandomSupport.Seed128bit seed) { this.randomNumberGenerator = new Xoroshiro128PlusPlus(seed); }

    public XoroshiroRandomSource(long seedLo, long seedHi) { this.randomNumberGenerator = new Xoroshiro128PlusPlus(seedLo, seedHi); }

    private XoroshiroRandomSource(Xoroshiro128PlusPlus generator) { this.randomNumberGenerator = generator; }

    @Override
    public RandomSource fork() { return new XoroshiroRandomSource(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong()); }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new XoroshiroPositionalRandomFactory(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
        this.gaussianSource.reset();
    }

    @Override
    public int nextInt() { return (int) this.randomNumberGenerator.nextLong(); }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
        long l = Integer.toUnsignedLong(this.nextInt());
        long m = l * (long) bound;
        long n = m & 4294967295L;
        if (n < (long) bound) {
            for (int i = Integer.remainderUnsigned(~bound + 1, bound); n < (long) i; n = m & 4294967295L) {
                l = Integer.toUnsignedLong(this.nextInt());
                m = l * (long) bound;
            }
        }
        long o = m >> 32;
        return (int) o;
    }

    @Override
    public long nextLong() { return this.randomNumberGenerator.nextLong(); }

    @Override
    public boolean nextBoolean() { return (this.randomNumberGenerator.nextLong() & 1L) != 0L; }

    @Override
    public float nextFloat() { return (float) this.nextBits(24) * FLOAT_UNIT; }

    @Override
    public double nextDouble() { return (double) this.nextBits(53) * DOUBLE_UNIT; }

    @Override
    public double nextGaussian() { return this.gaussianSource.nextGaussian(); }

    @Override
    public void consumeCount(int count) {
        for (int i = 0; i < count; i++) this.randomNumberGenerator.nextLong();
    }

    private long nextBits(int bits) { return this.randomNumberGenerator.nextLong() >>> 64 - bits; }

    /** 바닐라 XoroshiroPositionalRandomFactory: 위치 씨앗은 아래 절반에, 이름 해시는 128비트에 섞는다. */
    public static class XoroshiroPositionalRandomFactory implements PositionalRandomFactory {
        private final long seedLo;
        private final long seedHi;

        public XoroshiroPositionalRandomFactory(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            long l = net.minecraft.util.Mth.getSeed(x, y, z);
            long m = l ^ this.seedLo;
            return new XoroshiroRandomSource(m, this.seedHi);
        }

        @Override
        public RandomSource fromHashOf(String name) {
            RandomSupport.Seed128bit seed128bit = RandomSupport.seedFromHashOf(name);
            return new XoroshiroRandomSource(seed128bit.xor(this.seedLo, this.seedHi));
        }

        @Override
        public RandomSource fromSeed(long seed) { return new XoroshiroRandomSource(seed ^ this.seedLo, seed ^ this.seedHi); }

        @Override
        public void parityConfigString(StringBuilder sb) { sb.append("seedLo: ").append(this.seedLo).append(", seedHi: ").append(this.seedHi); }
    }
}
