package org.bukkit.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

/**
 * Paper 1.21.1 ChunkGenerator(플러그인 월드 생성기, T-217). {@code WorldCreator#generator} 로 createWorld 에 넘기면 Paper CustomChunkGenerator 와 같은
 * 순서·난수 씨앗으로 불린다: 노이즈(바닐라 → generateNoise) → 표면(바닐라 → generateSurface → generateBedrock → 옛 generateChunkData) →
 * 카버(바닐라 → generateCaves) → 구조물 → 장식(바닐라 → BlockPopulator) → 처음 동물. 바닐라 단계는 shouldGenerate* 가 참일 때만 돈다.
 * generate*·shouldGenerate*·getBaseHeight 는 생성 스레드(여럿이 동시에)에서 불린다. 기본값은 Paper 와 같다.
 */
public abstract class ChunkGenerator {

    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {}

    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {}

    public void generateBedrock(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {}

    public void generateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {}

    /** 이 생성기의 기본 바이옴 공급자(WorldCreator 에 공급자가 없을 때 쓴다). null 이면 바닐라 바이옴. */
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) { return null; }

    /** 구조물 배치용 높이(첫 빈 칸의 y). 기본은 UnsupportedOperationException — 그러면 다시 묻지 않고 바닐라 높이를 쓴다. */
    public int getBaseHeight(WorldInfo worldInfo, Random random, int x, int z, HeightMap heightMap) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /** 옛 API 의 바이옴 격자. */
    @Deprecated
    public interface BiomeGrid {
        Biome getBiome(int x, int z);

        Biome getBiome(int x, int y, int z);

        void setBiome(int x, int z, Biome bio);

        void setBiome(int x, int y, int z, Biome bio);
    }

    /**
     * 옛 API: 표면 단계 끝에 불리고, 돌려준 데이터에서 손댄 섹션(16칸 높이)이 청크의 그 섹션을 통째로 갈아 끼운다. 데이터는 {@link #createChunkData} 로 만든다.
     * 기본은 UnsupportedOperationException — 처음 그렇게 되면 이 생성기에는 다시 부르지 않는다. {@link #isParallelCapable()} 이 거짓이면 한 번에 하나씩 부른다.
     */
    @Deprecated
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        throw new UnsupportedOperationException("Not implemented, no longer needed");
    }

    @Deprecated
    protected final ChunkData createChunkData(World world) { return Bukkit.getServer().createChunkData(world); }

    /** Paper 1.21.1 서버는 부르지 않는다. 기본 본문은 월드 높이 조회가 생기면 Paper 처럼(T-218) — 지금은 참. */
    public boolean canSpawn(World world, int x, int z) { return true; }

    /** 월드가 올라올 때(새 월드든 이미 있는 월드든) 그 월드의 BlockPopulator 목록에 더해진다. */
    public List<BlockPopulator> getDefaultPopulators(World world) { return new ArrayList<BlockPopulator>(); }

    /** 새 월드의 스폰(그 월드의 위치여야 한다). null 이면 바닐라 스폰 찾기. 난수는 {@code new Random(시드)}. */
    public Location getFixedSpawnLocation(World world, Random random) { return null; }

    /** 참이면 옛 generateChunkData 를 여러 스레드에서 동시에 부른다. */
    public boolean isParallelCapable() { return false; }

    public boolean shouldGenerateNoise() { return false; }

    public boolean shouldGenerateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return shouldGenerateNoise(); }

    public boolean shouldGenerateSurface() { return false; }

    public boolean shouldGenerateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return shouldGenerateSurface(); }

    /** Paper 1.21.1 에선 아무 효과가 없다(바닐라 기반암은 표면 단계 몫). */
    @Deprecated
    public boolean shouldGenerateBedrock() { return false; }

    public boolean shouldGenerateCaves() { return false; }

    public boolean shouldGenerateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return shouldGenerateCaves(); }

    public boolean shouldGenerateDecorations() { return false; }

    public boolean shouldGenerateDecorations(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return shouldGenerateDecorations(); }

    public boolean shouldGenerateMobs() { return false; }

    public boolean shouldGenerateMobs(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return shouldGenerateMobs(); }

    public boolean shouldGenerateStructures() { return false; }

    public boolean shouldGenerateStructures(WorldInfo worldInfo, Random random, int chunkX, int chunkZ) { return shouldGenerateStructures(); }

    /**
     * 청크 하나의 블록(x·z 는 0..15, y 는 [getMinHeight, getMaxHeight)). 범위 밖 쓰기는 무시, 읽기는 공기. MaterialData 판 메서드는 이 호환 계층에 없다.
     */
    public static interface ChunkData {
        int getMinHeight();

        int getMaxHeight();

        Biome getBiome(int x, int y, int z);

        void setBlock(int x, int y, int z, Material material);

        void setBlock(int x, int y, int z, BlockData blockData);

        /** 상자 꼴(끝 좌표는 제외)로 채운다. */
        void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material);

        void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData);

        Material getType(int x, int y, int z);

        BlockData getBlockData(int x, int y, int z);

        /** 옛 데이터 값 — 이 호환 계층에는 옛 데이터 값 표가 없다. */
        @Deprecated
        default byte getData(int x, int y, int z) {
            throw new org.domi.furnace.compat.paper.UnsupportedFeature("ChunkData#getData", "legacy data values are not available in this compatibility layer");
        }
    }
}
