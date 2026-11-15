// 플러그인이 준 java.util.Random 을 지형 생성 난수로 쓴다(Paper RandomSourceWrapper 와 같다): 모든 뽑기를 그 Random 에 그대로 넘겨
// 플러그인 쪽 Random 상태도 Paper 처럼 나아간다. consumeCount 는 바닐라 RandomSource 기본(nextInt() 반복), forkPositional 은 Paper 처럼 지원하지 않는다.
package org.domi.furnace.server.world;

import java.util.Objects;
import java.util.Random;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class JavaRandomWorldgen extends WorldgenRandom {

    private final Random random;

    public JavaRandomWorldgen(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    @Override
    public void setSeed(long seed) {
        if (random != null) random.setSeed(seed);   // 상위 생성자의 setSeed(0) 는 건너뛴다
    }

    @Override
    public int nextInt() { return random.nextInt(); }

    @Override
    public int nextInt(int bound) { return random.nextInt(bound); }

    @Override
    public long nextLong() { return random.nextLong(); }

    @Override
    public float nextFloat() { return random.nextFloat(); }

    @Override
    public double nextDouble() { return random.nextDouble(); }

    @Override
    public boolean nextBoolean() { return random.nextBoolean(); }

    @Override
    public double nextGaussian() { return random.nextGaussian(); }

    @Override
    public void consumeCount(int count) {
        for (int i = 0; i < count; i++) random.nextInt();
    }

    @Override
    public org.domi.furnace.server.world.gen.noise.LegacyPositionalRandom forkPositional() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void restoreState(long rawSeed, boolean haveGaussian, double gaussian) {
        throw new UnsupportedOperationException("java.util.Random 난수의 상태는 옮기지 않는다");
    }
}
