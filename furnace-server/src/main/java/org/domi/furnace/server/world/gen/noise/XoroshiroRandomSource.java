// 바닐라 XoroshiroRandomSource 비트정확 포팅(worldgen 난수 소스)
package org.domi.furnace.server.world.gen.noise;

import org.domi.furnace.server.world.gen.MathHelper;

public class XoroshiroRandomSource implements NoiseRandom {
    private static final float FLOAT_UNIT = 5.9604645E-8F;
    // 바닐라 bytecode: ldc2_w (double constant) 1.1102230246251565E-16d — 디컴파일러가 F(float) 접미사로 잘못 표기
    private static final double DOUBLE_UNIT = 1.1102230246251565E-16;
    private Xoroshiro128PlusPlus randomNumberGenerator;

    public XoroshiroRandomSource(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
    }

    public XoroshiroRandomSource(RandomSupport.Seed128bit seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(seed);
    }

    public XoroshiroRandomSource(long seedLo, long seedHi) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(seedLo, seedHi);
    }

    public XoroshiroRandomSource fork() {
        return new XoroshiroRandomSource(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    @Override
    public XoroshiroPositionalRandomFactory forkPositional() {
        return new XoroshiroPositionalRandomFactory(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    public void setSeed(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
    }

    @Override
    public int nextInt() {
        return (int) this.randomNumberGenerator.nextLong();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        } else {
            long l = Integer.toUnsignedLong(this.nextInt());
            long l1 = l * bound;
            long l2 = l1 & 4294967295L;
            if (l2 < bound) {
                for (int i = Integer.remainderUnsigned(~bound + 1, bound); l2 < i; l2 = l1 & 4294967295L) {
                    l = Integer.toUnsignedLong(this.nextInt());
                    l1 = l * bound;
                }
            }
            long l3 = l1 >> 32;
            return (int) l3;
        }
    }

    @Override
    public long nextLong() {
        return this.randomNumberGenerator.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return (this.randomNumberGenerator.nextLong() & 1L) != 0L;
    }

    @Override
    public float nextFloat() {
        return (float) this.nextBits(24) * 5.9604645E-8F;
    }

    @Override
    public double nextDouble() {
        return this.nextBits(53) * DOUBLE_UNIT;
    }

    @Override
    public void consumeCount(int count) {
        for (int i = 0; i < count; i++) {
            this.randomNumberGenerator.nextLong();
        }
    }

    private long nextBits(int bits) {
        return this.randomNumberGenerator.nextLong() >>> 64 - bits;
    }

    public static class XoroshiroPositionalRandomFactory implements PositionalRandom {
        private final long seedLo;
        private final long seedHi;

        public XoroshiroPositionalRandomFactory(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        @Override
        public XoroshiroRandomSource at(int x, int y, int z) {
            long seed = MathHelper.getSeed(x, y, z);
            long l = seed ^ this.seedLo;
            return new XoroshiroRandomSource(l, this.seedHi);
        }

        @Override
        public XoroshiroRandomSource fromHashOf(String name) {
            RandomSupport.Seed128bit seed128bit = RandomSupport.seedFromHashOf(name);
            return new XoroshiroRandomSource(seed128bit.xor(this.seedLo, this.seedHi));
        }

        // 바닐라 XoroshiroPositionalRandomFactory.fromHashOf 동작: 이름-파생 시드로 새 위치기반 팩토리 반환
        public XoroshiroPositionalRandomFactory fromHashOfPositional(String name) {
            RandomSupport.Seed128bit hash = RandomSupport.seedFromHashOf(name);
            return new XoroshiroPositionalRandomFactory(hash.seedLo() ^ seedLo, hash.seedHi() ^ seedHi);
        }

        @Override
        public XoroshiroRandomSource fromSeed(long seed) {
            return new XoroshiroRandomSource(seed ^ this.seedLo, seed ^ this.seedHi);
        }
    }
}
