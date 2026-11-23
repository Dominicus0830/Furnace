package net.minecraft.world.level.levelgen;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.RandomSource;

/**
 * 옛 LCG 난수원(Mojang 이름 shim, N2b-6) — 바닐라 1.21.1 그대로(48비트, 곱 25214903917, 더함 11). 바닐라처럼 두 스레드가 동시에 쓰면
 * 예외를 던진다(바닐라는 ThreadingDetector 의 예외 — 이 shim 에는 그 클래스가 없어 IllegalStateException).
 */
class LegacyRandomSource {

    private static final int MODULUS_BITS = 48;
    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;
    private final AtomicLong seed = new AtomicLong();
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public LegacyRandomSource(long seed) { setSeed(seed); }

    @Override
    public RandomSource fork() { return new LegacyRandomSource(nextLong()); }

    @Override
    public void setSeed(long s) {
        if (!seed.compareAndSet(seed.get(), (s ^ MULTIPLIER) & MODULUS_MASK)) throw threading();
        gaussianSource.reset();
    }

    @Override
    public int next(int bits) {
        long cur = seed.get();
        long nxt = cur * MULTIPLIER + INCREMENT & MODULUS_MASK;
        if (!seed.compareAndSet(cur, nxt)) throw threading();
        return (int) (nxt >> MODULUS_BITS - bits);
    }

    @Override
    public double nextGaussian() { return gaussianSource.nextGaussian(); }

    @Override
    public PositionalRandomFactory forkPositional() { return new LegacyPositionalRandomFactory(nextLong()); }

    /** 바닐라 LegacyRandomSource.LegacyPositionalRandomFactory: 위치 씨앗(Mth.getSeed)·이름 해시를 이 공장의 씨앗과 섞는다. */
    public static class LegacyPositionalRandomFactory implements PositionalRandomFactory {
        private final long seed;

        public LegacyPositionalRandomFactory(long seed) { this.seed = seed; }

        @Override
        public RandomSource at(int x, int y, int z) {
            long l = net.minecraft.util.Mth.getSeed(x, y, z);
            long m = l ^ this.seed;
            return new LegacyRandomSource(m);
        }

        @Override
        public RandomSource fromHashOf(String name) {
            int i = name.hashCode();
            return new LegacyRandomSource((long) i ^ this.seed);
        }

        @Override
        public RandomSource fromSeed(long seed) { return new LegacyRandomSource(seed); }

        @Override
        public void parityConfigString(StringBuilder sb) { sb.append("LegacyPositionalRandomFactory{").append(this.seed).append("}"); }
    }

    private static RuntimeException threading() { return new IllegalStateException("Accessing LegacyRandomSource from multiple threads"); }
}
