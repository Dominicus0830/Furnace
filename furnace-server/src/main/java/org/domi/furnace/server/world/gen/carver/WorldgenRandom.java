// 바닐라 WorldgenRandom 포팅 — carver origin chunk별 시드 초기화
package org.domi.furnace.server.world.gen.carver;

public final class WorldgenRandom extends LegacyRandom {

    public WorldgenRandom() {
        super(0L);
    }

    /** 바닐라 WorldgenRandom.setLargeFeatureSeed(worldSeed, chunkX, chunkZ) */
    public void setLargeFeatureSeed(long worldSeed, int x, int z) {
        setSeed(worldSeed);
        long a = nextLong() | 1L;
        long b = nextLong() | 1L;
        setSeed((long)x * a + (long)z * b ^ worldSeed);
    }
}
