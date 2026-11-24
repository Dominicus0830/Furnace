package net.minecraft.world.level.levelgen;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.RandomSource;

/** 스레드 안전 옛 LCG 난수원(Mojang 이름 shim) — 바닐라 1.21.1 그대로(비교-교환 되풀이, {@code >>>} 로 비트를 자른다). */
class ThreadSafeLegacyRandomSource {

    private static final int BITS = 48;
    private static final long MASK = 281474976710655L;
    private static final long MUL = 25214903917L;
    private static final long INC = 11L;
    private final AtomicLong seed = new AtomicLong();
    private final MarsagliaPolarGaussian gaussian = new MarsagliaPolarGaussian(this);

    public ThreadSafeLegacyRandomSource(long seed) { this.setSeed(seed); }

    @Override
    public RandomSource fork() { return new ThreadSafeLegacyRandomSource(this.nextLong()); }

    @Override
    public PositionalRandomFactory forkPositional() { return new LegacyRandomSource.LegacyPositionalRandomFactory(this.nextLong()); }

    @Override
    public void setSeed(long s) { this.seed.set((s ^ MUL) & MASK); }

    @Override
    public int next(int bits) {
        long l;
        long m;
        do {
            l = this.seed.get();
            m = l * MUL + INC & MASK;
        } while (!this.seed.compareAndSet(l, m));
        return (int) (m >>> BITS - bits);
    }

    /** 정규 분포 값은 한 칸짜리 캐시를 공유한다 — 바닐라처럼 스레드 안전하지 않다(여러 스레드가 동시에 부르면 캐시 값이 섞인다). 난수 본체({@link #next})만 안전하다. */
    @Override
    public double nextGaussian() { return this.gaussian.nextGaussian(); }
}
