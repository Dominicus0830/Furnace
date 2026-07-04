package org.domi.oracle;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.WorldGenTickAccess;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

final class OracleWorldGenLevel implements WorldGenLevel {
    private final ProtoChunk[] grid;
    private final int cx;
    private final int cz;
    private final int radius;
    // featurespill: 하나의 level 인스턴스로 9개 origin을 순차 decorate할 때, 쓰기 클램프(writeRadius=1)와
    // getRandom() fork 지점은 매번 "지금 decorate 중인 origin" 기준이어야 한다(vanilla WorldGenRegion은
    // origin마다 새로 생성되므로). cx/cz(grid 인덱싱 기준, 항상 고정)와 분리해 setDecorationOrigin으로 전환.
    private int originX;
    private int originZ;
    private final long seed;
    private final MultiNoiseBiomeSource biomeSource;
    private final RandomState randomState;
    private final RegistryAccess registryAccess;
    private final DimensionType dimensionType;
    private final BiomeManager biomeManager;
    private final LevelTickAccess<Block> blockTicks;
    private final LevelTickAccess<Fluid> fluidTicks;
    private final WorldBorder worldBorder = new WorldBorder();

    OracleWorldGenLevel(
        ProtoChunk[] grid,
        int cx,
        int cz,
        long seed,
        MultiNoiseBiomeSource biomeSource,
        RandomState randomState,
        RegistryAccess registryAccess,
        HolderLookup.Provider registries
    ) {
        this(grid, cx, cz, 1, seed, biomeSource, randomState, registryAccess, registries);
    }

