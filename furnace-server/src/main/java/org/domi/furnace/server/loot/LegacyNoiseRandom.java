// 블록 쪽 LegacyRandomSource(java.util.Random 과 같은 LCG)를 노이즈/전리품 쪽 NoiseRandom 인터페이스로 보이게 하는 어댑터.
package org.domi.furnace.server.loot;

import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.domi.furnace.server.world.gen.noise.PositionalRandom;

public final class LegacyNoiseRandom implements NoiseRandom {
    private final LegacyRandomSource r;

    public LegacyNoiseRandom(LegacyRandomSource r) { this.r = r; }

    @Override public int nextInt() { return r.nextInt(); }
    @Override public int nextInt(int bound) { return r.nextInt(bound); }
    @Override public long nextLong() { return r.nextLong(); }
    @Override public float nextFloat() { return r.nextFloat(); }
    @Override public double nextDouble() { return r.nextDouble(); }
    @Override public boolean nextBoolean() { return r.nextBoolean(); }
    @Override public void consumeCount(int count) { for (int i = 0; i < count; i++) r.nextInt(); }
    @Override public PositionalRandom forkPositional() { throw new UnsupportedOperationException("legacy random positional fork"); }
}
