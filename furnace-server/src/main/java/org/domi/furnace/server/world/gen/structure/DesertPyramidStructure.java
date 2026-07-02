// 사막 피라미드 StructureStart 생성 스켈레톤 — 바닐라 SinglePieceStructure.findGenerationPoint 이식.
// 블록 배치는 하지 않는다(S2b) — 여기서는 placement 판정 + 스텁 피스(BoundingBox·회전)까지만.
package org.domi.furnace.server.world.gen.structure;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class DesertPyramidStructure {

    public static final String ID = "minecraft:desert_pyramid";

    /**
     * vanilla data/minecraft/worldgen/structure/desert_pyramid.json 의 "biomes" 태그
     * (#minecraft:has_structure/desert_pyramid) 실제 원소 — desert 단일. (가정, 원본 태그 리소스 미확보 — S2c 검증 필요)
     */
    private static final Set<String> VALID_BIOMES = Set.of("minecraft:desert");

    private DesertPyramidStructure() {}

    /** WORLD_SURFACE_WG 류 높이 조회 — (blockX,blockZ) → 최상단 비-공기 블록의 y(=vanilla getFirstOccupiedHeight). */
    @FunctionalInterface
    public interface HeightSampler {
        int worldSurfaceHeight(int blockX, int blockZ);
    }

    /** (blockX,blockY,blockZ) 위치의 바이옴 레지스트리 이름 조회(quart 해상도). */
    @FunctionalInterface
    public interface BiomeSampler {
        String biomeAt(int blockX, int blockY, int blockZ);
    }

    /**
     * 바닐라 SinglePieceStructure.findGenerationPoint(width=depth=21) + isValidBiome 이식.
     * 유효하면 스텁 피스 1개를 가진 StructureStart 를 반환한다.
     */
    public static Optional<StructureStart> generate(
            long seed, int chunkX, int chunkZ, int seaLevel, HeightSampler heights, BiomeSampler biomes) {
        int minBlockX = chunkX * 16;
        int minBlockZ = chunkZ * 16;
        int width = DesertPyramidPiece.WIDTH;
        int depth = DesertPyramidPiece.DEPTH;

        // Structure.getLowestY(context, width, depth) — 4개 코너의 WORLD_SURFACE_WG 최소값.
        int lowestY = Math.min(
            Math.min(heights.worldSurfaceHeight(minBlockX, minBlockZ), heights.worldSurfaceHeight(minBlockX, minBlockZ + depth)),
            Math.min(heights.worldSurfaceHeight(minBlockX + width, minBlockZ), heights.worldSurfaceHeight(minBlockX + width, minBlockZ + depth))
        );
        if (lowestY < seaLevel) {
            return Optional.empty();
        }

        // Structure.onTopOfChunkCenter — 청크 중심점의 높이.
        int middleBlockX = minBlockX + 8;
        int middleBlockZ = minBlockZ + 8;
        int firstOccupiedHeight = heights.worldSurfaceHeight(middleBlockX, middleBlockZ);

        // Structure.isValidBiome — 중심점 biome 필터.
        String biome = biomes.biomeAt(middleBlockX, firstOccupiedHeight, middleBlockZ);
        if (!VALID_BIOMES.contains(biome)) {
            return Optional.empty();
        }

        // Structure.GenerationContext.makeRandom — setLargeFeatureSeed(seed, chunkX, chunkZ).
        WorldgenRandom random = new WorldgenRandom();
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);

        // SinglePieceStructure.generatePieces — constructor.construct(random, minBlockX, minBlockZ).
        DesertPyramidPiece piece = new DesertPyramidPiece(random, minBlockX, minBlockZ);

        return Optional.of(new StructureStart(ID, chunkX, chunkZ, List.of(piece)));
    }
}
