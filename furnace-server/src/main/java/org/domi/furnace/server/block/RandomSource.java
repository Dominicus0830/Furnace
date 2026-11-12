// 바닐라 RandomSource(블록 동작이 쓰는 부분). 레벨 난수는 LegacyRandomSource(java.util.Random 과 같은 LCG).
package org.domi.furnace.server.block;

public interface RandomSource {
    int nextInt();

    int nextInt(int bound);

    long nextLong();

    float nextFloat();

    double nextDouble();

    boolean nextBoolean();

    double nextGaussian();

    /** RandomSource.nextIntBetweenInclusive. */
    default int nextIntBetweenInclusive(int min, int max) { return nextInt(max - min + 1) + min; }

    /** RandomSource.triangle(center, spread). */
    default double triangle(double center, double spread) { return center + spread * (nextDouble() - nextDouble()); }

    /** RandomSource.create(): RandomSupport.generateUniqueSeed 로 씨앗을 고른 LegacyRandomSource. */
    static RandomSource create() { return new LegacyRandomSource(UniqueSeed.next()); }

    /**
     * RandomSource.createThreadSafe(): 여러 스레드가 함께 써도 상태가 깨지지 않는 LCG(바닐라 ThreadSafeLegacyRandomSource 는 CAS,
     * 여기서는 잠금 — 값의 분포는 같다). 정적 공유 난수(Sensor 시작 틱 등)에 쓴다.
     */
    static RandomSource createThreadSafe() { return new ThreadSafe(new LegacyRandomSource(UniqueSeed.next())); }

    /** RandomSupport.generateUniqueSeed: 곱해 가는 원자 값 ^ nanoTime(같은 순간에 만들어도 씨앗이 겹치지 않는다). */
    final class UniqueSeed {
        private static final java.util.concurrent.atomic.AtomicLong UNIQUIFIER = new java.util.concurrent.atomic.AtomicLong(8682522807148012L);

        private UniqueSeed() {}

        static long next() { return UNIQUIFIER.updateAndGet(v -> v * 1181783497276652981L) ^ System.nanoTime(); }
    }

    /** 잠금으로 감싼 난수. */
    final class ThreadSafe implements RandomSource {
        private final RandomSource r;

        ThreadSafe(RandomSource r) { this.r = r; }

        public synchronized int nextInt() { return r.nextInt(); }

        public synchronized int nextInt(int bound) { return r.nextInt(bound); }

        public synchronized long nextLong() { return r.nextLong(); }

        public synchronized float nextFloat() { return r.nextFloat(); }

        public synchronized double nextDouble() { return r.nextDouble(); }

        public synchronized boolean nextBoolean() { return r.nextBoolean(); }

        public synchronized double nextGaussian() { return r.nextGaussian(); }
    }

    static RandomSource create(long seed) { return new LegacyRandomSource(seed); }
}
