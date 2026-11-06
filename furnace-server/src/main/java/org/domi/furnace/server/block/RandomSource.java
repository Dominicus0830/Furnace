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

    static RandomSource create() { return new LegacyRandomSource(System.nanoTime() ^ 0x5DEECE66DL * Thread.currentThread().threadId()); }

    static RandomSource create(long seed) { return new LegacyRandomSource(seed); }
}
