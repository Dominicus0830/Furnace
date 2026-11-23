package net.minecraft.world.level.levelgen;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.RandomSource;

/** 스레드 안전 옛 LCG 난수원(Mojang 이름 shim) — 바닐라 1.21.1 그대로(비교-교환 되풀이, {@code >>>} 로 비트를 자른다). */
class ThreadSafeLegacyRandomSource {

    private static final int MODULUS_BITS = 48;
    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;
    private final AtomicLong seed = new AtomicLong();
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public ThreadSafeLegacyRandomSource(long seed) { this.setSeed(seed); }

    @Override
    public RandomSource fork() { return new ThreadSafeLegacyRandomSource(this.nextLong()); }

    @Override
    public PositionalRandomFactory forkPositional() { return new LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong()); }

    @Override
    public void setSeed(long s) { this.seed.set((s ^ MULTIPLIER) & MODULUS_MASK); }

    @Override
    public int next(int bits) {
        long l;
        long m;
        do {
            l = this.seed.get();
            m = l * MULTIPLIER + INCREMENT & MODULUS_MASK;
        } while (!this.seed.compareAndSet(l, m));
        return (int) (m >>> MODULUS_BITS - bits);
    }

    @Override
    public double nextGaussian() { return this.gaussianSource.nextGaussian(); }
}
