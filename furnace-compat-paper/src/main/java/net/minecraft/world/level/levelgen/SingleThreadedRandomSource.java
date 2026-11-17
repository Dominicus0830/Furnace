package net.minecraft.world.level.levelgen;

import net.minecraft.util.RandomSource;

/** 한 스레드용 옛 LCG 난수원(Mojang 이름 shim, N2b-6) — 바닐라 1.21.1 그대로(검사 없는 {@link LegacyRandomSource}). */
public class SingleThreadedRandomSource implements BitRandomSource {

    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;
    private long seed;
    private final MarsagliaPolarGaussian gaussianSource = new MarsagliaPolarGaussian(this);

    public SingleThreadedRandomSource(long seed) { setSeed(seed); }

    @Override
    public RandomSource fork() { return new SingleThreadedRandomSource(nextLong()); }

    @Override
    public void setSeed(long s) {
        seed = (s ^ MULTIPLIER) & MODULUS_MASK;
        gaussianSource.reset();
    }

    @Override
    public int next(int bits) {
        long nxt = seed * MULTIPLIER + INCREMENT & MODULUS_MASK;
        seed = nxt;
        return (int) (nxt >> 48 - bits);
    }

    @Override
    public double nextGaussian() { return gaussianSource.nextGaussian(); }
}
