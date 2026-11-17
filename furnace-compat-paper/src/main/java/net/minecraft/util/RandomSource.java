package net.minecraft.util;

/**
 * 난수원(Mojang 이름 shim, N2b-6) — 바닐라 1.21.1 그대로({@link #create()} 는 바닐라처럼 겹치지 않는 씨앗의 옛 LCG 난수원).
 * 위치별 난수 공장({@code forkPositional})은 PositionalRandomFactory shim 이 없어 두지 않았다.
 */
public interface RandomSource {

    double GAUSSIAN_SPREAD_FACTOR = 2.297;

    static RandomSource create() { return create(net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()); }

    static RandomSource create(long seed) { return new net.minecraft.world.level.levelgen.LegacyRandomSource(seed); }

    static RandomSource createNewThreadLocalInstance() {
        return new net.minecraft.world.level.levelgen.SingleThreadedRandomSource(java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    RandomSource fork();

    void setSeed(long seed);

    int nextInt();

    int nextInt(int bound);

    default int nextIntBetweenInclusive(int min, int max) { return nextInt(max - min + 1) + min; }

    long nextLong();

    boolean nextBoolean();

    float nextFloat();

    double nextDouble();

    double nextGaussian();

    default double triangle(double mode, double deviation) { return mode + deviation * (nextDouble() - nextDouble()); }

    default void consumeCount(int count) {
        for (int i = 0; i < count; i++) nextInt();
    }

    default int nextInt(int origin, int bound) {
        if (origin >= bound) throw new IllegalArgumentException("bound - origin is non positive");
        return origin + nextInt(bound - origin);
    }
}
