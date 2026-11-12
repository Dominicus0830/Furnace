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

    /** createFor 의 스레드 안전판(바닐라 createThreadSafe): 여러 스레드가 함께 쓰는 정적 난수용. */
    static RandomSource createThreadSafeFor(long ownerKey, int purpose) { return new ThreadSafeLegacyRandomSource(RandomSeeds.seed(ownerKey, purpose)); }

    static RandomSource create(long seed) { return new LegacyRandomSource(seed); }
}
