package org.bukkit.craftbukkit.util;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.ticks.TickPriority;

/**
 * CraftBukkit DummyGeneratorAccess(T-211) — Paper 1.21.1 그대로: 아무 월드도 아닌 지형 생성 레벨. 블록은 늘 공기, 쓰기는 거짓, 예약 틱·레벨 이벤트는 버리고
 * 나머지는 UnsupportedOperationException("Not supported yet."). shim 에 없는 형(틱 접근·파티클·엔티티 찾기 등)을 쓰는 메서드는 두지 않았다.
 */
public class DummyGeneratorAccess implements WorldGenLevel {

    public static final WorldGenLevel INSTANCE = new DummyGeneratorAccess();

    protected DummyGeneratorAccess() {}

    private static UnsupportedOperationException no() { return new UnsupportedOperationException("Not supported yet."); }

    @Override
    public long getSeed() { throw no(); }

    @Override
    public ServerLevel getLevel() { throw no(); }

    public MinecraftServer getServer() { throw no(); }

    @Override
    public ChunkSource getChunkSource() { throw no(); }

    @Override
    public RandomSource getRandom() { throw no(); }

    public void scheduleTick(BlockPos pos, Block block, int delay) {}

    public void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority) {}

    public void scheduleTick(BlockPos pos, Fluid fluid, int delay) {}

    public void scheduleTick(BlockPos pos, Fluid fluid, int delay, TickPriority priority) {}

    public void playSound(Player source, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) { throw no(); }

    public void levelEvent(Player player, int eventId, BlockPos pos, int data) {}

    public List<? extends Player> players() { throw no(); }

    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) { throw no(); }

    /** 바닐라 기본 hasChunk 는 getChunkSource() 로 가므로 여기서도 지원하지 않는다. */
    @Override
    public boolean hasChunk(int x, int z) { throw no(); }

    @Override
    public int getHeight(Heightmap.Types heightmap, int x, int z) { throw no(); }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int biomeX, int biomeY, int biomeZ) { throw no(); }

    @Override
    public net.minecraft.world.level.biome.BiomeManager getBiomeManager() { throw no(); }

    @Override
    public net.minecraft.world.DifficultyInstance getCurrentDifficultyAt(BlockPos pos) { throw no(); }

    @Override
    public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { throw no(); }

    @Override
    public ChunkAccess getChunkIfLoadedImmediately(int x, int z) { throw no(); }

    public boolean isClientSide() { return false; }

    public int getSeaLevel() { throw no(); }

    @Override
    public DimensionType dimensionType() { throw no(); }

    /** Paper: getLevelData 가 던진다(shim 은 레벨 데이터 대신 dayTime 을 직접). */
    @Override
    public long dayTime() { throw no(); }

    /** 바닐라 높이 질의는 dimensionType() 으로 가므로 여기서도 지원하지 않는다. */
    @Override
    public int getHeight() { throw no(); }

    @Override
    public int getMinBuildHeight() { throw no(); }

    @Override
    public RegistryAccess registryAccess() { throw no(); }

    public float getShade(Direction direction, boolean shaded) { throw no(); }

    public LevelLightEngine getLightEngine() { throw no(); }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) { throw no(); }

    @Override
    public BlockState getBlockState(BlockPos pos) { return Blocks.AIR.defaultBlockState(); }

    @Override
    public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }

    @Override
    public BlockState getBlockStateIfLoaded(BlockPos pos) { throw no(); }

    @Override
    public FluidState getFluidIfLoaded(BlockPos pos) { throw no(); }

    public WorldBorder getWorldBorder() { throw no(); }

    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state) { throw no(); }

    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> state) { throw no(); }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) { return false; }

    @Override
    public boolean removeBlock(BlockPos pos, boolean move) { throw no(); }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean drop, Entity breakingEntity, int maxUpdateDepth) { return false; }
}
