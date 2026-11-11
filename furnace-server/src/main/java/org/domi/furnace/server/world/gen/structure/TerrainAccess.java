// 구조물 시작점 생성에 필요한 지형 질의 — 바닐라 ChunkGenerator.getFirstOccupiedHeight / BiomeSource.getNoiseBiome 상당.
package org.domi.furnace.server.world.gen.structure;

public interface TerrainAccess {
    int HM_WORLD_SURFACE_WG = 0;
    int HM_OCEAN_FLOOR_WG = 1;

    int seaLevel();

    int minY();

    int genDepth();

    /** getFirstOccupiedHeight(x, z, type): 최상단 점유 블록의 y (= getFirstFreeHeight - 1). */
    int firstOccupiedHeight(int heightmap, int blockX, int blockZ);

    /** getFirstFreeHeight (= 첫 빈 칸 y). */
    default int firstFreeHeight(int heightmap, int blockX, int blockZ) {
        return firstOccupiedHeight(heightmap, blockX, blockZ) + 1;
    }

    /** NoiseColumn 상당: 카버/피처 이전 지형 채움의 블록 상태 id. */
    int baseBlock(int blockX, int blockY, int blockZ);

    /** Biome.coldEnoughToSnow(pos) */
    boolean coldEnoughToSnow(String biome, int x, int y, int z);

    /** 블록 좌표의 노이즈 바이옴(쿼트 해상도) 이름. */
    String biomeAt(int blockX, int blockY, int blockZ);

    /** LevelHeightAccessor.getMinBuildHeight — 차원 높이(생성기 minY 와 다를 수 있다: 평지 0 대 -64, 네더 노이즈 128 대 차원 256). */
    default int levelMinY() { return minY(); }

    /** LevelHeightAccessor.getMaxBuildHeight(배타). */
    default int levelMaxY() { return minY() + genDepth(); }

    /** 바이옴 원천이 FixedBiomeSource 면 그 바이옴(아니면 null) — findBiomeHorizontal 이 다른 식을 쓴다. */
    default String fixedBiome() { return null; }
}