    // radius > 1 지원 — M4g-e featurestruct: createReferences가 center±8 청크를 조회해야 함
    OracleWorldGenLevel(
        ProtoChunk[] grid,
        int cx,
        int cz,
        int radius,
        long seed,
        MultiNoiseBiomeSource biomeSource,
        RandomState randomState,
        RegistryAccess registryAccess,
        HolderLookup.Provider registries
    ) {
        this.grid = grid;
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
        this.originX = cx;
        this.originZ = cz;
        this.seed = seed;
        this.biomeSource = biomeSource;
        this.randomState = randomState;
        this.registryAccess = registryAccess;
        this.dimensionType = registries.lookupOrThrow(Registries.DIMENSION_TYPE)
            .getOrThrow(BuiltinDimensionTypes.OVERWORLD).value();
        this.biomeManager = new BiomeManager(
            (qx, qy, qz) -> biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler()),
            BiomeManager.obfuscateSeed(seed)
        );
        this.blockTicks = new WorldGenTickAccess<>(pos -> getChunk(pos).getBlockTicks());
        this.fluidTicks = new WorldGenTickAccess<>(pos -> getChunk(pos).getFluidTicks());
    }

    @Override public long getSeed() { return seed; }
    @Override public boolean isClientSide() { return false; }
    @Override public int getSeaLevel() { return 63; }
    @Override public int getHeight() { return FixtureFormat.HEIGHT; }
    @Override public int getMinBuildHeight() { return FixtureFormat.MIN_Y; }
    @Override public RegistryAccess registryAccess() { return registryAccess; }
    @Override public DimensionType dimensionType() { return dimensionType; }
    @Override public BiomeManager getBiomeManager() { return biomeManager; }
    @Override public Holder<Biome> getUncachedNoiseBiome(int qx, int qy, int qz) {
        return biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler());
    }
    @Override public ServerLevel getLevel() { return null; }
    @Override public ServerLevel getMinecraftWorld() { return null; }
    @Override public long nextSubTickCount() { return 0L; }
    @Override public LevelTickAccess<Block> getBlockTicks() { return blockTicks; }
    @Override public LevelTickAccess<Fluid> getFluidTicks() { return fluidTicks; }
    @Override public FeatureFlagSet enabledFeatures() { return FeatureFlags.DEFAULT_FLAGS; }

    @Override public BlockState getBlockState(BlockPos pos) {
        ProtoChunk chunk = getChunkAt(chunkX(pos), chunkZ(pos));
        return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
    }

    @Override public BlockState getBlockStateIfLoaded(BlockPos pos) {
        ProtoChunk chunk = getChunkAt(chunkX(pos), chunkZ(pos));
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override public FluidState getFluidIfLoaded(BlockPos pos) {
        BlockState state = getBlockStateIfLoaded(pos);
        return state == null ? null : state.getFluidState();
    }

    @Override public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        // vanilla WorldGenRegion: FEATURES writeRadius=1 → 3×3 이웃 쓰기 허용 (구조물 postProcess는 자기 청크로 이미 클램프됨).
        // center만 허용하면 이웃에 걸친 배치(tall_seagrass 등)가 드롭돼 이후 world-state가 바닐라와 어긋남.
        // 이 writeRadius=1 제약은 grid 조회 radius(구조물 references 탐색용)와 별개 — 항상 1로 고정.
        // origin(현재 decorate 중인 청크) 기준 — featurespill처럼 하나의 level로 여러 origin을 순차
        // decorate할 때도 각 origin 자신의 3×3만 쓰기 허용(vanilla가 origin마다 WorldGenRegion을 새로 만드는 것과 동일 효과).
        int dx = chunkX(pos) - originX;
        int dz = chunkZ(pos) - originZ;
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) {
            return false;
        }
        return getChunkAt(chunkX(pos), chunkZ(pos)).setBlockState(pos, state, false) != null;
    }

    @Override public boolean removeBlock(BlockPos pos, boolean move) {
        return setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override public boolean destroyBlock(BlockPos pos, boolean drop, Entity breakingEntity, int maxUpdateDepth) {
        return removeBlock(pos, false);
    }

    @Override public boolean ensureCanWrite(BlockPos pos) {
        return Math.abs(chunkX(pos) - originX) <= 1 && Math.abs(chunkZ(pos) - originZ) <= 1;
    }

    // featurespill: 다음 origin을 decorate하기 전에 호출 — 쓰기 클램프와 getRandom() fork 지점을 전환한다.
    void setDecorationOrigin(int ox, int oz) {
        this.originX = ox;
        this.originZ = oz;
        this.regionRandom = null;
    }

    @Override public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
        ProtoChunk chunk = getChunkAt(chunkX, chunkZ);
        if (chunk == null) {
            throw new IllegalStateException("Oracle chunk outside grid (radius=" + radius + "): " + chunkX + "," + chunkZ);
        }
        return chunk;
    }

    @Override public ChunkAccess getChunkIfLoadedImmediately(int x, int z) {
        return getChunkAt(x, z);
    }

    @Override public boolean hasChunk(int chunkX, int chunkZ) {
        return getChunkAt(chunkX, chunkZ) != null;
    }

    @Override public int getHeight(Heightmap.Types type, int x, int z) {
        ProtoChunk chunk = getChunkAt(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        if (chunk == null) {
            return getMinBuildHeight();
        }
        return chunk.getHeight(type, x & 15, z & 15) + 1;
    }

    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        ProtoChunk chunk = getChunkAt(chunkX(pos), chunkZ(pos));
        BlockEntity be = chunk == null ? null : chunk.getBlockEntity(pos);
        // NoBukkitEvent 미러: Paper의 setLootTable이 CraftBlockStates(CraftChest) 생성을 시도해
        // 헤드리스에선 "The blockState must be placed" IllegalStateException으로 죽는다.
        // vanilla 의미론은 필드 저장뿐이고 fixture는 블록만 기록하므로 setLootTable을 no-op으로 차단.
        // RNG parity: createChest의 random.nextLong()은 setLootTable 호출 전에 인자 평가로 이미 소비됨.
        if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            return new net.minecraft.world.level.block.entity.ChestBlockEntity(pos, chest.getBlockState()) {
                @Override public void setLootTable(ResourceKey<net.minecraft.world.level.storage.loot.LootTable> lootTable, long seed) {
                    // no-op — Paper Bukkit hook 우회
                }
            };
        }
        return be;
    }

    @Override public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> state) {
        return state.test(getBlockState(pos));
    }

    @Override public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> state) {
        return state.test(getFluidState(pos));
    }

    @Override public int getSkyDarken() { return 0; }
    @Override public float getShade(Direction direction, boolean shaded) { return 1.0F; }
    @Override public LevelLightEngine getLightEngine() { throw new UnsupportedOperationException("No light engine in oracle"); }
    @Override public int getBrightness(net.minecraft.world.level.LightLayer lightLayer, BlockPos blockPos) { return 15; }
    @Override public int getRawBrightness(BlockPos pos, int amount) { return 15; }
    @Override public boolean canSeeSky(BlockPos pos) { return true; }
    @Override public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return colorResolver.getColor(getBiome(pos).value(), pos.getX(), pos.getZ());
    }
    @Override public WorldBorder getWorldBorder() { return worldBorder; }
    @Override public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        return getChunkAt(chunkX, chunkZ);
    }
    @Override public List<VoxelShape> getEntityCollisions(Entity entity, AABB box) {
        return Collections.emptyList();
    }
    @Override public List<Entity> getEntities(Entity except, AABB box, Predicate<? super Entity> predicate) {
        return Collections.emptyList();
    }
    @Override public <T extends Entity> List<T> getEntities(net.minecraft.world.level.entity.EntityTypeTest<Entity, T> filter, AABB box, Predicate<? super T> predicate) {
        return Collections.emptyList();
    }
    @Override public List<? extends Player> players() { return Collections.emptyList(); }
    @Override public LevelData getLevelData() {
        return new LevelData() {
            @Override public BlockPos getSpawnPos() { return BlockPos.ZERO; }
            @Override public float getSpawnAngle() { return 0.0F; }
            @Override public long getGameTime() { return 0L; }
            @Override public long getDayTime() { return 0L; }
            @Override public boolean isThundering() { return false; }
            @Override public boolean isRaining() { return false; }
            @Override public void setRaining(boolean raining) {}
            @Override public boolean isHardcore() { return false; }
            @Override public GameRules getGameRules() { return new GameRules(); }
            @Override public Difficulty getDifficulty() { return Difficulty.NORMAL; }
            @Override public boolean isDifficultyLocked() { return false; }
        };
    }
    @Override public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return new DifficultyInstance(Difficulty.NORMAL, 0L, 0L, 0.0F);
    }
    @Override public MinecraftServer getServer() { return null; }
    @Override public ChunkSource getChunkSource() { throw new UnsupportedOperationException("No chunk source in oracle"); }
    // vanilla WorldGenRegion.getRandom() 미러 — randomState 기반 positional random을 center 청크
    // 원점에 fork한 인스턴스 하나를 유지(호출마다 새 인스턴스 금지 — 순차 소비가 결정적 시퀀스).
    private net.minecraft.util.RandomSource regionRandom;
    @Override public net.minecraft.util.RandomSource getRandom() {
        if (regionRandom == null) {
            regionRandom = randomState.getOrCreateRandomFactory(
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("worldgen_region_random"))
                .at(new ChunkPos(originX, originZ).getWorldPosition());
        }
        return regionRandom;
    }
    @Override public void playSound(Player source, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch) {}
    @Override public void addParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {}
    @Override public void levelEvent(Player player, int eventId, BlockPos pos, int data) {}
    @Override public void gameEvent(Holder<GameEvent> event, Vec3 emitterPos, GameEvent.Context emitter) {}

    @Override public ChunkAccess getChunk(BlockPos pos) {
        return getChunk(chunkX(pos), chunkZ(pos));
    }

    private static int chunkX(BlockPos pos) {
        return Math.floorDiv(pos.getX(), 16);
    }

    private static int chunkZ(BlockPos pos) {
        return Math.floorDiv(pos.getZ(), 16);
    }

    private ProtoChunk getChunkAt(int chunkX, int chunkZ) {
        int dx = chunkX - cx;
        int dz = chunkZ - cz;
        if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
            return null;
        }
        int size = 2 * radius + 1;
        return grid[(dz + radius) * size + (dx + radius)];
    }
}
