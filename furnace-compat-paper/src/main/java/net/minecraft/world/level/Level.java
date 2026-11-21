package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.nms.NmsChunks;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 월드 shim(Mojang 이름). 실제 객체는 늘 {@link net.minecraft.server.level.ServerLevel} 이다.
 * <p>N1/N2a: 블록 읽기·쓰기와 청크 얻기. 읽기·쓰기는 그 칸의 청크를 가진 region 스레드(이벤트 처리기·entityScheduler·전역 빌림 구간)에서만 되고
 * 아니면 ThreadOwnershipException 이다(Paper 는 주 스레드 하나). Paper 와 다른 점: 로드돼 있지 않은 청크를 동기로 불러오지 않는다 —
 * 그런 칸을 읽거나 쓰면 IllegalStateException(로드 여부는 {@link #hasChunk}·{@link #getBlockStateIfLoaded} 로 본다).
 */
public abstract class Level implements LevelAccessor {

    public static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    public static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
    public static final ResourceKey<Level> END = ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_end"));

    private final CraftWorld world;

    /** 바닐라 Level#isClientSide 필드(FAWE removeBeacon 이 필드로 읽는다). 서버 월드라 늘 거짓이다. */
    public final boolean isClientSide;

    /** Paper 엑스레이 막기 조절기 필드(Axiom 이 읽는다). Furnace 에는 엑스레이 막기가 없어 늘 "아무것도 안 함" 조절기다. */
    public final com.destroystokyo.paper.antixray.ChunkPacketBlockController chunkPacketBlockController =
            com.destroystokyo.paper.antixray.ChunkPacketBlockController.NO_OPERATION_INSTANCE;

    protected Level(CraftWorld world) {
        this.world = java.util.Objects.requireNonNull(world, "world");
        this.isClientSide = false;
    }

    /** CraftBukkit 이 더한 메서드: 이 월드의 Bukkit 객체. */
    public CraftWorld getWorld() { return world; }

    private org.domi.furnace.api.world.World furnace() { return world.furnaceHandle(); }

    // ------------------------------------------------------------------ 쓰기 기록(T-211 — Paper 필드 그대로)
    // Paper 의 나무·구조물 생성 기록: captureTreeGeneration 이 켜져 있으면 setBlock 은 월드에 쓰지 않고 칸마다 CapturedBlockState 에 마지막 쓰기를 싣고
    // (부술 수 없는 블록 칸은 거짓), getBlockState·getBlockStateIfLoaded 는 기록을 먼저 본다. 표시는 월드마다 하나인 평범한 필드라 누가 켰는지 알 수 없다 —
    // Furnace 는 region 스레드가 여럿이므로 전역 스레드(Bukkit 동기 작업 — Paper 의 주 스레드 자리)의 호출에만 적용한다(NmsFeatures#captureAppliesHere —
    // region 스레드의 NMS 쓰기·지형지물 배치는 전역 스레드가 켠 기록에 섞이지 않는다). 기록 표는 동기화된 표다(다른 스레드가 읽을 수 있다).

    public boolean captureBlockStates = false;
    public boolean captureTreeGeneration = false;
    public java.util.Map<BlockPos, org.bukkit.craftbukkit.block.CraftBlockState> capturedBlockStates =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());
    public java.util.Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> capturedTileEntities =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());

    /** 이 호출에 나무 기록을 적용하는가. */
    private boolean treeCapture() { return captureTreeGeneration && org.domi.furnace.compat.nms.NmsFeatures.captureAppliesHere(); }

    /** 기록된 마지막 쓰기(나무 기록 중일 때만), 없으면 null. */
    private BlockState capturedState(BlockPos pos) {
        if (!treeCapture()) return null;
        org.bukkit.craftbukkit.block.CraftBlockState previous = capturedBlockStates.get(pos);
        return previous == null ? null : previous.getHandle();
    }

    /** 바닐라 Level#random: 월드마다 하나인 난수원(씨앗 없는 {@code RandomSource.create()} — 바닐라 1.21.1 생성자와 같다, 2026-11-17 점검에서 {@code javap -c} 로 확인). */
    public final net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();

    /** 바닐라 Level#getRandom. */
    public net.minecraft.util.RandomSource getRandom() { return random; }

    /** 바닐라 1.21.1 Level#getSeaLevel — 상수 63(차원마다 다르지 않다. 서버 jar 로 확인). */
    public int getSeaLevel() { return 63; }

    private volatile net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> dimensionType;

    /** 바닐라 dimensionTypeRegistration: 이 월드의 차원 타입(같은 레벨은 같은 손잡이). */
    public net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> dimensionTypeRegistration() {
        var h = dimensionType;
        if (h == null) dimensionType = h = net.minecraft.core.Holder.direct(new net.minecraft.world.level.dimension.DimensionType(furnace()));
        return h;
    }

    /** 바닐라 Level#dimensionType: {@code dimensionTypeRegistration().value()}. */
    public net.minecraft.world.level.dimension.DimensionType dimensionType() { return dimensionTypeRegistration().value(); }

    /**
     * 바닐라 Level#getUncachedNoiseBiome(quart 좌표): 월드 생성기가 그 칸에 정하는 바이옴(청크에 저장된 값이 아니다). 아무 스레드.
     * 바이옴 원천이 없는 생성기는 UnsupportedNmsCall.
     */
    public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
        int id;
        try {
            id = org.domi.furnace.api.Furnace.server().internals().chunks().noiseBiome(furnace(), quartX, quartY, quartZ);
        } catch (UnsupportedOperationException e) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("Level#getUncachedNoiseBiome", e.getMessage());
        }
        return org.domi.furnace.compat.nms.NmsChunks.biomeHolders().get(id);
    }

    // ------------------------------------------------------------------ 레벨 공통(T-211 3단계 — 설계 9장)

    private volatile net.minecraft.world.level.biome.BiomeManager biomeManager;

    /** 바닐라 Level#getBiomeManager: 이 레벨을 원천으로, 월드 시드를 감춘 값(obfuscateSeed)으로 만든 것 하나. */
    @Override
    public net.minecraft.world.level.biome.BiomeManager getBiomeManager() {
        var m = biomeManager;
        if (m == null) {
            biomeManager = m = new net.minecraft.world.level.biome.BiomeManager(this,
                    net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(furnace().seed()));
        }
        return m;
    }

    /** Paper Level#getChunkIfLoadedImmediately: {@code chunkSource.getChunkAtIfLoadedImmediately} — 로드된 청크의 보기, 아니면 null. 아무 스레드. */
    @Override
    public final ChunkAccess getChunkIfLoadedImmediately(int x, int z) { return getChunkSource().getChunkNow(x, z); }

    /** 바닐라 ServerLevel#enabledFeatures: 서버에 켜진 기능 플래그(데이터 팩 구성). 아무 스레드. */
    @Override
    public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() {
        java.util.List<ResourceLocation> names = new java.util.ArrayList<>();
        for (String n : NmsChunks.bridge().enabledFeatureFlags()) names.add(ResourceLocation.parse(n));
        return net.minecraft.world.flag.FeatureFlags.REGISTRY.fromNames(names);
    }

    /**
     * 바닐라 Level#getCurrentDifficultyAt: 청크가 있으면 그 청크의 머문 시간·달 밝기를 넣은 지역 난이도(서버 값 — 난이도 프로필의 지역 배율 포함).
     * 청크가 로드돼 있으면 그 칸의 주인 스레드에서 읽는다(주인이 아닌 틱 스레드면 ThreadOwnershipException, 그 밖의 스레드는 기다리는 왕복 — {@code NmsOwner}).
     */
    @Override
    public net.minecraft.world.DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        var b = NmsChunks.bridge();
        var w = furnace();
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        float[] d;
        if (hasChunkAt(pos)) {
            d = org.domi.furnace.compat.nms.NmsOwner.call(w, x, y, z, "Level#getCurrentDifficultyAt", () -> b.currentDifficultyNow(w, x, y, z));
        } else {
            try {
                d = b.currentDifficultyNow(w, x, y, z);
            } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException e) {   // 그사이 로드됐다
                d = org.domi.furnace.compat.nms.NmsOwner.call(w, x, y, z, "Level#getCurrentDifficultyAt", () -> b.currentDifficultyNow(w, x, y, z));
            }
        }
        return net.minecraft.world.DifficultyInstance.furnaceOf(net.minecraft.world.Difficulty.byId((int) d[0]), d[1]);
    }

    /**
     * 바닐라 Level#destroyBlock(4인자): 서버의 블록 부수기(바닐라 순서 — 레벨 이벤트 2001, 떨굼, 유체 상태로 setBlock(3, 깊이), BLOCK_DESTROY). 그 칸의 주인
     * 스레드(빌림 포함)에서만, 로드되지 않은 칸은 IllegalStateException({@link #setBlock} 과 같다). Paper 와 다른 점: 경험치를 뽑는 차례(설계 9.2),
     * {@code BlockDestroyEvent} 를 부르지 않는다. 부수는 엔티티는 이 스레드가 가진 것이어야 한다(아니면 UnsupportedNmsCall).
     */
    @Override
    public boolean destroyBlock(BlockPos pos, boolean drop, net.minecraft.world.entity.Entity breakingEntity, int maxUpdateDepth) {
        if (isOutsideBuildHeight(pos)) return false;   // 바닐라: 높이 밖은 공기
        if (!hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) throw NmsChunks.notLoaded(furnace(), pos.getX() >> 4, pos.getZ() >> 4, "Level#destroyBlock");
        var breaker = breakingEntity == null ? null : breakingEntity.getBukkitEntity().furnaceHandle();
        try {
            return NmsChunks.bridge().destroyBlockNow(furnace(), pos.getX(), pos.getY(), pos.getZ(), drop, breaker, maxUpdateDepth);
        } catch (IllegalArgumentException e) {   // 부수는 엔티티가 이 스레드의 것이 아니거나 사라졌다
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("Level#destroyBlock", e.getMessage());
        }
    }

    /** 바닐라 Level#getDayTime(레벨 데이터의 하루 시각). */
    public long getDayTime() { return furnace().dayTime(); }

    /** shim 의 LevelAccessor#dayTime(레벨 데이터 대신 — T-221). */
    @Override
    public long dayTime() { return getDayTime(); }

    /** 서버 레지스트리 묶음(H7b, {@link net.minecraft.server.MinecraftServer#registryAccess()} 와 같다). */
    public net.minecraft.core.RegistryAccess registryAccess() { return net.minecraft.core.RegistryAccess.SERVER; }

    public boolean isClientSide() { return isClientSide; }

    /** 이 월드의 차원 키(minecraft:overworld 등). */
    public ResourceKey<Level> dimension() { return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(furnace().dimension())); }

    /**
     * 바닐라 Level#getEntity(int): 그 네트워크 id 의 이 월드 엔티티(플레이어 포함), 없으면 null — ProtocolLib 의 getEntityFromID 가 쓴다.
     * Paper·Folia 는 어느 스레드에서나 기다림 없이 돌려준다. Furnace 도 그 엔티티의 Bukkit 객체를 이미 알면(같은 UUID 캐시) 어느 스레드에서나
     * 곧바로 마지막 스냅샷의 shim 을 준다. 처음 보는 엔티티는 스냅샷을 그 엔티티를 가진 region 이 떠야 해서: 그 region 스레드(빌림 구간 포함)면 곧바로,
     * 틱 스레드가 아닌 스레드(네트워크·비동기)는 최대 1초 기다리고, 틱 스레드가 남의 region 의 처음 보는 엔티티를 찾으면 교착을 피하려고
     * ThreadOwnershipException 이다(Paper·Folia 와 다른 점).
     */
    public net.minecraft.world.entity.Entity getEntity(int id) { return null; }   // 서버 월드(ServerLevel)가 재정의한다

    // ------------------------------------------------------------------ 높이·청크(N2a)

    @Override
    public int getMinBuildHeight() { return NmsChunks.bridge().minY(furnace()); }

    @Override
    public int getHeight() { return NmsChunks.bridge().height(furnace()); }

    @Override
    public abstract ChunkSource getChunkSource();

    /** 바닐라 Level#getLightEngine: 청크 공급원의 빛 엔진(T-205). */
    public net.minecraft.world.level.lighting.LevelLightEngine getLightEngine() { return getChunkSource().getLightEngine(); }

    /** 바닐라 BlockAndTintGetter#getBrightness: 그 층 듣기의 칸 값(Paper 규칙 — {@link net.minecraft.world.level.lighting.LevelLightEngine}). */
    public int getBrightness(LightLayer layer, BlockPos pos) { return getLightEngine().getLayerListener(layer).getLightValue(pos); }

    /** 바닐라 BlockAndTintGetter#getRawBrightness: 빛 엔진의 getRawBrightness(T-205). */
    public int getRawBrightness(BlockPos pos, int ambientDarkness) { return getLightEngine().getRawBrightness(pos, ambientDarkness); }

    /** 로드된 청크의 보기. 로드돼 있지 않으면 IllegalStateException(Paper 는 동기로 불러온다). */
    private volatile ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup furnaceEntityLookup;

    /** Paper(Moonrise): 이 월드의 엔티티 찾기(N2b-6 — 청크별 엔티티 묶음만). FAWE 가 이 메서드가 Level 에 있는지로 Paper 엔티티 길을 고른다. */
    public final ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup moonrise$getEntityLookup() {
        var l = furnaceEntityLookup;
        if (l == null) furnaceEntityLookup = l = new ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup((net.minecraft.server.level.ServerLevel) this);
        return l;
    }

    public LevelChunk getChunk(int x, int z) { return (LevelChunk) getChunk(x, z, ChunkStatus.FULL, true); }

    public LevelChunk getChunkAt(BlockPos pos) { return getChunk(pos.getX() >> 4, pos.getZ() >> 4); }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) { return getChunkSource().getChunk(x, z, status, create); }

    /** 지금 로드돼 있는가(아무 스레드). */
    @Override
    public boolean hasChunk(int x, int z) { return NmsChunks.bridge().isLoaded(furnace(), x, z); }

    public boolean isLoaded(BlockPos pos) { return !isOutsideBuildHeight(pos) && hasChunkAt(pos); }

    /** Paper: 로드된 청크면 그 보기, 아니면 null(불러오지 않고 기다리지 않는다 — 주인이 아닌 스레드는 공개본이 생긴 뒤부터 보인다). 아무 스레드. */
    public LevelChunk getChunkIfLoaded(int x, int z) { return getChunkSource().getChunkNow(x, z); }

    /** Paper Level#getChunkIfLoaded(pos): 그 칸의 로드된 청크, 아니면 null. */
    public final LevelChunk getChunkIfLoaded(BlockPos pos) { return getChunkIfLoaded(pos.getX() >> 4, pos.getZ() >> 4); }

    /** 바닐라 isInSpawnableBounds: 높이 ±2000만 안, 가로 ±3000만 안. */
    public static boolean isInSpawnableBounds(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return y >= -20000000 && y < 20000000 && x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000;
    }

    // ------------------------------------------------------------------ 블록 읽기(N1)

    /** 높이 밖은 void_air(바닐라). */
    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState captured = capturedState(pos);
        if (captured != null) return captured;
        if (isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState();
        return getChunk(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos);
    }

    /** Paper: 높이 밖은 void_air, 청크가 로드돼 있지 않으면 null. */
    @Override
    public BlockState getBlockStateIfLoaded(BlockPos pos) {
        BlockState captured = capturedState(pos);
        if (captured != null) return captured;
        if (isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState();
        LevelChunk c = getChunkIfLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        return c == null ? null : c.getBlockState(pos);
    }

    /** 높이 밖은 빈 유체(바닐라). */
    @Override
    public FluidState getFluidState(BlockPos pos) {
        if (isOutsideBuildHeight(pos)) return Fluids.EMPTY.defaultFluidState();
        return getChunkAt(pos).getFluidState(pos);
    }

    /**
     * 바닐라 Level#getHeight(종류, x, z): 그 칸 위로 처음 비는 y. 청크가 로드돼 있지 않으면 최저 높이(바닐라와 같다 — 불러오지 않는다).
     * 월드 가로 경계(±30,000,000) 밖의 해수면 + 1 은 아직 없다.
     */
    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        if (x < -30_000_000 || z < -30_000_000 || x >= 30_000_000 || z >= 30_000_000) {
            throw new UnsupportedNmsCall("Level#getHeight(outside the world border)", "the answer outside the border (sea level + 1) needs the sea level of the dimension and is not available yet");
        }
        if (!hasChunk(x >> 4, z >> 4)) return getMinBuildHeight();
        return getChunk(x >> 4, z >> 4).getHeight(type, x & 15, z & 15) + 1;
    }

    // ------------------------------------------------------------------ 블록 엔티티(T-198)

    /**
     * Paper Level#getBlockEntity(pos): 그 칸의 블록 엔티티 보기 — 없고 블록이 블록 엔티티를 가지는 종류면 만든다(바닐라 IMMEDIATE). 높이 밖이면 null.
     * Paper 는 틱 스레드가 아니면 null 을 준다 — Furnace 는 그 칸의 주인 region 스레드(이벤트 처리기·entityScheduler·Bukkit 동기 작업의 빌림 구간)가 아니면 null.
     * 로드돼 있지 않은 청크는 null — Paper 1.21.1 은 틱 스레드에서 getChunkAt 으로 불러온다(차이: region 스레드에서 멈춰 서서 불러오는 일은 하지 않는다 — 스케줄러 규칙 6).
     */
    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
        net.minecraft.world.level.block.entity.BlockEntity captured;
        if (!capturedTileEntities.isEmpty() && (captured = capturedTileEntities.get(pos)) != null) return captured;   // Paper: 기록된 블록 엔티티가 먼저
        if (isOutsideBuildHeight(pos)) return null;
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!hasChunk(cx, cz) || !NmsChunks.bridge().ownsChunk(furnace(), cx, cz)) return null;
        return getChunk(cx, cz).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
    }

    /** 바닐라 Level#removeBlockEntity: 그 칸 청크의 removeBlockEntity(높이 밖은 아무것도 안 함). 주인이 아닌 스레드는 거울 일지(LevelChunk 참고). */
    public void removeBlockEntity(BlockPos pos) {
        if (isOutsideBuildHeight(pos)) return;
        getChunkAt(pos).removeBlockEntity(pos);
    }

    /**
     * 호환 모듈 전용 — 바닐라 Level#blockEntityChanged + 정적 BlockEntity.setChanged 의 나머지(그 칸 블록이 공기가 아니면 updateNeighbourForOutputSignal).
     * 청크가 로드돼 있지 않으면 아무것도 하지 않는다. 주인이 아닌 스레드는 그 청크의 거울 일지로.
     */
    public void furnaceBlockEntityChanged(BlockPos pos) {
        LevelChunk c = getChunkIfLoaded(pos.getX() >> 4, pos.getZ() >> 4);
        if (c != null) c.furnaceBlockEntityChanged(pos);
    }

    /**
     * 바닐라 Level#playSound(제외할 플레이어, 칸, 소리, 분류, 크기, 높이): 칸 가운데(+0.5)에서 그 소리 — 근처 플레이어가 듣는다(바닐라 ServerLevel 처럼 난수 씨앗은
     * 스레드 안전 난수). 특정 플레이어를 빼는 것(except 가 null 이 아님)은 아직 없다(UnsupportedNmsCall).
     */
    public void playSound(net.minecraft.world.entity.player.Player except, BlockPos pos, net.minecraft.sounds.SoundEvent sound,
                          net.minecraft.sounds.SoundSource source, float volume, float pitch) {
        if (except != null) throw new UnsupportedNmsCall("Level#playSound(except a player)", "playing a sound to everyone except one player does not exist yet");
        world.playSound(new org.bukkit.Location(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), sound.getLocation().toString(),
                org.bukkit.SoundCategory.valueOf(source.name()), volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    /** 바닐라 {@code LevelAccessor#levelEvent(type, pos, data)}: 제외할 플레이어 없이 {@link #levelEvent(net.minecraft.world.entity.player.Player, int, BlockPos, int)}. */
    public void levelEvent(int type, BlockPos pos, int data) { levelEvent(null, type, pos, data); }

    /**
     * 바닐라 {@code ServerLevel#levelEvent}: 이 월드에서 그 칸 64칸 안(거리 제곱 &lt; 64²)에 있는 플레이어에게 레벨 이벤트(소리·입자 번호 type, data)를 보낸다.
     * except 는 뺀다. 이 서버는 이 이벤트를 받는 쪽 클라이언트에만 알린다 — 서버 상태는 바뀌지 않는다.
     */
    public void levelEvent(net.minecraft.world.entity.player.Player except, int type, BlockPos pos, int data) {
        var packet = org.domi.furnace.api.packet.GenericPacket.of(org.domi.furnace.api.packet.PacketTypes.Clientbound.LEVEL_EVENT)
                .with("type", type)
                .with("pos", new org.domi.furnace.api.world.BlockPos(pos.getX(), pos.getY(), pos.getZ()))
                .with("data", data)
                .with("global_event", false);
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            if (!(player instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) || !cp.furnaceHandle().isOnline()) continue;
            if (except != null && except.getBukkitEntity() == cp) continue;
            double dx = pos.getX() - player.getLocation().getX(), dy = pos.getY() - player.getLocation().getY(), dz = pos.getZ() - player.getLocation().getZ();
            if (dx * dx + dy * dy + dz * dz < 64.0 * 64.0) cp.furnaceHandle().sendPacket(packet);
        }
    }

    // ------------------------------------------------------------------ 블록 쓰기(N1)

    /** 바닐라 Block.UPDATE_MOVE_BY_PISTON. */
    private static final int UPDATE_MOVE_BY_PISTON = 64;

    /**
     * 바닐라 Level#setBlock 그대로(서버의 바닐라 포팅 ChunkLevel.setBlock): 플래그 1(이웃)·2(클라이언트 — 틱 끝에 섹션별로 모아 보낸다)·
     * 16(모양 갱신 안 함)·32(모양 갱신으로 부서진 이웃의 아이템 안 떨굼)·64(피스톤이 옮김)와 남은 깊이(maxUpdateDepth)를 바닐라 뜻대로 쓴다.
     * 4·8 은 클라이언트 쪽 다시 그리기라 서버에서 뜻이 없다. 바뀌었으면 true, 같은 상태거나 높이 밖이면 false.
     */
    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        if (treeCapture()) {   // Paper: 높이 검사보다 먼저 — 부술 수 없는 블록 칸이면 거짓, 아니면 칸마다 하나인 기록에 마지막 쓰기를 싣는다
            if (!getBlockState(pos).isDestroyable()) return false;
            synchronized (capturedBlockStates) {
                org.bukkit.craftbukkit.block.CraftBlockState blockstate = capturedBlockStates.get(pos);
                if (blockstate == null) {
                    blockstate = org.bukkit.craftbukkit.block.CapturedBlockState.getTreeBlockState(this, pos, flags);
                    capturedBlockStates.put(pos.immutable(), blockstate);
                }
                blockstate.setData(state);
                blockstate.setFlag(flags);
            }
            return true;
        }
        if (captureBlockStates && org.domi.furnace.compat.nms.NmsFeatures.captureAppliesHere()) {
            throw new UnsupportedNmsCall("Level#setBlock(captureBlockStates)",
                    "writing to the world without neighbour updates while recording the old states does not exist on the server yet");
        }
        if (isOutsideBuildHeight(pos)) return false;
        if (!hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) throw NmsChunks.notLoaded(furnace(), pos.getX() >> 4, pos.getZ() >> 4, "Level#setBlock");
        return NmsChunks.bridge().setBlockNow(furnace(), pos.getX(), pos.getY(), pos.getZ(), state.furnaceStateId(), flags, maxUpdateDepth);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags) { return setBlock(pos, state, flags, 512); }

    /**
     * 호환 모듈 전용 — {@code FluidState#tick}·{@code BlockState#tick}(fluid 가 null 이면 블록): 그 칸에서 받은 값으로 서버의 예약 틱 동작(FlowingFluid.tick·
     * Block#tick 이식)을 한 번 돈다. 스레드 규칙은 {@link #setBlock} 과 같다(그 칸의 주인 region). 쓰기 기록 중이면 기록에 싣지 못하므로 거절한다.
     */
    public final void furnaceScheduledTick(BlockPos pos, org.domi.furnace.api.world.FluidData fluid, BlockState block, net.minecraft.util.RandomSource random) {
        String what = fluid != null ? "FluidState#tick" : "BlockState#tick";
        if (treeCapture() || (captureBlockStates && org.domi.furnace.compat.nms.NmsFeatures.captureAppliesHere())) {
            throw new UnsupportedNmsCall(what + "(capture)", "running a scheduled tick while the world records block writes does not exist on the server yet");
        }
        if (!hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) throw NmsChunks.notLoaded(furnace(), pos.getX() >> 4, pos.getZ() >> 4, what);
        if (fluid != null) {
            NmsChunks.bridge().fluidTickNow(furnace(), pos.getX(), pos.getY(), pos.getZ(), fluid);
            return;
        }
        NmsChunks.bridge().blockTickNow(furnace(), pos.getX(), pos.getY(), pos.getZ(), block.furnaceStateId(), new FurnaceCallerRandom(random));
    }

    /** 부른 쪽 난수를 다리의 난수로(값은 그 난수열 그대로). 이름 있는 클래스 — 익명이면 바닐라 Level$1(WorldBorder)과 이름이 겹친다. */
    private record FurnaceCallerRandom(net.minecraft.util.RandomSource random) implements java.util.random.RandomGenerator {
        @Override public long nextLong() { return random.nextLong(); }
        @Override public int nextInt() { return random.nextInt(); }
        @Override public int nextInt(int bound) { return random.nextInt(bound); }
        @Override public float nextFloat() { return random.nextFloat(); }
        @Override public double nextDouble() { return random.nextDouble(); }
        @Override public boolean nextBoolean() { return random.nextBoolean(); }
        @Override public double nextGaussian() { return random.nextGaussian(); }
    }

    public boolean setBlockAndUpdate(BlockPos pos, BlockState state) { return setBlock(pos, state, 3); }

    /** 바닐라: 그 칸의 유체만 남긴다(물에 잠긴 블록이면 물). */
    @Override
    public boolean removeBlock(BlockPos pos, boolean moved) {
        FluidState fluid = getFluidState(pos);
        return setBlock(pos, fluid.createLegacyBlock(), 3 | (moved ? UPDATE_MOVE_BY_PISTON : 0));
    }

    // ------------------------------------------------------------------ 쪼갠 setBlock 조각(T-204 — FAWE NativeAccess 등)
    // 서버 setBlock 과 같은 코드를 부른다. 원점 칸의 주인 region 스레드면 바로, 다른 스레드는 주인 작업으로(최대 한 틱 늦다 — NmsEffects).

    /** 바닐라 ServerLevel#sendBlockUpdated: 변경 방송(틱 끝에 섹션별로 모아 보낸다) + 충돌 모양이 바뀌었으면 길찾기 다시. flags 는 바닐라도 서버에서 보지 않는다. */
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        org.domi.furnace.compat.nms.NmsEffects.run(this, pos, org.domi.furnace.compat.nms.NmsEffects.SEND_BLOCK_UPDATED, oldState.furnaceStateId(),
                newState.furnaceStateId(), 0, 0L, "Level#sendBlockUpdated");
    }

    /** 바닐라 Level#blockUpdated: 6방향 이웃 갱신(updateNeighborsAt — 플러그인 BlockPhysicsEvent 포함). */
    @Override
    public void blockUpdated(BlockPos pos, net.minecraft.world.level.block.Block block) { updateNeighborsAt(pos, block); }

    /** 바닐라 Level#updateNeighborsAt: 서쪽·동쪽·아래·위·북쪽·남쪽 이웃의 neighborChanged(서버 갱신기 — 플러그인 BlockPhysicsEvent 포함). */
    public void updateNeighborsAt(BlockPos pos, net.minecraft.world.level.block.Block block) {
        org.domi.furnace.compat.nms.NmsEffects.run(this, pos, org.domi.furnace.compat.nms.NmsEffects.UPDATE_NEIGHBORS, block.defaultBlockState().furnaceStateId(),
                0, 0, 0L, "Level#updateNeighborsAt");
    }

    /** 바닐라 Level#updateNeighbourForOutputSignal: 옆(또는 단단한 블록 건너) 비교기에 알린다. */
    public void updateNeighbourForOutputSignal(BlockPos pos, net.minecraft.world.level.block.Block block) {
        org.domi.furnace.compat.nms.NmsEffects.run(this, pos, org.domi.furnace.compat.nms.NmsEffects.OUTPUT_SIGNAL, block.defaultBlockState().furnaceStateId(),
                0, 0, 0L, "Level#updateNeighbourForOutputSignal");
    }

    /** 바닐라 ServerLevel#onBlockStateChange: POI 종류가 바뀌면 POI 를 지우고 더한다(서버 setBlock 과 같이 미루지 않는다). */
    public void onBlockStateChange(BlockPos pos, BlockState oldState, BlockState newState) {
        org.domi.furnace.compat.nms.NmsEffects.run(this, pos, org.domi.furnace.compat.nms.NmsEffects.POI, oldState.furnaceStateId(), newState.furnaceStateId(),
                0, 0L, "Level#onBlockStateChange");
    }

    // ------------------------------------------------------------------ 예약 틱(T-204 — 바닐라 LevelAccessor 기본 메서드)
    // 서버 scheduleTick 과 같다(순번은 그 칸 청크가 없어도 하나 쓰고 예약은 버린다). 다른 스레드는 주인 작업으로 — 지연은 주인이 받은 틱부터 센다(최대 한 틱 늦다).

    public void scheduleTick(BlockPos pos, net.minecraft.world.level.block.Block block, int delay, net.minecraft.world.ticks.TickPriority priority) {
        org.domi.furnace.compat.nms.NmsEffects.run(this, pos, org.domi.furnace.compat.nms.NmsEffects.SCHEDULE_BLOCK_TICK, block.defaultBlockState().furnaceStateId(),
                delay, priority.getValue(), 0L, "Level#scheduleTick(Block)");
    }

    public void scheduleTick(BlockPos pos, net.minecraft.world.level.block.Block block, int delay) {
        scheduleTick(pos, block, delay, net.minecraft.world.ticks.TickPriority.NORMAL);
    }

    public void scheduleTick(BlockPos pos, net.minecraft.world.level.material.Fluid fluid, int delay, net.minecraft.world.ticks.TickPriority priority) {
        org.domi.furnace.compat.nms.NmsEffects.run(this, pos, org.domi.furnace.compat.nms.NmsEffects.SCHEDULE_FLUID_TICK,
                org.domi.furnace.compat.nms.NmsPlacement.fluidType(fluid), delay, priority.getValue(), 0L, "Level#scheduleTick(Fluid)");
    }

    public void scheduleTick(BlockPos pos, net.minecraft.world.level.material.Fluid fluid, int delay) {
        scheduleTick(pos, fluid, delay, net.minecraft.world.ticks.TickPriority.NORMAL);
    }

    /**
     * 바닐라 Level#getSharedSpawnPos: 월드 스폰. 경계 밖이면 경계 중심 칸의 MOTION_BLOCKING 맨 위(바닐라 getHeightmapPos — 그 청크가 없으면 최저 높이 바닥).
     * 네더·엔드는 서버가 오버월드 스폰을 함께 쓴다(바닐라 DerivedLevelData).
     */
    public BlockPos getSharedSpawnPos() {
        var sp = furnace().spawnLocation();
        BlockPos pos = new BlockPos((int) Math.floor(sp.x()), (int) Math.floor(sp.y()), (int) Math.floor(sp.z()));
        var border = getWorldBorder();
        if (!border.isWithinBounds(pos)) {
            int x = net.minecraft.util.Mth.floor(border.getCenterX()), z = net.minecraft.util.Mth.floor(border.getCenterZ());
            return new BlockPos(x, getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        }
        return pos;
    }

    /**
     * 바닐라 Level#getMapData: 번호의 지도 데이터(서버 전체 — 바닐라는 오버월드 저장소에서 읽는다). 없으면 null. 값은 읽을 때의 겉모양 사본이다.
     */
    public net.minecraft.world.level.saveddata.maps.MapItemSavedData getMapData(net.minecraft.world.level.saveddata.maps.MapId id) {
        var info = NmsChunks.bridge().mapInfo(id.id());
        return info == null ? null : net.minecraft.world.level.saveddata.maps.MapItemSavedData.furnaceOf(id.id(), info);
    }

    private volatile net.minecraft.world.level.border.WorldBorder furnaceBorder;

    /** 바닐라 Level#getWorldBorder: 이 월드의 경계(서버 경계를 비춘다 — 판정만). */
    public net.minecraft.world.level.border.WorldBorder getWorldBorder() {
        var b = furnaceBorder;
        if (b == null) furnaceBorder = b = new net.minecraft.world.level.border.WorldBorder(furnace());
        return b;
    }

    /** CraftBukkit Level#getCraftServer. */
    public org.bukkit.craftbukkit.CraftServer getCraftServer() { return org.bukkit.craftbukkit.CraftServer.INSTANCE; }

    @Override
    public String toString() { return getClass().getSimpleName() + "[" + world.getName() + "]"; }
}
