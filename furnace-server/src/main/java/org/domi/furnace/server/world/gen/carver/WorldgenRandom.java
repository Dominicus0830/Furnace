// 바닐라 WorldgenRandom 포팅 — carver origin chunk별 시드 초기화
package org.domi.furnace.server.world.gen.carver;

public class WorldgenRandom extends LegacyRandom {

    public WorldgenRandom() {
        super(0L);
    }

    /** 바닐라 WorldgenRandom.setLargeFeatureSeed — vanilla: chunkX*rand ^ chunkZ*rand1 ^ baseSeed */
    public void setLargeFeatureSeed(long worldSeed, int x, int z) {
        setSeed(worldSeed);
        long randomLong  = nextLong();
        long randomLong1 = nextLong();
        setSeed((long)x * randomLong ^ (long)z * randomLong1 ^ worldSeed);
    }

    /** 바닐라 WorldgenRandom.setDecorationSeed — features/decoration per-chunk seed. */
    public long setDecorationSeed(long worldSeed, int blockX, int blockZ) {
        setSeed(worldSeed);
        long randomLong  = nextLong() | 1L;
        long randomLong1 = nextLong() | 1L;
        long decorationSeed = ((long) blockX * randomLong + (long) blockZ * randomLong1) ^ worldSeed;
        setSeed(decorationSeed);
        return decorationSeed;
    }

    /** 바닐라 WorldgenRandom.setFeatureSeed — featureIndex is the step-local sorter index. */
    public void setFeatureSeed(long decorationSeed, int featureIndex, int stepOrdinal) {
        setSeed(decorationSeed + featureIndex + 10000L * stepOrdinal);
    }
}
