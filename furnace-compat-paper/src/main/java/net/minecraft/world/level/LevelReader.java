package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/** 월드 읽기(Mojang 이름, N2a). 바닐라 기본 메서드 중 청크·높이·바이옴(T-211 3단계)·기능 플래그 쪽만. */
public interface LevelReader extends BlockGetter, net.minecraft.world.level.biome.BiomeManager.NoiseBiomeSource {

    ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create);

    boolean hasChunk(int x, int z);

    int getHeight(Heightmap.Types type, int x, int z);

    /** 바닐라처럼 읽기 쪽에 둔다 — FAWE 는 {@code LevelAccessor#registryAccess()} 로 부른다(T-203 4단계). */
    net.minecraft.core.RegistryAccess registryAccess();

    default ChunkAccess getChunk(int x, int z) { return getChunk(x, z, ChunkStatus.FULL, true); }

    /** Paper LevelReader#getChunkIfLoadedImmediately: 로드된 다 만든 청크, 아니면 null(T-211 3단계). */
    ChunkAccess getChunkIfLoadedImmediately(int x, int z);

    default ChunkAccess getChunkIfLoadedImmediately(BlockPos pos) { return getChunkIfLoadedImmediately(pos.getX() >> 4, pos.getZ() >> 4); }

    /** 바닐라 LevelReader#getBiomeManager(T-211 3단계). */
    net.minecraft.world.level.biome.BiomeManager getBiomeManager();

    /** 바닐라 LevelReader#getBiome: 확대 지터를 거친 그 칸의 바이옴. */
    default net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getBiome(BlockPos pos) { return getBiomeManager().getBiome(pos); }

    /** 바닐라 LevelReader#getNoiseBiome: BIOMES 이상인 청크가 있으면 그 청크의 바이옴, 없으면 생성기 값. */
    @Override
    default net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ) {
        ChunkAccess c = getChunk(net.minecraft.core.QuartPos.toSection(biomeX), net.minecraft.core.QuartPos.toSection(biomeZ), ChunkStatus.BIOMES, false);
        return c != null ? c.getNoiseBiome(biomeX, biomeY, biomeZ) : getUncachedNoiseBiome(biomeX, biomeY, biomeZ);
    }

    /** 바닐라 LevelReader#getUncachedNoiseBiome: 생성기가 그 칸에 정하는 바이옴. */
    net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int biomeX, int biomeY, int biomeZ);

    /** 바닐라 LevelReader#enabledFeatures(T-211 3단계). */
    net.minecraft.world.flag.FeatureFlagSet enabledFeatures();

    default ChunkAccess getChunk(int x, int z, ChunkStatus status) { return getChunk(x, z, status, true); }

    default ChunkAccess getChunk(BlockPos pos) { return getChunk(pos.getX() >> 4, pos.getZ() >> 4); }

    default boolean hasChunkAt(int x, int z) { return hasChunk(x >> 4, z >> 4); }

    default boolean hasChunkAt(BlockPos pos) { return hasChunkAt(pos.getX(), pos.getZ()); }

    default boolean isEmptyBlock(BlockPos pos) { return getBlockState(pos).isAir(); }

    default BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) {
        return new BlockPos(pos.getX(), getHeight(type, pos.getX(), pos.getZ()), pos.getZ());
    }
}
