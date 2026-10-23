// 바닐라 RandomSource 의 노이즈·월드젠 공용 부분 — Xoroshiro(신규) 와 Legacy(LCG, 네더·엔드 legacy_random_source) 를 같은 코드로 쓰기 위한 인터페이스.
package org.domi.furnace.server.world.gen.noise;

public interface NoiseRandom {
    int nextInt();

    int nextInt(int bound);

    long nextLong();

    float nextFloat();

    double nextDouble();

    boolean nextBoolean();

    /** RandomSource.consumeCount — Xoroshiro 는 nextLong, Legacy 는 nextInt 를 count 번. */
    void consumeCount(int count);

    PositionalRandom forkPositional();
}
