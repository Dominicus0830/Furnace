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

    /**
     * 주인 열쇠로 난수를 만든다(RandomSeeds). 운영에서는 예측 불가 씨앗 — 바닐라 RandomSource.create() 와 같다.
     * 시험 JVM(-Dfurnace.test.seeded=true)에서는 (열쇠, 용도)에서 파생해 같은 장면이 같은 난수열을 얻는다.
     */
    static RandomSource createFor(long ownerKey, int purpose) { return new LegacyRandomSource(RandomSeeds.seed(ownerKey, purpose)); }

    /**
     * RandomSource.createThreadSafe(): 여러 스레드가 함께 써도 상태가 깨지지 않는 LCG(바닐라 ThreadSafeLegacyRandomSource 는 CAS,
     * 여기서는 잠금 — 값의 분포는 같다). 정적 공유 난수(Sensor 시작 틱)에 쓴다. 씨앗은 createFor 와 같은 이음매.
     */
    static RandomSource createThreadSafeFor(long ownerKey, int purpose) { return new ThreadSafe(createFor(ownerKey, purpose)); }

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
