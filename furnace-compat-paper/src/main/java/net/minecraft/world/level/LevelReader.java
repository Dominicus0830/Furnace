package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/** 월드 읽기(Mojang 이름, N2a). 바닐라 기본 메서드 중 청크·높이 쪽만. */
public interface LevelReader extends BlockGetter {

    ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create);

    boolean hasChunk(int x, int z);

    int getHeight(Heightmap.Types type, int x, int z);

    /** 바닐라처럼 읽기 쪽에 둔다 — FAWE 는 {@code LevelAccessor#registryAccess()} 로 부른다(T-203 4단계). */
    net.minecraft.core.RegistryAccess registryAccess();

    /** 바닐라 LevelReader#dimensionType. 서버 레벨은 그 월드의 차원 타입. */
    default net.minecraft.world.level.dimension.DimensionType dimensionType() {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LevelReader#dimensionType", "the level (" + getClass().getName() + ") has no dimension type in the shim");
    }

    /** 바닐라 LevelReader#getBiomeManager. */
    default net.minecraft.world.level.biome.BiomeManager getBiomeManager() {
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LevelReader#getBiomeManager", "the level (" + getClass().getName() + ") has no biome manager in the shim");
    }

    /** 바닐라 LevelReader#getBiome: {@code getBiomeManager().getBiome(pos)}. */
    default net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getBiome(BlockPos pos) { return getBiomeManager().getBiome(pos); }

    default ChunkAccess getChunk(int x, int z) { return getChunk(x, z, ChunkStatus.FULL, true); }

    default ChunkAccess getChunk(int x, int z, ChunkStatus status) { return getChunk(x, z, status, true); }

    default ChunkAccess getChunk(BlockPos pos) { return getChunk(pos.getX() >> 4, pos.getZ() >> 4); }

    default boolean hasChunkAt(int x, int z) { return hasChunk(x >> 4, z >> 4); }

    default boolean hasChunkAt(BlockPos pos) { return hasChunkAt(pos.getX(), pos.getZ()); }

    default boolean isEmptyBlock(BlockPos pos) { return getBlockState(pos).isAir(); }

    default BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) {
        return new BlockPos(pos.getX(), getHeight(type, pos.getX(), pos.getZ()), pos.getZ());
    }
}
