// 청크 섹션: 블록 16³ + 바이옴 4³ + 비공기 수. 인덱스는 바닐라 YZX 순.
package org.domi.furnace.server.world;

public final class ChunkSection {

    public static final int AIR = 0;

    private final int[] blocks = new int[4096]; // 전부 0(air)
    private final int[] biomes = new int[64];
    private short nonAirCount = 0;

    private static int blockIndex(int x, int y, int z) {
        return y * 256 + z * 16 + x;
    }

    private static int biomeIndex(int x, int y, int z) {
        return y * 16 + z * 4 + x;
    }

    public void setBlock(int x, int y, int z, int stateId) {
        int idx = blockIndex(x, y, z);
        int old = blocks[idx];
        if (old == AIR && stateId != AIR) nonAirCount++;
        else if (old != AIR && stateId == AIR) nonAirCount--;
        blocks[idx] = stateId;
    }

    public int getBlock(int x, int y, int z) {
        return blocks[blockIndex(x, y, z)];
    }

    public void fillBiome(int biomeId) {
        java.util.Arrays.fill(biomes, biomeId);
    }

    public int getBiome(int x, int y, int z) {
        return biomes[biomeIndex(x, y, z)];
    }

    public void setBiome(int x, int y, int z, int biomeId) {
        biomes[biomeIndex(x, y, z)] = biomeId;
    }

    public short nonAirCount() {
        return nonAirCount;
    }

    public int[] blocks() {
        return blocks;
    }

    public int[] biomes() {
        return biomes;
    }
}
