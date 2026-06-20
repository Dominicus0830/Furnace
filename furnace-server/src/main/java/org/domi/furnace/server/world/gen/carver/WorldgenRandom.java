// 바닐라 WorldgenRandom 포팅 — carver origin chunk별 시드 초기화
package org.domi.furnace.server.world.gen.carver;

public final class WorldgenRandom extends LegacyRandom {

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
}
