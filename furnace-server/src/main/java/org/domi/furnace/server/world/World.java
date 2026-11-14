// 월드: 공유 자원(청크 저장소·구독·접속자) + 동적 region(Regionizer). 월드 작업은 청크 주인 region 으로 보낸다(runAtChunk).
package org.domi.furnace.server.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityView;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;

public final class World {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(World.class);

    private final WorldSource worldSource;
    private final ChunkWorkerPool chunkPool;
    private final FurnaceScheduler scheduler;
    private final Regionizer regionizer;
    private volatile int viewDistance;   // 실행 중 바꿀 수 있다(ServerCore.setViewDistance)
    private final int entityViewDistance;
    private final int blockDirectBits;
    private final int biomeDirectBits;
    private final ExecutorService ioPool;
    private final ExecutorService genPool;
    // 같은 region 파일의 save/load 를 FIFO 로(ioPool 다중 스레드에서 저장 전에 읽어 옛 내용을 되살리던 것)
    private static final int IO_LANES = 16;
    private final org.domi.furnace.server.world.anvil.IoLanes ioLanes;
    // 재시도 끝에 실패한 저장 수(종료 훅이 로그). 저장 재시도 백오프(ms) — 레인 스레드에서 차례로 기다려 순서를 지킨다.
    private final java.util.concurrent.atomic.AtomicLong saveFailures = new java.util.concurrent.atomic.AtomicLong();
    static long[] saveRetryBackoffMs = {50, 250, 1000};

    // 영속성 설정(FurnaceMain서 configurePersistence로 주입; 테스트는 기본 비활성).
    private int autosaveTicks = Integer.MAX_VALUE; // 기본 비활성(테스트)
    private int chunkEvictionGraceTicks = 100;
    // 청크 스트리밍 설정(FurnaceMain서 주입). 테스트 기본값.
    private int spawnChunkRadius = 2;   // join 동기 전송 스폰 청크 반경
    private int chunksPerTick = 16;     // 플레이어당 틱 전송 상한(0 = auto). 서버는 configureStreaming 으로 설정값(기본 auto)을 넣는다
    private java.nio.file.Path worldDir = java.nio.file.Path.of("world");
    private BlockRegistry blockRegistry;
    private ItemRegistry itemRegistry;
    private volatile SpawnPoint defaultSpawn = new SpawnPoint(0, 64, 0, 0.0f);
    // 이 월드의 차원(기본 오버월드 — 단일 차원 테스트·도구 호환). 저장 폴더·높이·하늘빛이 여기서 나온다.
    private volatile DimensionType dimensionType;
    private volatile Dimensions dimensions;
    /** 이 차원의 보더(바닐라 Level.worldBorder) — 네더는 중심을 좌표 비로 나눈다. 어느 스레드든 읽는다(보더 자체가 직렬화). */
    private final WorldBorder border;
    /** 오버월드 보더를 따라가는 고리(오버월드가 아니고 서버에 붙었을 때). 떼어 낼 때 쓴다. */
    private volatile WorldBorder.Delegate borderFollow;

    // 로드된 청크와 로드 중인 청크(월드 공용). 청크 내용은 그 청크 구역을 가진 region 스레드만 만진다.
    private final ConcurrentHashMap<Long, ChunkColumn> chunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CompletableFuture<ChunkColumn>> loading = new ConcurrentHashMap<>();

    /**
     * 청크 구독 상태. 청크가 클라에 전달되기 전(ready=false)의 블록 변경은 pending에 쌓았다가 청크 뒤에 보낸다 —
     * 스냅샷 이후·전송 이전의 변경이 청크보다 먼저 도착해 클라가 "청크 미로드"로 버리면 그 변경을 영구히 놓친다.
     * ready/pending은 청크 소유 region 스레드서만 접근한다(브로드캐스트와 전달 완료 처리가 같은 스레드라 직렬화됨).
     */
    public static final class Subscription {
        /** 소유 region 스레드가 쓴다. 이웃 region 의 빛 가장자리 판정이 읽으므로 volatile. */
        volatile boolean ready;
        final List<Packet> pending = new ArrayList<>();
    }

    // 청크별 구독자(그 청크를 로드한 플레이어)와 구독 상태. 블록 변경 시 이들에게만 전송.
    private final ConcurrentHashMap<ChunkPos, ConcurrentHashMap<Player, Subscription>> chunkSubscribers = new ConcurrentHashMap<>();

    /** 기본 region 설정(Config.DEFAULT 의 구역 크기·합치기 반경). regionSize 인자는 예전 고정 격자용이라 쓰지 않는다. */
    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int regionSize, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits, ExecutorService ioPool, ExecutorService genPool) {
        this(worldSource, chunkPool, scheduler, regionThreads, org.domi.furnace.server.config.Config.DEFAULT.regionSectionShift(),
                org.domi.furnace.server.config.Config.DEFAULT.regionMergeRadius(), viewDistance, entityViewDistance,
                blockDirectBits, biomeDirectBits, ioPool, genPool);
    }

    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int sectionShift, int mergeRadius, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits, ExecutorService ioPool, ExecutorService genPool) {
        this(DimensionType.OVERWORLD, worldSource, chunkPool, scheduler, regionThreads, sectionShift, mergeRadius, viewDistance,
                entityViewDistance, blockDirectBits, biomeDirectBits, ioPool, genPool);
    }

    /**
     * 차원 월드. region 은 월드마다 따로(Regionizer 와 작업 스레드가 월드 단위) — 스케줄러의 RegionPos 라우팅(플러그인 region
     * 스케줄러)은 오버월드만 건다. 다른 차원은 종료 훅만 단다.
     */
    public World(DimensionType type, WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int sectionShift, int mergeRadius, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits, ExecutorService ioPool, ExecutorService genPool) {
        this(type, worldSource, chunkPool, scheduler, regionThreads, null, sectionShift, mergeRadius, viewDistance, entityViewDistance,
                blockDirectBits, biomeDirectBits, ioPool, genPool);
    }

    /** 이미 있는 작업 스레드 풀(서버 전체 공유, S4)에 이 월드를 건다. 풀 수명은 부르는 쪽이 갖는다 — 월드 close 는 자기 큐만 뗀다. */
    public World(DimensionType type, WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler, SharedScheduler pool,
          int sectionShift, int mergeRadius, int viewDistance, int entityViewDistance,
          int blockDirectBits, int biomeDirectBits, ExecutorService ioPool, ExecutorService genPool) {
        this(type, worldSource, chunkPool, scheduler, pool.threadCount(), pool, sectionShift, mergeRadius, viewDistance, entityViewDistance,
                blockDirectBits, biomeDirectBits, ioPool, genPool);
    }

    private World(DimensionType type, WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                  int regionThreads, SharedScheduler pool, int sectionShift, int mergeRadius, int viewDistance, int entityViewDistance,
                  int blockDirectBits, int biomeDirectBits, ExecutorService ioPool, ExecutorService genPool) {
        this.dimensionType = type;
        this.border = new WorldBorder(type.coordinateScale());
        this.worldSource = worldSource;
        this.ioPool = ioPool;
        this.ioLanes = ioPool == null ? null : new org.domi.furnace.server.world.anvil.IoLanes(ioPool, IO_LANES);
        this.genPool = genPool;
        this.chunkPool = chunkPool;
        this.scheduler = scheduler;
        this.viewDistance = viewDistance;
        this.entityViewDistance = entityViewDistance;
        this.blockDirectBits = blockDirectBits;
        this.biomeDirectBits = biomeDirectBits;
        long tickNanos = scheduler instanceof org.domi.furnace.server.sched.FurnaceSchedulerImpl impl ? impl.tickNanos() : 50_000_000L;
        if (pool == null && SharedScheduler.TEST_SHARED) pool = SharedScheduler.testShared(regionThreads);   // 시험 전용: 기존 시험을 공유 풀에서 돌린다
        this.regionizer = pool == null
                ? new Regionizer(this, regionThreads, sectionShift, mergeRadius, tickNanos)
                : new Regionizer(this, pool, sectionShift, mergeRadius, tickNanos);
        this.closeHook = regionizer::close;
        if (scheduler instanceof org.domi.furnace.server.sched.FurnaceSchedulerImpl impl) {
            if (type == DimensionType.OVERWORLD) impl.installRouter((cx, cz) -> chunkScheduler(cx, cz), closeHook);
            else impl.addCloseHook(closeHook);
        }
    }

    private final Runnable closeHook;
    private volatile boolean unloaded;
    private volatile String levelName;
    /** (시드, 바이옴 확대 지터 시드) 불변 묶음 — null 이면 시드를 정하지 않은 월드. setter 에서 한 번 계산해 찢어 읽기·SHA-256 재계산이 없다. */
    private record SeedInfo(long seed, long zoomSeed) {}
    private volatile SeedInfo seedInfo;
    private volatile boolean extra;

    /** 실행 중에 올린 추가 월드인가(다중 월드 — 내릴 수 있다). */
    public boolean isExtraWorld() { return extra; }

    /** 추가 월드 표시와 이름(폴더 이름)·시드. 등록 전에 부른다. */
    public void markExtraWorld(String name) {
        this.extra = true;
        this.levelName = name;
    }

    /** 월드 이름(폴더 이름). 기본 세 차원은 null — 부르는 쪽이 level-name 으로 만든다(WorldHandle.name). */
    public String levelName() { return levelName; }

    /** 생성 시드(바닐라 BiomeManager 의 해시 시드·LoginPlay/Respawn 의 hashed seed 의 바탕). FurnaceMain/월드 관리자가 정한다. 정하지 않았으면 0. */
    public long seed() {
        SeedInfo si = seedInfo;
        return si == null ? 0L : si.seed();
    }

    /** 시드를 정했는가(시험 월드는 안 정한다 — 그때는 서버 시드를 쓴다). */
    public boolean hasSeed() { return seedInfo != null; }

    /** BiomeManager.obfuscateSeed(seed) — 시드를 정할 때 한 번 계산해 둔 값. 시드를 정하지 않았으면 0(부르는 쪽이 hasSeed 로 먼저 가른다). */
    public long zoomSeed() {
        SeedInfo si = seedInfo;
        return si == null ? 0L : si.zoomSeed();
    }

    /** 시드를 정했으면 그 시드, 아니면 fallback — SeedInfo 를 한 번만 읽는다. */
    public long seedOr(long fallback) {
        SeedInfo si = seedInfo;
        return si == null ? fallback : si.seed();
    }

    /** 시드를 정했으면 그 시드의 확대 지터 시드, 아니면 fallback — SeedInfo 를 한 번만 읽는다. */
    public long zoomSeedOr(long fallback) {
        SeedInfo si = seedInfo;
        return si == null ? fallback : si.zoomSeed();
    }

    public void seed(long seed) {
        this.seedInfo = new SeedInfo(seed, BiomeZoom.obfuscateSeed(seed));
    }

    /** 내렸는가(다시 쓰지 않는다). */
    public boolean isUnloaded() { return unloaded; }

    /**
     * 추가 월드 내리기의 마지막 단계: 게임 틱을 끄고 region 작업 스레드를 닫고 스케줄러 종료 훅에서 뗀다. 저장은 부르는 쪽이 먼저 끝낸다.
     * 이 뒤 이 월드에 넣는 작업은 받아들여지지 않는다.
     */
    public void closeForUnload() {
        unloaded = true;
        regionizer.gameTicks(false);
        if (scheduler instanceof org.domi.furnace.server.sched.FurnaceSchedulerImpl impl) impl.removeCloseHook(closeHook);
        closeHook.run();
    }

    // ------------------------------------------------------------------ 수동 시계(헤드리스 시험 서버 furnace-test 전용 — Regionizer 설명)

    /** region 이 벽시계 대신 시험이 낸 틱만 돌게 한다. startTicking 전에 한 번 부른다. */
    public void useManualClock() { regionizer.enableManualClock(); }

    public boolean manualClock() { return regionizer.isManualClock(); }

    /** 시험 틱 하나를 낸다. 모든 region 이 이 번호까지 돌면 {@link #manualTickDone} 이 참이 된다. */
    public long advanceManualTick() { return regionizer.manualAdvance(); }

    /** 살아 있는 모든 region 이 target 번째 시험 틱을 마쳤는가. */
    public boolean manualTickDone(long target) { return regionizer.manualCaughtUp(target); }

    /** 게임 틱을 끈다(내리기 시작 — 작업은 계속 처리해 저장을 끝낸다). */
    public void stopTicking() { regionizer.gameTicks(false); }

    private final java.util.concurrent.atomic.AtomicInteger incoming = new java.util.concurrent.atomic.AtomicInteger();

    /** 이 월드로 오는 중인 차원 이동 수(목적 청크 준비 중). */
    public int incomingTravels() { return incoming.get(); }

    /** 이 월드에서 떠나 아직 도착(또는 되돌림 작업 넣기)하지 않은 엔티티 이동 수 — 같은 레벨 관문 이동도 센다. */
    private final java.util.concurrent.atomic.AtomicInteger outgoing = new java.util.concurrent.atomic.AtomicInteger();

    public int outgoingTravels() { return outgoing.get(); }

    /** 로드 중인 청크 수(생성·읽기 진행 중). */
    public int pendingChunkLoads() { return loading.size(); }

    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int regionSize, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits, ExecutorService ioPool) {
        this(worldSource, chunkPool, scheduler, regionThreads, regionSize, viewDistance,
            entityViewDistance, blockDirectBits, biomeDirectBits, ioPool, ioPool);
    }

    public Regionizer regionizer() { return regionizer; }

    public DimensionType dimensionType() { return dimensionType; }

    /** 이 차원의 보더(바닐라 ServerLevel.getWorldBorder). */
    public WorldBorder border() { return border; }

    /** 오버월드 보더를 따라가는 고리(없으면 null). ServerCore 가 붙이고 뗀다. */
    public WorldBorder.Delegate borderFollow() { return borderFollow; }

    public void borderFollow(WorldBorder.Delegate d) { this.borderFollow = d; }

    /** 서버가 띄운 차원들(단일 월드 테스트면 null). */
    public Dimensions dimensions() { return dimensions; }

    void dimensions(Dimensions d) { this.dimensions = d; }

    /** 다른 차원 월드(없거나 단일 월드면 null). */
    public World otherWorld(String levelKey) {
        Dimensions d = dimensions;
        return d == null ? null : d.get(levelKey);
    }

    /**
     * 다른 차원과 서버 상태를 공유한다(Dimensions.register): 접속자 목록(탭 목록은 차원과 무관), 월드 시계(바닐라 DerivedLevelData —
     * 네더·엔드의 시간·날씨 데이터는 오버월드 것), 엔티티 id 공간.
     */
    void shareServerState(World overworld) {
        this.online = overworld.online;
        this.clock = overworld.clock;
        this.entityIds = overworld.entityIds;
    }

    /** 레벨 키(minecraft:overworld …). */
    public String dimension() { return dimensionType.levelKey(); }

    /** 게임 틱을 켠다(서버 준비가 끝난 뒤). 꺼져 있으면 region 은 작업만 처리한다. */
    public void startTicking() {
        regionizer.gameTicks(true);
        // /forceload 로 붙잡아 둔 청크를 다시 올린다(chunks.dat)
        var f = forcedChunks();
        for (long k : f.sortedKeys()) {
            ChunkPos cp = new ChunkPos(ForcedChunks.xOf(k), ForcedChunks.zOf(k));
            runAtChunk(cp, r -> r.loadChunk(cp));
        }
    }

    /** 모든 region 에서 게임 틱을 한 번씩(테스트용 — 게임 틱이 꺼져 있을 때 손으로 돌린다). */
    public CompletableFuture<Void> tickOnce() {
        return CompletableFuture.allOf(regionizer.forEachRegion(Region::tick).toArray(new CompletableFuture[0]));
    }

    // ------------------------------------------------------------------ 청크 저장소(주인 region 스레드에서 쓴다)

    // chunks·loading 의 키는 청크 키를 섞은 값이다. Long.hashCode(x<<32|z) 는 x^z 라 가까운 청크끼리 해시가 겹쳐
    // 버킷이 트리로 바뀌고 조회가 느려진다(매 블록 조회마다 탄다). 홀수 곱은 일대일이라 키 충돌은 없다. 순회하지 않는다.
    private static Long mapKey(long key) { return key * 0x9E3779B97F4A7C15L; }

    /** 그 청크가 지금 로드돼 있는가(아무 스레드, 최근 값). */
    public boolean isChunkLoaded(int cx, int cz) { return loadedChunk(cx, cz) != null; }

    ChunkColumn loadedChunk(int cx, int cz) { return chunks.get(mapKey(Regionizer.chunkKey(cx, cz))); }

    void putChunk(int cx, int cz, ChunkColumn c) { chunks.put(mapKey(Regionizer.chunkKey(cx, cz)), c); }

    void removeChunk(int cx, int cz) { chunks.remove(mapKey(Regionizer.chunkKey(cx, cz))); }

    CompletableFuture<ChunkColumn> loadingChunk(long key) { return loading.get(mapKey(key)); }

    void putLoading(long key, CompletableFuture<ChunkColumn> f) { loading.put(mapKey(key), f); }

    void removeLoading(long key, CompletableFuture<ChunkColumn> f) { loading.remove(mapKey(key), f); }

    /** 새 판(DataVersion) 청크를 이미 알렸는가 — 처음이면 true(A05-03, 로그 한 번만). */
    private final java.util.Set<Long> newerChunksWarned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    boolean warnNewerChunkOnce(long key) { return newerChunksWarned.add(key); }

    /** 로드된 청크 수(테스트·모니터링). */
    public int loadedChunkCount() { return chunks.size(); }

    // 바닐라 PrimaryLevelData.specialWorldProperty(FLAT·DEBUG): 월드 하나의 모든 차원이 같은 값을 login/respawn 의 is_flat·is_debug 로 클라이언트에 알린다.
    private volatile boolean flatLevel, debugLevel;

    /** 이 월드(level.dat)의 오버월드 생성기가 평지인가 — 클라이언트의 지평선 높이·허공 안개가 달라진다. */
    public boolean isFlatLevel() { return flatLevel; }

    /** 오버월드 생성기가 디버그(모든 블록 상태 격자)인가 — 클라이언트는 저장된 블록 대신 격자 식으로 그린다. */
    public boolean isDebugLevel() { return debugLevel; }

    public void levelKind(boolean flat, boolean debug) {
        this.flatLevel = flat;
        this.debugLevel = debug;
    }

    // ------------------------------------------------------------------ 작업 보내기

    /** 그 청크 주인 region 에서 task 실행(주인이 없으면 그 구역의 region 을 만든다). 실패는 로그로 남긴다. */
    public void runAtChunk(ChunkPos cp, java.util.function.Consumer<Region> task) {
        if (unloaded) throw new java.util.concurrent.RejectedExecutionException("내린 월드: " + dimension());
        regionizer.enqueue(Regionizer.chunkKey(cp.x(), cp.z()), task);
    }

    /** 그 블록 좌표의 청크 주인 region 에서. */
    public void runAt(double blockX, double blockZ, java.util.function.Consumer<Region> task) {
        runAtChunk(ChunkPos.ofBlock(blockX, blockZ), task);
    }

    /**
     * 클라이언트가 보낸 블록 좌표의 작업: 그 청크에 주인 region 이 있으면 거기서, 없으면 플레이어가 선 청크의 region 에서
     * (그 region 에선 남의 청크라 "로드 안 된 칸"으로 보인다). 아무 좌표나 보내 region 을 새로 만들게 하지 못한다.
     */
    public void runAtRequested(int blockX, int blockZ, double playerX, double playerZ, java.util.function.Consumer<Region> task) {
        ChunkPos cp = ChunkPos.ofBlock(blockX, blockZ);
        if (regionizer.ownerOf(cp.x(), cp.z()) == null) cp = ChunkPos.ofBlock(playerX, playerZ);
        runAtChunk(cp, task);
    }

    /** runAtChunk 의 결과판. */
    public <T> CompletableFuture<T> submitAtChunk(ChunkPos cp, java.util.function.Function<Region, T> task) {
        if (unloaded) return CompletableFuture.failedFuture(new java.util.concurrent.RejectedExecutionException("내린 월드: " + dimension()));
        CompletableFuture<T> f = new CompletableFuture<>();
        runAtChunk(cp, r -> {
            try { f.complete(task.apply(r)); } catch (Throwable t) { f.completeExceptionally(t); }
        });
        return f;
    }

    /** submitAtChunk 의 조회판: 그 청크에 주인 region 이 없으면 region 을 만들지 않고 null. */
    public <T> CompletableFuture<T> submitAtOwnedChunk(ChunkPos cp, java.util.function.Function<Region, T> task) {
        if (unloaded) return CompletableFuture.failedFuture(new java.util.concurrent.RejectedExecutionException("내린 월드: " + dimension()));
        CompletableFuture<T> f = new CompletableFuture<>();
        boolean sent = regionizer.enqueueIfOwned(Regionizer.chunkKey(cp.x(), cp.z()), r -> {
            try { f.complete(task.apply(r)); } catch (Throwable t) { f.completeExceptionally(t); }
        });
        return sent ? f : null;
    }

    /** 살아 있는 region 마다 한 번씩(저장·종료 정리). */
    public List<CompletableFuture<Void>> forEachRegion(java.util.function.Consumer<Region> task) {
        return regionizer.forEachRegion(task);
    }

    /** 그 청크 주인 region 에서 도는 스케줄러(플러그인 region 스케줄러). */
    public org.domi.furnace.api.sched.TaskScheduler chunkScheduler(int cx, int cz) { return new ChunkScheduler(cx, cz); }

    private final class ChunkScheduler implements org.domi.furnace.api.sched.TaskScheduler, ThreadOwner {
        private final ChunkPos cp;

        ChunkScheduler(int cx, int cz) { this.cp = new ChunkPos(cx, cz); }

        @Override
        public CompletableFuture<Void> execute(Runnable task) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            runAtChunk(cp, r -> {
                try { task.run(); f.complete(null); } catch (Throwable t) { f.completeExceptionally(t); }
            });
            return f;
        }

        @Override
        public <T> CompletableFuture<T> submit(java.util.function.Supplier<T> task) {
            return submitAtChunk(cp, r -> task.get());
        }

        @Override
        public CompletableFuture<Void> executeDelayed(Runnable task, long delayTicks) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            long key = Regionizer.chunkKey(cp.x(), cp.z());
            java.util.function.Consumer<Region> run = r2 -> {
                try { task.run(); f.complete(null); } catch (Throwable t) { f.completeExceptionally(t); }
            };
            // 그 청크의 region 스레드 안이면 곧바로 건다 — 작업 큐를 한 번 더 거치면 다음 틱 머리에서야 걸려 한 틱 밀린다
            // (반복 작업 runAtFixedRate(…, 1, 1) 이 두 틱마다 돌던 원인). 밖에서 부르면 예전처럼 큐를 거친다.
            Region owner = regionizer.ownerOf(cp.x(), cp.z());
            // 곧바로 걸 때 0 은 1 로 — 지금 돌고 있는 지연 작업 차례에서 다시 꺼내 같은 틱에 돌거나, 자기를 다시 거는 작업이 끝없이 돌지 않게
            if (owner != null && owner.isOwnerThread()) owner.schedule(key, run, Math.max(1, delayTicks));
            else runAtChunk(cp, r -> r.schedule(key, run, delayTicks));
            return f;
        }

        /** 그 청크를 가진 region 의 스레드인가. 전역 스레드의 빌림 구간(RegionBorrow) 안이면 그 region 을 빌려서라도 참으로 만든다. */
        @Override
        public boolean isOwnerThread() {
            return RegionBorrow.claimChunk(World.this, cp.x(), cp.z());
        }

        @Override
        public String ownerName() { return "region@chunk" + cp; }
    }

    /** 저장·종료용: 모든 region 의 dirty 청크 저장(각 region 스레드에서). 플레이어 저장은 각 연결의 channelInactive 가 한다. */
    public List<CompletableFuture<Void>> flushAll() { return flushAll(null); }

    /** deferred 가 있으면 레인이 밀려 이번에 넣지 못한(자동 저장으로 넘긴) 청크 수를 더한다. */
    private List<CompletableFuture<Void>> flushAll(java.util.concurrent.atomic.AtomicInteger deferred) {
        org.domi.furnace.server.map.MapStore.saveDirty();   // 바뀐 지도 데이터·idcounts
        List<CompletableFuture<Void>> fs = new ArrayList<>(forEachRegion(r -> {
            r.releaseDisconnectedMounts();
            r.saveOnlinePlayerVehicles();   // 바닐라 saveAll 의 플레이어 저장 중 탈것 몫(본체는 Player.requestSave)
            int left = r.saveDirtyChunks();
            if (deferred != null) deferred.addAndGet(left);
            r.publishRaids();
            var saver = dragonFightSaver;
            if (saver != null) snapshotDragonFight(r, saver);
        }));
        // 모든 region 이 습격 스냅샷을 올린 뒤 raids.dat 하나로 쓴다
        fs.add(CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).handle((v, t) -> {
            raidStore().saveIfDirty();
            var storage = commandStorage;
            if (storage != null) storage.saveIfDirty();
            var forced = forcedChunks;
            if (forced != null) forced.saveIfDirty();
            var stale = lightStale;
            if (stale != null) stale.saveIfDirty();
            var gen = org.domi.furnace.server.world.gen.NoiseWorldSource.of(worldSource);
            if (gen != null) gen.structureEngine().references().saveIfDirty();   // 탐험 지도 references(A06-06)
            return null;
        }));
        return fs;
    }

    /**
     * 종료·save-all 용: {@link #flushAll} 이 레인 역압으로 청크를 다 넣지 못했으면 IO 레인이 빈 뒤 다시 부르기를 되풀이한다(역압은 그대로
     * 지켜 저장 스냅샷이 한꺼번에 쌓이지 않는다). flushAll 한 번은 레인이 밀리면 남은 청크를 다음 틱의 자동 저장으로 넘기는데, 종료 때는
     * 다음 틱이 없어 그 청크가 저장되지 않고 사라졌다(여럿이 새 땅을 돌아 생성 청크가 많을 때 — M12). 한 번이라도 밀리지 않고 다 넣으면
     * 끝난다(그 사이 새로 dirty 가 된 청크는 기다리지 않는다 — 틱이 도는 save-all 이 끝없이 돌지 않게). 마감을 넘기면 남은 수를,
     * region 이 마감 안에 답하지 않아 남은 수를 모르면 -1 을 돌려준다.
     */
    public int flushAllDrained(long timeoutNanos) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutNanos;
        int left = 0;
        for (int round = 0; ; round++) {
            java.util.concurrent.atomic.AtomicInteger deferred = new java.util.concurrent.atomic.AtomicInteger();
            // 첫 회차는 전부(엔티티·지도·습격 포함), 다음 회차는 레인을 비운 뒤 미룬 청크만 — 적어도 하나는 넣어 회차마다 진행한다
            boolean done = round == 0
                    ? awaitQuietly(flushAll(deferred), deadline, "저장")
                    : awaitQuietly(forEachRegion(r -> deferred.addAndGet(r.saveDirtyChunks(false, true))), deadline, "저장");
            left = deferred.get();
            if (!done) return -1;   // 마감 안에 region 이 답하지 않았다 — 남은 수를 모른다
            if (left == 0 || System.nanoTime() >= deadline) break;
            if (round > 0 && round % 10 == 0) {
                org.slf4j.LoggerFactory.getLogger(World.class).info("[SAVE] {} 레인이 밀려 나눠 저장 중 — 남은 청크 {}", dimension(), left);
            }
            awaitQuietly(java.util.List.of(ioBarrier()), deadline, "IO 레인 비우기");
        }
        return left;
    }

    /** 모두 마감 안에 끝났으면 true(실패해도 끝난 것으로 본다 — 로그만). */
    private static boolean awaitQuietly(java.util.List<CompletableFuture<Void>> fs, long deadline, String what) throws InterruptedException {
        boolean all = true;
        for (var f : fs) {
            try {
                f.get(Math.max(1, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                all = false;
                org.slf4j.LoggerFactory.getLogger(World.class).error("[SAVE] {} 지연(마감 초과)", what, e);
            } catch (java.util.concurrent.ExecutionException e) {
                org.slf4j.LoggerFactory.getLogger(World.class).error("[SAVE] {} 실패", what, e);
            }
        }
        return all;
    }

    /** owner 스레드서 스냅샷 뜬 뒤 IO 레인서 .mca write. worldSource가 AnvilWorldSource일 때만. */
    public void saveChunkAsync(ChunkSnapshot snap) {
        saveChunkAsync(snap, null);
    }

    /**
     * 저장 제출. IO 실패는 레인에서 백오프 재시도하고, 끝내 실패하면 col 이 아직 로드돼 있을 때 주인 region 스레드에서
     * 다시 dirty 로 되돌려 다음 저장에 다시 싣는다(호출자는 제출 직후 clearDirty 한다).
     */
    public void saveChunkAsync(ChunkSnapshot snap, ChunkColumn col) {
        if (!(worldSource instanceof AnvilWorldSource anvil)) return;
        if (snap.light() != null && virtualLights.affects(snap.chunkX(), snap.chunkZ())) snap = snap.withoutLight();   // 가짜 빛은 디스크에 남기지 않는다
        final ChunkSnapshot snapF = snap;
        int cx = snap.chunkX(), cz = snap.chunkZ();
        // 저장 빛을 믿지 않는 표시 때문에 다시 계산한 빛을 싣는다 — 디스크에 써진 뒤에 표시를 지운다(그 사이 다시 표시됐으면 그대로)
        long staleSeq = 0;
        if (col != null && col.lightStaleSeq() != 0) {
            staleSeq = col.lightStaleSeq();
            col.lightStaleSeq(0);
        }
        final long staleSeqF = staleSeq;
        ioLane(cx, cz).execute(() -> {
            boolean ok;
            try {
                byte[] bytes = NbtIo.toBytes(anvil.codec().toNbt(snapF), "");
                ok = runWithRetry(() -> anvil.store().writeChunk(cx, cz, bytes), "청크 " + cx + "," + cz + " 저장");
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(World.class).error("[SAVE] 청크 {},{} 직렬화 실패", cx, cz, e);
                ok = false;
            }
            if (!ok) onChunkSaveFailed(new ChunkPos(cx, cz), col);
            else if (staleSeqF != 0) lightStale().clearIfSeq(cx, cz, staleSeqF);
        });
    }

    private void onChunkSaveFailed(ChunkPos cp, ChunkColumn col) {
        saveFailures.incrementAndGet();
        if (col == null) return;
        try {
            runAtChunk(cp, r -> r.restoreDirtyAfterFailedSave(cp, col));
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(World.class).error("[SAVE] 청크 {} 저장 실패 — 되돌릴 region 없음(유실)", cp, e);
        }
    }

    /** 재시도 끝에 실패한 저장(청크·엔티티) 수. */
    public long saveFailures() { return saveFailures.get(); }

    /** 레인당 대기 작업이 이 수를 넘으면 region 쪽 퇴출·자동 저장이 그 틱 저장을 멈춘다(내리지 않고 다음 틱에). -Dfurnace.io.backlogLimit */
    private volatile int ioBacklogLimit = Integer.getInteger("furnace.io.backlogLimit", 256);

    public int ioBacklogLimit() { return ioBacklogLimit; }

    public void ioBacklogLimit(int v) { ioBacklogLimit = v; }

    /** 이 월드가 지금까지 IO 레인에 넣은 저장·읽기가 모두 끝나면 완료(월드 내리기 — 저장소를 닫기 전에 기다린다). */
    public CompletableFuture<Void> ioBarrier() {
        return ioLanes == null ? CompletableFuture.completedFuture(null) : ioLanes.barrier();
    }

    /** 가장 긴 IO 레인 대기열 길이. */
    public int ioBacklog() { return ioLanes == null ? 0 : ioLanes.maxBacklog(); }

    /** 저장 역압: 레인이 밀려 있으면 이번 틱 저장 제출을 멈춘다. 월드를 내리며 저장하는 동안(forceSave)·백업 기준점 저장 중은 미루지 않는다. */
    public boolean ioBackedUp() { return !forceSave && snapshotSaves.get() == 0 && ioBacklog() >= ioBacklogLimit; }

    // ------------------------------------------------------------------ 틱 붙잡기(백업 기준점·빈 서버 일시정지)

    private final java.util.concurrent.atomic.AtomicInteger tickHolds = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger snapshotSaves = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * 게임 틱을 붙잡는다(on) / 놓는다. 붙잡힌 동안 region 은 작업 큐·지연 작업만 돌리고 게임 틱(Region.tick)은 건너뛴다.
     * 여러 곳(백업·빈 서버 일시정지)이 겹쳐 걸 수 있어 횟수로 센다.
     */
    public void holdTicks(boolean on) {
        if (on) tickHolds.incrementAndGet();
        else if (tickHolds.decrementAndGet() < 0) tickHolds.set(0);
    }

    /** 게임 틱이 붙잡혀 있는가. */
    public boolean ticksHeld() { return tickHolds.get() > 0; }

    /**
     * 틱을 붙잡은 뒤 부른다: 모든 region 이 지금 도는 틱을 끝내면 완료. 붙잡은 뒤 시작하는 region 차례는 틱을 건너뛰므로(runTick 이
     * 작업 큐를 비운 다음에 붙잡힘을 다시 본다) 완료 뒤에는 어느 region 도 게임 틱을 돌지 않는다.
     */
    public CompletableFuture<Void> quiesce() {
        return CompletableFuture.allOf(forEachRegion(r -> { }).toArray(new CompletableFuture[0]));
    }

    /** 종료 기준점 뒤 참: 이 월드에서 새 차원·region 사이 엔티티 이동을 시작하지 않는다(놓지 않는다). */
    private volatile boolean travelFrozen;

    /** 이 월드에서 새 엔티티 이동을 더 시작하지 않는다(내리기·종료 — 놓지 않는다). */
    public void freezeTravel() { travelFrozen = true; }

    /**
     * 종료 저장 기준점(A03-01): 바닐라는 서버 루프가 멈춘 뒤 모든 차원을 저장한다(stopServer → saveAllChunks) — 저장 사이에 틱도 이동도 없다.
     * 여기서도 저장 전에 같은 기준점을 잡는다: 전역·모든 월드 게임 틱을 붙잡고(백업과 같다) 새 엔티티 이동을 막은 뒤, 지금 도는 틱이 끝나고
     * 날아가는 차원 이동(목적 청크 준비 중)이 모두 도착할 때까지 기다리고, 도착이 남긴 작업까지 한 번 더 비운다. 그 뒤에는 어느 엔티티도
     * 두 차원(또는 두 region) 사이에 떠 있지 않으므로 차원마다 차례로 저장해도 복제·소실이 없다. 종료라 놓지 않는다.
     * holdGlobal·quiesceGlobal 은 null 이면 건너뛴다(시험). 마감 안에 다 되면 참.
     */
    public static boolean holdForShutdown(java.util.Collection<World> worlds, Runnable holdGlobal,
                                          java.util.function.Supplier<CompletableFuture<Void>> quiesceGlobal, long deadlineNanos)
            throws InterruptedException {
        if (holdGlobal != null) holdGlobal.run();
        for (World w : worlds) {
            w.holdTicks(true);
            w.freezeTravel();
        }
        List<CompletableFuture<Void>> quiet = new ArrayList<>();
        if (quiesceGlobal != null) quiet.add(quiesceGlobal.get());
        for (World w : worlds) quiet.add(w.quiesce());
        boolean ok = awaitQuietly(quiet, deadlineNanos, "종료 틱 멈춤");
        // 붙잡기 전 마지막 틱에 떠난 이동(차원·같은 레벨 관문): 목적 청크를 읽거나 출구를 만드는 중이면 도착 작업이 아직 없다 — 다 도착할 때까지
        while (true) {
            int flying = 0;
            for (World w : worlds) flying += w.incomingTravels() + w.outgoingTravels();
            if (flying == 0) break;
            if (System.nanoTime() - deadlineNanos >= 0) {
                org.slf4j.LoggerFactory.getLogger(World.class).error("[STOP] 차원 이동 {}건이 마감 안에 도착하지 않았다", flying);
                ok = false;
                break;
            }
            Thread.sleep(5);
        }
        // 도착(그리고 같은 차원 region 넘겨주기)이 남긴 작업까지 — 저장 작업은 그 뒤 차례에 선다
        quiet.clear();
        for (World w : worlds) quiet.add(w.quiesce());
        return awaitQuietly(quiet, deadlineNanos, "종료 큐 비우기") && ok;
    }

    /** 켜면 저장 역압을 무시한다(백업 기준점 저장 — 레인이 밀려 미루면 기준점 상태가 빠진다). 횟수로 센다. */
    public void snapshotSaving(boolean on) {
        if (on) snapshotSaves.incrementAndGet();
        else if (snapshotSaves.decrementAndGet() < 0) snapshotSaves.set(0);
    }

    /**
     * 백업 기준점: 레인마다 marker 를 넣고(지금까지 넣은 저장 뒤), marker 가 돈 레인의 파일은 그 뒤 첫 쓰기 전에 복사된다.
     * 저장소(region·entities·poi)마다 스냅샷을 걸어 돌려준다 — 레인이 없으면(시험) 바로 기준점이다. 반환 future 는 모든 marker 가 돈 뒤 완료.
     */
    public CompletableFuture<Void> beginSnapshot(java.util.function.Function<java.nio.file.Path, java.nio.file.Path> destFor,
                                                 List<org.domi.furnace.server.world.anvil.RegionFileStore.Snapshot> out) {
        var lanes = ioLanes;
        final boolean[] passed = new boolean[lanes == null ? 1 : lanes.laneCount()];
        if (lanes == null) passed[0] = true;
        org.domi.furnace.server.world.anvil.RegionFileStore.CutPassed cut = lanes == null
                ? (rx, rz) -> true
                : (rx, rz) -> { synchronized (passed) { return passed[lanes.laneOfRegion(rx, rz)]; } };
        for (var store : regionFileStores()) out.add(store.beginSnapshot(destFor.apply(store.regionDir()), cut));
        if (lanes == null) return CompletableFuture.completedFuture(null);
        return lanes.markers(i -> () -> { synchronized (passed) { passed[i] = true; } });
    }

    /** 이 월드의 지역 파일 저장소들(region, entities, poi — 없는 것은 뺀다). */
    public List<org.domi.furnace.server.world.anvil.RegionFileStore> regionFileStores() {
        List<org.domi.furnace.server.world.anvil.RegionFileStore> out = new ArrayList<>(3);
        if (worldSource instanceof AnvilWorldSource anvil) {
            out.add(anvil.store());
            if (anvil.entityStore() != null) out.add(anvil.entityStore());
            if (anvil.poiStore() != null) out.add(anvil.poiStore());
        }
        return out;
    }

    private volatile boolean forceSave;

    /** 켜면 저장 역압을 무시한다(월드 내리기 — 남은 dirty 청크를 다음 틱으로 미루면 영영 저장되지 않는다). */
    public void forceSave(boolean on) { this.forceSave = on; }

    private final java.util.concurrent.atomic.AtomicLong lastDiskLog = new java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE);

    /** 자동 저장마다 한 번(여러 region 이 불러도 한 줄): region/ 크기·파일 수·여유 공간, 레인 대기열 최대·대기 시간. IO 풀에서. */
    void logStorageOnce() {
        long now = gameTime();
        long prev = lastDiskLog.get();
        if (prev != Long.MIN_VALUE && now - prev < Math.max(1, autosaveTicks / 2)) return;
        if (!lastDiskLog.compareAndSet(prev, now)) return;
        if (!(worldSource instanceof AnvilWorldSource anvil) || ioPool == null) return;
        long[] lane = ioLanes == null ? new long[2] : ioLanes.drainStats();
        java.nio.file.Path dir = anvil.store().regionDir();
        ioPool.execute(() -> {
            var log = org.slf4j.LoggerFactory.getLogger(World.class);
            long bytes = 0, files = 0, usable = -1;
            try (var st = java.nio.file.Files.list(dir)) {
                for (var p : (Iterable<java.nio.file.Path>) st::iterator) {
                    if (!p.getFileName().toString().endsWith(".mca")) continue;
                    files++;
                    bytes += java.nio.file.Files.size(p);
                }
                usable = java.nio.file.Files.getFileStore(dir).getUsableSpace();
            } catch (java.io.IOException | RuntimeException e) {
                log.debug("[SAVE] 저장소 크기 확인 실패 {}", dir, e);
                return;
            }
            String msg = String.format("[SAVE] %s: region 파일 %d개 %.1f MB, 여유 %.1f GB, IO 레인 최대 대기열 %d · 최대 대기 %.1f ms",
                    dir, files, bytes / 1048576.0, usable / 1073741824.0, lane[0], lane[1] / 1e6);
            if (usable < 1L << 30) log.warn("{} — 디스크 여유 1GB 미만", msg); else log.info(msg);
        });
    }

    /** 그 청크가 든 region 파일의 직렬 IO 레인. */
    public java.util.concurrent.Executor ioLane(int chunkX, int chunkZ) {
        return ioLanes == null ? Runnable::run : ioLanes.lane(chunkX, chunkZ);
    }

    @FunctionalInterface
    interface IoAction { void run() throws Exception; }

    /** 실패하면 saveRetryBackoffMs 만큼 기다리며 재시도. 끝내 실패하면 false. 같은 레인에서 기다리므로 뒤 작업이 앞지르지 않는다. */
    static boolean runWithRetry(IoAction action, String what) {
        long[] backoff = saveRetryBackoffMs;
        for (int attempt = 0; ; attempt++) {
            try {
                action.run();
                return true;
            } catch (Exception e) {
                var log = org.slf4j.LoggerFactory.getLogger(World.class);
                if (attempt >= backoff.length) {
                    log.error("[SAVE] {} 실패({}회 시도) — 포기", what, attempt + 1, e);
                    return false;
                }
                log.warn("[SAVE] {} 실패({}회째) — {}ms 뒤 재시도: {}", what, attempt + 1, backoff[attempt], e.toString());
                try {
                    Thread.sleep(backoff[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("[SAVE] {} 재시도 중 인터럽트 — 포기", what);
                    return false;
                }
            }
        }
    }

    /** 그 청크의 엔티티 묶음을 IO 레인에서 읽는다(없거나 저장소가 없으면 null 로 끝난다). */
    public CompletableFuture<NbtCompound> loadEntitiesAsync(int chunkX, int chunkZ) {
        if (!(worldSource instanceof AnvilWorldSource anvil) || anvil.entityStore() == null) return CompletableFuture.completedFuture(null);
        return CompletableFuture.supplyAsync(() -> {
            try {
                synchronized (anvil.entityLock(chunkX, chunkZ)) {
                    return anvil.readEntities(chunkX, chunkZ);
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(World.class).error("[SAVE] 엔티티 {},{} 읽기 실패", chunkX, chunkZ, e);
                return null;
            }
        }, ioLane(chunkX, chunkZ));
    }

    /** owner 스레드서 만든 엔티티 묶음을 IO 레인서 entities/ 에 쓴다. 엔티티는 자동 저장마다 다시 쓰므로 되돌리기 없이 재시도만. */
    public void saveEntitiesAsync(int chunkX, int chunkZ, NbtCompound root) {
        if (!(worldSource instanceof AnvilWorldSource anvil) || anvil.entityStore() == null) return;
        ioLane(chunkX, chunkZ).execute(() -> {
            boolean ok = runWithRetry(() -> {
                synchronized (anvil.entityLock(chunkX, chunkZ)) {   // 덧붙이기(읽고·쓰기)와 엇갈리지 않게
                    anvil.writeEntities(chunkX, chunkZ, root);
                }
            }, "엔티티 " + chunkX + "," + chunkZ + " 저장");
            if (!ok) saveFailures.incrementAndGet();
        });
    }

    /** owner 스레드서 만든 POI 묶음을 IO 레인서 poi/ 에 쓴다. */
    public void savePoiAsync(int chunkX, int chunkZ, NbtCompound root) {
        if (!(worldSource instanceof AnvilWorldSource anvil) || anvil.poiStore() == null) return;
        ioLane(chunkX, chunkZ).execute(() -> {
            boolean ok = runWithRetry(() -> anvil.writePoi(chunkX, chunkZ, root), "POI " + chunkX + "," + chunkZ + " 저장");
            if (!ok) saveFailures.incrementAndGet();
        });
    }

    /** 디스크의 그 청크 엔티티 묶음에 덧붙인다(읽고 → 더하고 → 쓰기, 그 청크 엔티티 잠금 안에서). */
    public void appendEntitiesAsync(int chunkX, int chunkZ, List<NbtCompound> tags) {
        if (!(worldSource instanceof AnvilWorldSource anvil) || anvil.entityStore() == null) return;
        ioLane(chunkX, chunkZ).execute(() -> {
            synchronized (anvil.entityLock(chunkX, chunkZ)) {
                try {
                    anvil.appendEntities(chunkX, chunkZ, tags);
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(World.class).error("[SAVE] 엔티티 {},{} 덧붙이기 실패", chunkX, chunkZ, e);
                }
            }
        });
    }

    /** ChunkMap.isChunkTracked: 그 플레이어가 그 청크를 받았는가(구독이 전달 완료). */
    public boolean chunkReady(ChunkPos cp, Player p) {
        Subscription sub = subscriptionOf(cp, p);
        return sub != null && sub.ready;
    }

    // 엔티티 id 발급기(플레이어와 같은 것 — FurnaceMain 이 연결 쪽과 같은 것을 건다)
    private volatile org.domi.furnace.server.entity.EntityIdAllocator entityIds = new org.domi.furnace.server.entity.EntityIdAllocator();

    public org.domi.furnace.server.entity.EntityIdAllocator entityIds() { return entityIds; }

    /**
     * 이 월드(차원)에 있는 엔티티의 id → 엔티티 색인(ServerLevel#getEntity(int) 용). 레벨이 엔티티를 넣고 뺄 때 같이 고친다 —
     * region 사이를 넘어가는 중에도 남는다. 어느 스레드에서나 읽을 수 있지만 엔티티 상태는 소유 region 스레드에서만 만진다.
     * 플레이어는 들어 있지 않다(엔티티 저장소에 없다).
     */
    public java.util.concurrent.ConcurrentMap<Integer, org.domi.furnace.server.entity.Entity> entityIndex() { return entityIndex; }

    private final java.util.concurrent.ConcurrentMap<Integer, org.domi.furnace.server.entity.Entity> entityIndex = new java.util.concurrent.ConcurrentHashMap<>();

    /** entityIndex 와 같은 규칙의 UUID → 엔티티 색인(ServerLevel#getEntity(UUID) 용, T-040b). 플레이어는 없다. */
    public java.util.concurrent.ConcurrentMap<java.util.UUID, org.domi.furnace.server.entity.Entity> entityUuidIndex() { return entityUuidIndex; }

    private final java.util.concurrent.ConcurrentMap<java.util.UUID, org.domi.furnace.server.entity.Entity> entityUuidIndex = new java.util.concurrent.ConcurrentHashMap<>();

    public void entityIds(org.domi.furnace.server.entity.EntityIdAllocator a) { this.entityIds = a; }

    public void configurePersistence(int autosaveTicks, int graceTicks) {
        this.autosaveTicks = autosaveTicks;
        this.chunkEvictionGraceTicks = graceTicks;
    }
    public int chunkEvictionGraceTicks() { return chunkEvictionGraceTicks; }
    public int autosaveTicks() { return autosaveTicks; }

    // /save-off 이면 주기 저장을 멈춘다(퇴출 전 저장·종료 저장은 계속 — 바닐라 noSave 도 청크 언로드 저장은 막지 않는다).
    private volatile boolean autosaveEnabled = true;

    public boolean autosaveEnabled() { return autosaveEnabled; }

    public void autosaveEnabled(boolean v) { autosaveEnabled = v; }
    public void worldDir(java.nio.file.Path dir) { this.worldDir = dir; }

    // 이 차원의 저장 뿌리(region·data 의 기준 폴더). 기본 세 차원은 주 월드 폴더(worldDir)와 같고, 추가 월드는 <이름>/ 이다.
    // worldDir 는 서버 공용 데이터(playerdata·stats·advancements·generated 구조물) 자리라 추가 월드도 주 월드 폴더를 가리킨다.
    private volatile java.nio.file.Path levelRoot;

    public void levelRoot(java.nio.file.Path dir) { this.levelRoot = dir; }

    /** 이 차원의 저장 뿌리 — 추가 월드는 <이름>/, 기본 차원은 worldDir. */
    public java.nio.file.Path levelRoot() { var r = levelRoot; return r != null ? r : worldDir; }

    private final org.domi.furnace.server.light.VirtualLights virtualLights = new org.domi.furnace.server.light.VirtualLights();

    /** 이 차원의 가짜 광원(플러그인 빛 API). region 레벨들이 함께 쓴다. */
    public org.domi.furnace.server.light.VirtualLights virtualLights() { return virtualLights; }

    private volatile org.domi.furnace.server.world.raid.RaidStore raidStore;
    private volatile CommandStorage commandStorage;

    /** /data storage 저장소(차원 data 폴더 — 명령은 주 월드 것을 쓴다). 디스크 월드가 아니면 메모리에서만. */
    public CommandStorage commandStorage() {
        var s = commandStorage;
        if (s != null) return s;
        synchronized (this) {
            if (commandStorage == null) {
                commandStorage = new CommandStorage(worldSource instanceof AnvilWorldSource && worldDir != null
                        ? dimensionType.storageDir(levelRoot()).resolve("data") : null);
            }
            return commandStorage;
        }
    }

    /** 습격 저장소(world/data/raids.dat). 디스크 월드면 처음 쓸 때 읽고, 아니면 메모리에서만. */
    public org.domi.furnace.server.world.raid.RaidStore raidStore() {
        var s = raidStore;
        if (s != null) return s;
        synchronized (this) {
            if (raidStore == null) {
                raidStore = worldSource instanceof AnvilWorldSource
                        ? org.domi.furnace.server.world.raid.RaidStore.open(dimensionType.storageDir(levelRoot()),
                                dimensionType.raidsFileId())
                        : org.domi.furnace.server.world.raid.RaidStore.detached();
            }
            return raidStore;
        }
    }
    public java.nio.file.Path worldDir() { return worldDir; }
    public void defaultSpawn(SpawnPoint spawn) {
        this.defaultSpawn = spawn;
        if (spawnChunks) refreshSpawnTicket();   // 바닐라 setDefaultSpawnPos: 옛 START 티켓을 놓고 새 자리에 다시 건다
    }

    // ------------------------------------------------------------------ 스폰 청크(바닐라 ServerLevel.setDefaultSpawnPos 의 START 티켓)

    /** 스폰 청크 티켓을 쓰는 월드인가(주 오버월드만 — 바닐라 prepareLevels 가 오버월드에만 setDefaultSpawnPos 를 부른다). */
    private volatile boolean spawnChunks;

    /** 지금 걸린 START 티켓(없으면 null): 가운데 청크와 거리(게임 규칙 spawnChunkRadius + 1, 레벨 33 − 거리). */
    private volatile SpawnTicket spawnTicket;

    /** 스폰 청크 START 티켓. 거리 d 면 체비셰프 ≤ d 청크가 올라와 있고(FULL), ≤ d − 1 은 블록 틱, ≤ d − 2 는 엔티티 틱(바닐라 레벨 33/32/31). */
    record SpawnTicket(int cx, int cz, int distance) {
        int chebyshev(int x, int z) { return Math.max(Math.abs(x - cx), Math.abs(z - cz)); }
    }

    /**
     * 이 월드에 스폰 청크를 켠다(운영 서버가 주 오버월드에 한 번, 바닐라 MinecraftServer.prepareLevels). 켜는 즉시 지금 스폰 자리에
     * 티켓을 걸고 그 청크를 올린다. 이후 {@link #defaultSpawn(SpawnPoint)}·게임 규칙 변경({@link #refreshSpawnTicket()})이 따라 옮긴다.
     */
    public void enableSpawnChunks() {
        spawnChunks = true;
        refreshSpawnTicket();
    }

    public boolean spawnChunksEnabled() { return spawnChunks; }

    /**
     * 바닐라 ServerLevel.setDefaultSpawnPos 의 티켓 부분(바이트코드 aqu.a(jd, float)): 옛 START 티켓(거리 &gt; 1 이었으면)을 놓고
     * 거리 = spawnChunkRadius + 1 이 1 보다 크면 스폰 청크에 새로 건다. 게임 규칙 spawnChunkRadius 를 바꾸면 바닐라처럼 같은 자리로 다시 부른다.
     * 켜지 않은 월드는 아무것도 안 한다. 어느 스레드에서 불러도 된다.
     */
    public synchronized void refreshSpawnTicket() {
        if (!spawnChunks) return;
        SpawnPoint sp = defaultSpawn;
        int distance = levelServices.gameRuleInt("spawnChunkRadius") + 1;
        SpawnTicket next = distance > 1 ? new SpawnTicket(Math.floorDiv(sp.x(), 16), Math.floorDiv(sp.z(), 16), distance) : null;
        spawnTicket = next;   // 옛 티켓은 그냥 없어진다 — 그 청크는 구독자·다른 티켓이 없으면 퇴출 유예 뒤 내려간다
        if (next == null || unloaded) return;
        for (int dz = -distance; dz <= distance; dz++) {
            for (int dx = -distance; dx <= distance; dx++) {
                ChunkPos cp = new ChunkPos(next.cx() + dx, next.cz() + dz);
                try {
                    runAtChunk(cp, r -> r.loadChunk(cp));
                } catch (RuntimeException e) {   // 닫히는 중
                    log.debug("스폰 청크 {} 올리기 실패", cp, e);
                }
            }
        }
    }

    /** 지금 START 티켓(시험·관측용, 없으면 null). */
    SpawnTicket spawnTicket() { return spawnTicket; }

    private boolean spawnTicketWithin(int cx, int cz, int less) {
        SpawnTicket t = spawnTicket;
        return t != null && t.chebyshev(cx, cz) <= t.distance() - less;
    }

    // ------------------------------------------------------------------ 빈 차원(바닐라 ServerLevel.emptyTime)

    /** 바닐라 emptyTime: 플레이어도 /forceload 청크도 없이 지난 틱 수. 전역 틱이 늘리고, 엔티티가 차원을 넘으면 어느 스레드든 0 으로. */
    private final java.util.concurrent.atomic.AtomicInteger emptyTime = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * 바닐라 ServerLevel.tick: {@code flag = !players.isEmpty() || !getForcedChunks().isEmpty(); if (flag) resetEmptyTime();
     * if (flag || emptyTime++ < 300) { 드래곤 전투·엔티티·블록 엔티티 }}. 전역 틱(서버 틱마다, region 틱보다 먼저)에서 차원마다 부른다.
     * 예약 틱·블록 이벤트·청크 틱은 이와 무관하게 돈다 — 스폰 청크의 중계기·관측기는 계속 돌고 호퍼·움직이는 피스톤은 멈춘다.
     * keepTicking 이면 비어도 flag 를 참으로 본다(CraftBukkit·Paper 기본 {@code flag1 = true || …}, world.tick-when-empty).
     */
    public void tickEmptyTime(boolean keepTicking) {
        boolean occupied = keepTicking;
        if (!occupied) {
            var forced = forcedChunks;
            occupied = forced != null && forced.size() > 0;
        }
        if (!occupied) occupied = !playerIndex().isEmpty();   // 바닐라 !this.players.isEmpty()
        if (occupied) emptyTime.set(0);
        else emptyTime.incrementAndGet();
    }

    /** 바닐라 규칙대로(Bukkit 의 늘 돌기 없이). */
    public void tickEmptyTime() { tickEmptyTime(false); }

    /** 바닐라 ServerLevel.resetEmptyTime(Entity.changeDimension 이 떠나는·닿는 차원 둘 다 부른다): 다음 300 틱은 다시 엔티티가 돈다. */
    public void resetEmptyTime() { emptyTime.set(0); }

    /**
     * 이 차원이 비어 300 틱이 지나 엔티티·블록 엔티티 틱을 쉬는가. 바닐라 {@code emptyTime++ < 300} 의 거짓 = 늘린 뒤 값 &gt; 300.
     * 값 하나에서 유도하므로 resetEmptyTime 과 전역 틱이 겹쳐도 쉼 표시가 남지 않는다.
     */
    public boolean emptyIdle() { return emptyTime.get() > 300; }

    // 월드 시계: 모든 region 스레드·연결이 같은 값을 보도록 단조 시계(nanoTime)에서 계산한다(스레드별 tickCount는 비교 불가).
    // 시간·날씨: 전역 틱(ServerCore.tick)이 진행한다.
    private volatile WorldClock clock = new WorldClock();

    public WorldClock clock() { return clock; }

    public long gameTime() { return clock.gameTime(); }

    public long dayTime() { return clock.dayTime(); }

    /**
     * 접속 중 플레이어 — 서버 전체(모든 차원 월드가 같은 것을 쓴다). 탭 목록은 바닐라처럼 거리·차원과 무관하게 이 집합 전체를
     * 기준으로 한다. 차원별 목록(바닐라 ServerLevel.players, 도착 순)과 그 근접 색인도 여기서 든다({@link PlayerRoster}).
     */
    private volatile PlayerRoster online = new PlayerRoster();
    /** 이 차원 목록(online 이 바뀌면 다시 받는다). */
    private volatile PlayerRoster.Level here;

    /** 이 차원 플레이어 목록(바닐라 ServerLevel.players — 도착 순) + 청크 격자 색인. */
    public PlayerRoster.Level playerIndex() {
        PlayerRoster.Level h = here;
        PlayerRoster o = online;
        if (h == null || h.roster() != o) here = h = o.level(dimensionType.levelKey());
        return h;
    }
    /** 서버 없이(테스트·도구) 쓸 때: 기본값 + 이 월드의 접속자. */
    private volatile org.domi.furnace.server.block.LevelServices levelServices = new org.domi.furnace.server.block.LevelServices.Delegating(
            org.domi.furnace.server.block.LevelServices.DEFAULT) {
        @Override
        public void forEachPlayer(java.util.function.Consumer<Player> c) { World.this.forEachPlayerHere(c); }

        @Override
        public PlayerRoster.Level playerIndex() { return World.this.playerIndex(); }

        @Override
        public long gameTime() { return clock.gameTime(); }

        @Override
        public long dayTime() { return clock.dayTime(); }

        @Override
        public String dimension() { return dimensionType.levelKey(); }

        @Override
        public boolean ultraWarm() { return dimensionType.ultraWarm(); }

        @Override
        public boolean hasSkyLight() { return dimensionType.hasSkyLight(); }

        @Override
        public DimensionType dimensionType() { return dimensionType; }

        @Override
        public org.domi.furnace.server.block.LevelServices.DimensionTravel dimensionTravel() { return travel; }
    };

    /** 블록 레벨이 보는 서버 쪽 정보(ServerCore 가 설치한다). */
    /** 이 월드의 자연 스폰 정책(플러그인 API·설정이 상한·필터를 건다. 아무것도 걸지 않으면 바닐라 상수). */
    private final org.domi.furnace.server.entity.spawn.WorldSpawnPolicy spawnPolicy = new org.domi.furnace.server.entity.spawn.WorldSpawnPolicy();

    public org.domi.furnace.server.entity.spawn.WorldSpawnPolicy spawnPolicy() { return spawnPolicy; }

    public org.domi.furnace.server.block.LevelServices levelServices() { return levelServices; }

    public void levelServices(org.domi.furnace.server.block.LevelServices s) { this.levelServices = s; }

    // ------------------------------------------------------------------ 잠(바닐라 ServerLevel.sleepStatus)

    private final SleepStatus sleepStatus = new SleepStatus();

    public SleepStatus sleepStatus() { return sleepStatus; }

    /** 이 차원 접속자 목록(ServerLevel.players, 도착 순). */
    public java.util.List<Player> playersHere() { return new java.util.ArrayList<>(java.util.Arrays.asList(playerIndex().toArray())); }

    /** ServerLevel.canSleepThroughNights: playersSleepingPercentage ≤ 100. */
    public boolean canSleepThroughNights() { return levelServices.gameRuleInt("playersSleepingPercentage") <= 100; }

    /** ServerLevel.updateSleepingPlayerList: 다시 세어 바뀌었으면 announceSleepStatus. */
    public void updateSleepingPlayerList() {
        var players = playersHere();
        if (!players.isEmpty() && sleepStatus.update(players)) announceSleepStatus(players);
    }

    /** announceSleepStatus: 밤을 건너뛸 수 있는 규칙이면 이 차원 모두에게 액션바(전용 서버는 늘 알린다). */
    private void announceSleepStatus(java.util.List<Player> players) {
        if (!canSleepThroughNights()) return;
        if (!org.domi.furnace.server.config.OperatorOptions.current().announceSleepStatus()) return;   // announce-sleep-status=false
        int pct = levelServices.gameRuleInt("playersSleepingPercentage");
        org.domi.furnace.api.text.Text msg = sleepStatus.areEnoughSleeping(pct)
                ? org.domi.furnace.api.text.Text.translatable("sleep.skipping_night")
                : org.domi.furnace.api.text.Text.translatable("sleep.players_sleeping", sleepStatus.amountSleeping(), sleepStatus.sleepersNeeded(pct));
        for (Player p : players) p.sendSystemMessage(msg, true);
    }

    public void playerOnline(Player p) {
        online.put(p);
        joins.incrementAndGet();
    }

    private final java.util.concurrent.atomic.AtomicLong joins = new java.util.concurrent.atomic.AtomicLong();

    /** 이 월드에 접속자가 올라온 횟수(백업 건너뛰기 판단). */
    public long joinCount() { return joins.get(); }

    public void playerOffline(int eid) {
        online.remove(eid);
        trackers.remove(eid);
    }

    /**
     * 이 차원에 있는 접속자마다(바닐라 ServerLevel.players — 차원을 옮기는 중인 플레이어는 도착한 쪽). 순서도 바닐라처럼 이 차원에
     * 들어온 순서(접속·차원 이동·리스폰 때 끝에 붙는다).
     */
    public void forEachPlayerHere(java.util.function.Consumer<Player> c) { playerIndex().forEach(c); }

    /** 리스폰(바닐라 PlayerList.respawn → addRespawnedPlayer): 이 플레이어를 지금 차원 목록 끝으로 옮긴다. */
    public void playerRespawned(Player p) { online.relist(p); }

    // 엔티티 → 그 엔티티를 지금 화면에 추적 중인 관찰자들. 관찰자 region 이 추적 시작/끝에 갱신하고,
    // 어느 스레드든 피해·사망 같은 엔티티 이벤트를 보낼 때 읽는다(바닐라 ChunkMap.TrackedEntity.seenBy).
    private final ConcurrentHashMap<Integer, Set<Player>> trackers = new ConcurrentHashMap<>();

    public void addTracker(int eid, Player observer) {
        trackers.computeIfAbsent(eid, k -> ConcurrentHashMap.newKeySet()).add(observer);
    }

    public void removeTracker(int eid, Player observer) {
        trackers.computeIfPresent(eid, (k, set) -> {
            set.remove(observer);
            return set.isEmpty() ? null : set;
        });
    }

    /** 이 엔티티를 추적 중인 관찰자 모두에게(자기 자신 제외) 패킷. */
    public void broadcastToTrackers(int eid, Packet pkt) {
        Set<Player> set = trackers.get(eid);
        if (set == null) return;
        for (Player o : set) if (!o.isDisconnected()) o.send(pkt);
    }

    // 플레이어 주변 블록 판정(물·사다리·발밑). 블록 레지스트리가 정해질 때 만든다.
    private volatile PlayerProbe probe;

    public PlayerProbe probe() {
        PlayerProbe pr = probe;
        if (pr == null && blockRegistry != null) probe = pr = new PlayerProbe(blockRegistry);
        return pr;
    }
    /** 탭 목록 기준 접속자 뷰(읽기 전용 순회용). */
    /** 서버 전체 접속자(바닐라 PlayerList.players·Paper getOnlinePlayers 순서 — 접속 순, 리스폰 때 끝으로). 고칠 수 없는 그때의 사본. */
    public java.util.List<Player> onlinePlayers() { return online.onlineInOrder(); }
    /**
     * 서버 전체 접속자 배열 그 자체(복사하지 않는다 — {@link #onlinePlayers} 와 같은 순서). 접속·퇴장·리스폰 때마다 <b>다른 배열</b>로 바뀌므로
     * 부른 쪽은 배열 동일성(==)으로 "접속자 집합이 그대로인가"를 알 수 있다. 읽기 전용: 고치면 안 된다.
     * (끊김 표시는 배열을 바꾸지 않는다 — {@link Player#rosterEpoch} 가 오른다.)
     */
    public Player[] onlineArray() { return online.array; }
    public Player onlinePlayer(int eid) { return online.map.get(eid); }
    public SpawnPoint defaultSpawn() { return defaultSpawn; }

    /** 클라이언트가 보낸 아이템 id가 유효한가. 레지스트리가 없으면(테스트) 양수만 확인한다. */
    public boolean isValidItem(int itemId) {
        return itemRegistry == null ? itemId > 0 : itemRegistry.isValid(itemId);
    }

    public void setRegistries(BlockRegistry blocks, ItemRegistry items) {
        this.blockRegistry = blocks;
        this.itemRegistry = items;
    }

    public BlockRegistry blockRegistry() { return blockRegistry; }

    public ItemRegistry itemRegistry() { return itemRegistry; }

    /** 아이템 id → 설치할 블록 default state id. 블록 아닌 아이템/미설정이면 0(air=설치 안 함). */
    public int itemToBlockState(int itemId) {
        if (blockRegistry == null || itemRegistry == null) return 0;
        return blockRegistry.stateIdOrAir(itemRegistry.nameOf(itemId));
    }

    private final java.util.concurrent.atomic.AtomicLong blockingLoads = new java.util.concurrent.atomic.AtomicLong();

    void countBlockingLoad() { blockingLoads.incrementAndGet(); }

    /** 이 월드에서 {@link Region#chunkAtBlocking} 이 실제로 동기 생성·디스크 읽기를 한 횟수(시험 계측). */
    public long blockingLoads() { return blockingLoads.get(); }

    /**
     * 외부 스레드(플러그인·콘솔·시험)용: 청크를 비동기 로드하고 로드가 끝날 때까지 이 스레드에서 기다린다.
     * region 스레드에서 부르면 자기 인박스를 기다리게 되므로 금지한다.
     */
    public ChunkColumn awaitChunk(ChunkPos cp) {
        // 어느 region 워커든(다른 region·다른 차원 포함) 막으면 규칙 6 위반이고, 워커가 하나면 영구 교착이다
        if (Regionizer.isRegionWorkerThread()) throw new IllegalStateException("region 스레드에서 awaitChunk 금지: " + cp);
        return submitAtChunk(cp, r -> r.loadChunk(cp)).thenCompose(f -> f).join();
    }

    /**
     * 청크의 canonical 불변 스냅샷을 비동기로 반환. 청크 소유 region 스레드서
     * get-or-load 후 스냅샷을 떠 — 단일 소유 보장(교차region 로더도 같은 청크를 본다).
     */
    public CompletableFuture<ChunkSnapshot> snapshotChunk(ChunkPos cp) {
        return submitAtChunk(cp, r -> r.loadChunk(cp))
                .thenCompose(f -> f)
                .thenCompose(col -> submitAtChunk(cp, r -> {
                    // 캐시는 청크가 바뀔 때마다(ChunkColumn.touch) 비워지므로 저장 여부(dirty)와 무관하게 재사용할 수 있다.
                    ChunkSnapshot cached = col.cachedSnapshot();
                    if (cached != null) return cached;
                    ChunkSnapshot snapshot = ChunkSnapshot.of(cp.x(), cp.z(), col);
                    col.cachedSnapshot(snapshot);
                    return snapshot;
                }));
    }

    // ------------------------------------------------------------------ 청크 재생성(W3, 불만 표 41번)

    /**
     * 그 청크를 이 월드의 생성기로 처음부터 다시 만들어 바꾼다. 생성은 생성 풀에서, 교체·재전송은 그 청크를 가진 region 스레드에서
     * 한다({@link Region#replaceRegenerated}). 노이즈 생성기는 공유 장식 상태를 쓰지 않는 독립 경로(5×5 pristine + 둘레 9개 원점 장식)로
     * 만든다 — 같은 시드의 새 월드에서 그 청크가 처음 만들어질 때와 같은 블록이고, 이웃 청크는 건드리지 않는다.
     * 로드돼 있지 않으면 먼저 불러온다. 내린 월드면 false.
     */
    public CompletableFuture<Boolean> regenerateChunk(int cx, int cz, boolean removeEntities, boolean generationEntities) {
        if (unloaded) return CompletableFuture.completedFuture(false);
        ChunkPos cp = new ChunkPos(cx, cz);
        CompletableFuture<ChunkColumn> fresh;
        // 생성은 한 번에 하나씩(독립 경로 하나가 이미 생성 풀을 병렬로 쓴다) — /worlds regen 8 의 289개가 생성 대기 풀을 다 붙잡아
        // 플레이어 청크 생성을 밀어내지 않게. 교체·재전송은 각자 region 스레드에서 이어 간다
        synchronized (regenLock) {
            fresh = regenTail.handle((v, e) -> null).thenApplyAsync(v -> regeneratedColumn(cx, cz), genPool);
            regenTail = fresh.handle((c, e) -> null);   // 만든 청크를 다음 재생성까지 붙들지 않게
        }
        return fresh.thenCompose(col -> installRegenerated(cp, col, removeEntities, generationEntities, 3))
                .exceptionally(e -> {
                    Throwable c = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
                    if (c instanceof java.util.concurrent.RejectedExecutionException) return false;   // 서버가 멈추는 중
                    throw e instanceof java.util.concurrent.CompletionException ce ? ce : new java.util.concurrent.CompletionException(e);
                });
    }

    private final Object regenLock = new Object();
    private CompletableFuture<?> regenTail = CompletableFuture.completedFuture(null);

    /** 새 청크 하나(생성 풀에서). 블록 엔티티·POI 는 디스크에서 올릴 때와 같게 채운다. 빛은 교체 때 옛 빛에서 고친다. */
    ChunkColumn regeneratedColumn(int cx, int cz) {
        WorldSource src = worldSource instanceof org.domi.furnace.server.world.gen.AnvilWorldSource a ? a.base() : worldSource;
        ChunkColumn col;
        if (src instanceof org.domi.furnace.server.world.gen.NoiseWorldSource n) {
            col = n.generateIsolated(cx, cz);
            // 생성 엔티티를 넣을 때만 쓴다(교체가 지운다). 평지 생성기(FlatLevelSource.spawnOriginalMobs)는 동물을 놓지 않는다
            if (!n.isFlat()) col.freshlyGenerated(true);
        } else {
            col = src.generate(cx, cz);
        }
        col.poi(org.domi.furnace.server.world.poi.PoiManager.restore(col, cx, cz, null));
        col.promotePendingBlockEntities(cx, cz, true);
        col.light(null);
        col.lightPrecomputed(false);
        return col;
    }

    private CompletableFuture<Boolean> installRegenerated(ChunkPos cp, ChunkColumn col, boolean removeEntities, boolean generationEntities,
                                                          int attempts) {
        if (unloaded) return CompletableFuture.completedFuture(false);
        return submitAtChunk(cp, r -> r.loadChunk(cp)).thenCompose(f -> f)
                .thenCompose(loaded -> {   // 디스크 엔티티를 읽는 중이면 끝날 때까지(교체가 그 결과를 이어받게)
                    var pending = loaded == null ? null : loaded.pendingEntityLoad();
                    return pending == null ? CompletableFuture.completedFuture(loaded) : pending.thenApply(v -> loaded);
                })
                .thenCompose(loaded -> submitAtChunk(cp, r -> r.replaceRegenerated(cp, col, removeEntities, generationEntities)))
                .thenCompose(ok -> {
                    if (ok != null) return CompletableFuture.completedFuture(ok);
                    // 불러온 뒤 교체 전에 내려갔거나 주인이 바뀌었다 — 다시 불러와 한 번 더
                    return attempts > 0 ? installRegenerated(cp, col, removeEntities, generationEntities, attempts - 1)
                            : CompletableFuture.completedFuture(false);
                });
    }

    /** 본문 직렬화용 스냅샷을 뜬 횟수(테스트가 "스냅샷이 떠졌다"를 기다리는 신호). */
    final java.util.concurrent.atomic.AtomicInteger bodySnapshots = new java.util.concurrent.atomic.AtomicInteger();

    public CompletableFuture<byte[]> serializedChunkBody(ChunkPos cp) {
        return serializedChunkBody(cp, false);
    }

    /**
     * inlineOnOwner: 지금 스레드가 그 청크의 주인 region 이면 작업 큐(다음 틱에 비워짐)를 거치지 않고 바로 돈다 —
     * 이미 로드된 청크의 본문이 같은 틱에 나간다(auto 전송). 다른 스레드에서 부르면 예전처럼 주인 region 에 넣는다.
     */
    public CompletableFuture<byte[]> serializedChunkBody(ChunkPos cp, boolean inlineOnOwner) {
        return atChunk(cp, inlineOnOwner, r -> r.loadChunk(cp))
                .thenCompose(f -> f)
                // 컬럼 접근(dirty/캐시/스냅샷)은 항상 주인 region 스레드서 — 로드 완료 스레드(IO 풀 등)서 돌지 않게 한다.
                .thenCompose(col -> atChunk(cp, inlineOnOwner, r -> {
                    // 캐시는 청크가 바뀔 때마다(ChunkColumn.touch) 비워진다 — 새로 생성돼 아직 저장 전(dirty)인 청크도 캐시한다.
                    byte[] cached = col.cachedSerializedBody();
                    if (cached != null) return CompletableFuture.<byte[]>completedFuture(cached);
                    // 같은 버전이 이미 직렬화 중이면 그 결과를 같이 받는다. 필드 읽기·쓰기·touch 가 모두 주인 region 스레드
                    // 태스크라, 남아 있으면 지금 modCount 와 같은 버전이고 아직 끝나지 않은 것이다.
                    CompletableFuture<byte[]> inflight = col.inflightSerializedBody();
                    if (inflight != null) {
                        // 미리 직렬화로 걸린 일에 실제로 기다리는 요청이 붙었다 — 이제 낡아도 건너뛰지 않는다(예전처럼 그 결과를 받는다).
                        // 진행 표시를 내리기(touch) 전에 이 쓰기가 일어나므로 풀은 "진행 표시가 바뀜"을 본 뒤 이 값을 보면 반드시 본다.
                        if (inflight instanceof BodyJob job) job.speculative = false;
                        return inflight;
                    }
                    return startBodySerialization(cp, col, false);
                }).thenCompose(f -> f));
    }

    /** 본문 직렬화 진행 표시. speculative = auto 전송의 미리 직렬화가 건 일이라 결과를 기다리는 요청이 아직 없다(T-011). */
    static final class BodyJob extends CompletableFuture<byte[]> {
        volatile boolean speculative;

        BodyJob(boolean speculative) { this.speculative = speculative; }
    }

    /** 미리 직렬화 일이 풀에서 시작하기 전에 낡아(청크가 바뀌어) 건너뛴 수 — 결과를 쓸 곳이 없던 일(측정·시험 신호, T-011). */
    final java.util.concurrent.atomic.AtomicInteger staleBodySkips = new java.util.concurrent.atomic.AtomicInteger();
    /** auto 전송이 이웃 청크가 올라오는 중이라 본문 직렬화를 미룬 수(측정·시험 신호, T-011). */
    final java.util.concurrent.atomic.AtomicInteger bodyLightDeferrals = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * 주인 region 스레드에서: 스냅샷을 뜨고 풀에 직렬화를 건다(캐시·진행 중 본문이 없을 때만 부른다). 결과는 주인 스레드 태스크에서
     * 진행 표시를 내리고, 그 사이 청크가 안 바뀌었으면 캐시에 넣은 뒤 완료한다.
     * speculative(미리 직렬화, 기다리는 요청 없음)인 일은 풀에서 시작할 때 이미 낡았으면 직렬화하지 않는다 — 그 결과는 어차피 캐시에 들어가지 않고
     * 받을 곳도 없다(T-011: 이웃이 올라올 때마다 비워진 본문을 풀이 계속 다시 만들던 낭비).
     */
    private CompletableFuture<byte[]> startBodySerialization(ChunkPos cp, ChunkColumn col, boolean speculative) {
        // 스냅샷(약 390KB)은 본문 직렬화에만 쓰므로 컬럼에 붙들어 두지 않는다 — 본문 캐시가 그 뒤를 대신한다.
        ChunkSnapshot snapshot = ChunkSnapshot.of(cp.x(), cp.z(), col);
        bodySnapshots.incrementAndGet();
        // 스냅샷과 같은 동기 구간에서 수정 카운터를 잡는다 — 풀 직렬화가 도는 동안 편집이 끼면
        // 카운터가 달라져 낡은 본문을 캐시에 넣지 않는다.
        long version = col.modCount();
        BodyJob f = new BodyJob(speculative);
        col.inflightSerializedBody(f);
        // 결과(성공·실패)는 주인 스레드 태스크 안에서 진행 표시를 내린 뒤 완료한다 — 그 뒤 요청이 끝난(실패한)
        // future 를 물려받지 않는다. 캐시 쓰기도 같은 태스크라 내림과 캐시 사이에 틈이 없다.
        final long[] traceSer = ChunkTrace.ON ? new long[2] : null;
        if (ChunkTrace.ON) traceSer[0] = System.nanoTime();
        chunkPool.submit(() -> {
            // 읽는 순서가 중요하다: 진행 표시 먼저, speculative 나중(BodyJob 주석). 진행 표시가 그대로면 청크가 안 바뀌었다.
            if (col.inflightSerializedBody() != f && f.speculative) {
                staleBodySkips.incrementAndGet();
                if (ChunkTrace.ON) ChunkTrace.value("batch.ser.staleskip", 1);
                return null;
            }
            long ts = ChunkTrace.ON ? System.nanoTime() : 0L;
            byte[] out = serializeChunkBody(snapshot);
            if (ChunkTrace.ON) { traceSer[1] = System.nanoTime(); ChunkTrace.nanos("ser", traceSer[1] - ts); ChunkTrace.nanos("hop.serpool", ts - traceSer[0]); }
            return out;
        }).handle((body, ex) -> {
            if (ex == null && body == null) {   // 건너뛴 낡은 미리 직렬화 — 진행 표시는 이미 내려갔고 기다리는 요청이 없다
                f.complete(null);
                return null;
            }
            return submitAtChunk(cp, r2 -> {
                if (ChunkTrace.ON && traceSer[1] != 0L) ChunkTrace.nanos("hop.ser", System.nanoTime() - traceSer[1]);
                if (col.inflightSerializedBody() == f) col.inflightSerializedBody(null);
                if (ex == null && col.modCount() == version) col.cachedSerializedBody(body);
                return body;
            }).whenComplete((body2, ex2) -> {
                if (ex != null) f.completeExceptionally(ex);
                else if (ex2 != null) f.completeExceptionally(ex2);
                else f.complete(body);
            });
        });
        return f;
    }

    /** auto 전송이 본문을 기다려 주는 최대 횟수(보내려던 틱 수). 넘으면 바닐라처럼 그 자리에서 직렬화한다. */
    static final int MAX_BODY_WAITS = 2;
    /** chunkBodyReadyOrPrefetch 결과: 아직 없음(직렬화를 걸었다) / 있음 / 지금 이 스레드에서 직렬화해 만들었음. */
    public static final int BODY_PENDING = 0, BODY_READY = 1, BODY_SYNC = 2;

    /**
     * auto 전송(그 청크 주인 region 스레드에서): 본문이 이미 준비돼 있어 배치에 넣으면 Start·청크·Finished 가 같은 틱에 나가는가.
     * 준비 안 됐으면 직렬화를 걸어 두고(이미 진행 중이면 그대로) BODY_PENDING — 끝나면 캐시에 들어가 다음 틱에 BODY_READY 가 된다.
     * 바닐라 클라이언트는 Start~Finished 시간을 배치 크기로 나눠 자기 처리 속도로 여기고 틱당 원하는 청크 수를 정한다
     * (ChunkBatchSizeCalculator). 그 사이에 직렬화 왕복(작업 큐가 다음 틱에 비워진다)이 끼면 클라이언트가 스스로 느리다고 보고 속도를 1 아래로 낮춘다.
     * sendingNow(이번 배치에 넣으려는 청크)인데 이미 MAX_BODY_WAITS 틱을 기다렸고 allowSync 면 — 레드스톤처럼 매 틱 블록이 바뀌어 캐시가
     * 보내기 직전에 비워지는 청크 — 바닐라처럼 이 스레드에서 바로 직렬화해 캐시에 넣고 BODY_SYNC(영영 못 보내지 않게). allowSync 는 호출자가
     * 틱당 상한으로 준다(이런 청크가 많을 때 한 틱에 몰리지 않게).
     */
    public int chunkBodyReadyOrPrefetch(ChunkPos cp, boolean sendingNow, boolean allowSync) {
        return chunkBodyReadyOrPrefetch(cp, sendingNow, allowSync, null);
    }

    /** upcoming: 이 플레이어가 곧 받을(아직 안 올라온 것은 곧 올릴) 청크 — 그 안의 이웃이 아직 없으면 올라오는 중과 같이 본다(T-011). */
    public int chunkBodyReadyOrPrefetch(ChunkPos cp, boolean sendingNow, boolean allowSync, java.util.Set<ChunkPos> upcoming) {
        return chunkBodyReadyOrPrefetch(cp, sendingNow, allowSync, upcoming, NO_TICK);
    }

    /** tick 을 모를 때(시험·도구): 이웃 기다림을 부를 때마다 센다. */
    static final long NO_TICK = Long.MIN_VALUE;

    /**
     * tick: 부르는 region 의 틱 번호. 이웃 기다림(lightWaits)은 틱당 한 번만 세고, 앞 틱에 세지 않았으면(대기열에서 빠졌다 돌아옴 등)
     * 1 부터 다시 센다 — 상한 MAX_LIGHT_WAITS 는 "연속으로 보내려다 미룬 틱 수"다.
     */
    int chunkBodyReadyOrPrefetch(ChunkPos cp, boolean sendingNow, boolean allowSync, java.util.Set<ChunkPos> upcoming, long tick) {
        ChunkColumn col = loadedChunk(cp.x(), cp.z());
        if (col == null) return BODY_READY;
        if (col.cachedSerializedBody() != null) {
            if (sendingNow) {
                col.bodyWaits(0);
                col.lightWaits(0);
            }
            return BODY_READY;
        }
        // T-011: 이웃(8칸)이 올라오는 중이면 아직 직렬화하지 않는다. 이웃이 올라와 빛을 붙이면(LevelLight.attachPrecomputed) 가장자리 빛이 이 청크로
        // 번져 본문 캐시와 진행 중 직렬화가 버려진다 — 8명 냉 접속에서 버린 원인의 98.8%, 청크당 직렬화 3.6번(O00-05 계수).
        // 바닐라도 청크를 반경 1 이웃이 모두 FULL 이 된 뒤에야 보낸다(ChunkMap.prepareTickingChunk → onChunkReadyToSend).
        // 이 플레이어의 대기열에 있어 곧 올릴 이웃(upcoming)도 같이 기다린다. 올라오는 중도 대기열도 아닌 이웃(시야 밖)은 기다리지 않는다.
        // 보내는 본문은 여전히 보낼 때의 청크 상태다(바뀌면 캐시가 비워진다) — 바뀌는 것은 직렬화·전송 시점뿐이다.
        // 보내려는 청크는 MAX_LIGHT_WAITS 번까지만 미룬다 — 이웃 생성이 오래 걸리면 예전처럼 보낸다.
        if ((!sendingNow || col.lightWaits() < MAX_LIGHT_WAITS) && neighborLoading(cp.x(), cp.z(), upcoming)) {
            if (sendingNow) countLightWait(col, tick);
            bodyLightDeferrals.incrementAndGet();
            if (ChunkTrace.ON) ChunkTrace.value(sendingNow ? "batch.lightwait.send" : "batch.lightwait.prefetch", 1);
            return BODY_PENDING;
        }
        if (sendingNow && allowSync && col.bodyWaits() >= MAX_BODY_WAITS) {
            if (ChunkTrace.ON) ChunkTrace.value("batch.syncser", 1);
            col.bodyWaits(0);
            col.lightWaits(0);
            try {
                bodySnapshots.incrementAndGet();
                col.cachedSerializedBody(serializeChunkBody(ChunkSnapshot.of(cp.x(), cp.z(), col)));
            } catch (RuntimeException e) {
                // 실패하면 캐시 없이 보낸다 — 배치 안의 예전 경로(serializedChunkBody)가 다시 해 보고 실패를 기록한다
                org.slf4j.LoggerFactory.getLogger(World.class).warn("[CHUNK] 본문 직렬화 실패 {}", cp, e);
            }
            return BODY_SYNC;
        }
        if (sendingNow && col.bodyWaits() < MAX_BODY_WAITS) col.bodyWaits(col.bodyWaits() + 1);
        if (col.inflightSerializedBody() == null) {
            Region owner = regionizer.ownerOf(cp.x(), cp.z());
            // 미리 직렬화: 결과는 캐시로만 받는다(배치는 다음 틱에 캐시를 본다). 기다리는 요청이 없으니 낡으면 풀이 건너뛴다.
            if (owner != null && owner.isOwnerThread()) startBodySerialization(cp, col, true);
            else serializedChunkBody(cp, true);
        }
        return BODY_PENDING;
    }

    private static void countLightWait(ChunkColumn col, long tick) {
        if (tick == NO_TICK) {
            col.lightWaits(col.lightWaits() + 1);
            return;
        }
        long last = col.lightWaitTick();
        if (last == tick) return;   // 이 틱에 이미 셌다(같은 청크를 기다리는 다른 플레이어)
        col.lightWaits(last == tick - 1 ? col.lightWaits() + 1 : 1);
        col.lightWaitTick(tick);
    }

    /** 이웃 청크가 올라오는 중이면 보내려는 청크의 직렬화를 미루는 최대 횟수(연속으로 보내려던 틱 수, T-011). 넘으면 예전처럼 직렬화한다. */
    static final int MAX_LIGHT_WAITS = 20;

    /**
     * (cx, cz) 를 둘러싼 8칸 중 지금 올라오는(디스크 읽기·생성 중) 청크, 또는 upcoming 에 있는데 아직 안 올라온 청크가 있는가.
     * 빛은 15칸 안에서만 번지므로 반경 1 밖은 이 청크 빛을 못 바꾼다(바닐라 sectionLightChanged 표시 상자 ±1 칸 포함).
     */
    boolean neighborLoading(int cx, int cz, java.util.Set<ChunkPos> upcoming) {
        if (loading.isEmpty() && (upcoming == null || upcoming.isEmpty())) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx | dz) == 0) continue;
                long k = mapKey(Regionizer.chunkKey(cx + dx, cz + dz));
                if (loading.containsKey(k)) return true;
                // 대기열 조회용 ChunkPos 는 이웃이 아직 안 올라왔을 때만 만든다(대부분은 올라와 있어 할당 없음)
                if (upcoming != null && !chunks.containsKey(k) && upcoming.contains(new ChunkPos(cx + dx, cz + dz))) return true;
            }
        }
        return false;
    }

    /** inline 이고 지금 스레드가 그 청크 주인 region 이면 바로 돌리고, 아니면 submitAtChunk. */
    private <T> CompletableFuture<T> atChunk(ChunkPos cp, boolean inline, java.util.function.Function<Region, T> task) {
        if (inline) {
            Region r = regionizer.ownerOf(cp.x(), cp.z());
            if (r != null && r.isOwnerThread()) {
                try {
                    return CompletableFuture.completedFuture(task.apply(r));
                } catch (Throwable t) {
                    return CompletableFuture.failedFuture(t);
                }
            }
        }
        return submitAtChunk(cp, task);
    }

    /** 시험용: 본문 직렬화 실패 주입(null 이면 없음). */
    volatile Runnable serializeFaultHook;
    /** 시험 전용: 청크 본문을 플레이어에게 보내기 직전에 (좌표, 본문)으로 불린다(본문이 준비돼 있으면 주인 region 스레드에서). 운영에서는 null. */
    volatile java.util.function.BiConsumer<ChunkPos, byte[]> chunkSendTap;

    private byte[] serializeChunkBody(ChunkSnapshot snap) {
        Runnable hook = serializeFaultHook;
        if (hook != null) hook.run();
        ByteBuf body = Unpooled.buffer();
        try {
            DimensionType t = dimensionType;
            ChunkSerializer.serialize(body, snap, blockDirectBits, biomeDirectBits, t.minY(), t.hasSkyLight());
            byte[] out = new byte[body.readableBytes()];
            body.getBytes(0, out);
            // 이 풀 스레드에서 한 번만 압축한다 — 여러 플레이어의 이벤트 루프가 같은 본문을 각자 압축하지 않게(M12)
            org.domi.furnace.server.net.io.SharedChunkFrames.precompress(out);
            return out;
        } finally {
            if (body.refCnt() > 0) body.release();
        }
    }

    /** 지금 스레드가 돌리는 region 이 이 청크의 주인일 때 그 region(테스트·도구 호환: RegionPos 는 청크 좌표). */
    public Region regionAt(RegionPos chunk) {
        Region r = regionizer.ownerOf(chunk.x(), chunk.z());
        if (r == null || !r.isOwnerThread()) {
            throw new IllegalStateException("청크 " + chunk + " 의 주인 region 스레드가 아니다: " + Thread.currentThread().getName());
        }
        return r;
    }

    /** 그 청크의 주인 region 스레드서 호출 중인가(다른 region 의 청크를 몰래 읽지 않기 위해). */
    public boolean ownsChunkOnCurrentThread(int cx, int cz) {
        Region r = regionizer.ownerOf(cx, cz);
        return r != null && r.isOwnerThread();
    }

    /** 디버그 소유권 가드: 그 region 을 돌리는 스레드서 호출 중인지. */
    public void assertOwns(Region r) {
        assert r.isOwnerThread() : "region #" + r.id() + " 를 비소유 스레드서 접근: " + Thread.currentThread().getName();
    }

    /**
     * 플레이어가 청크를 로드 — 블록 변경 수신 등록. 반환된 구독은 아직 ready=false이므로 청크 패킷을 보낸 뒤
     * {@link #chunkDelivered}를 불러야 보류된 변경이 청크 뒤에 흘러간다. 같은 청크를 다시 구독하면 이전 구독은 무효가 된다.
     */
    public Subscription subscribeChunk(ChunkPos cp, Player p) {
        Subscription sub = new Subscription();
        // compute는 키 단위로 원자적이라 "마지막 구독자 제거로 맵이 비어 삭제되는 사이에 put" 경합이 없다.
        chunkSubscribers.compute(cp, (k, m) -> {
            if (m == null) m = new ConcurrentHashMap<>();
            m.put(p, sub);
            return m;
        });
        return sub;
    }
    /**
     * 그 플레이어가 지금도 그 청크를 구독하고 있을 때만 새 구독으로 바꾼다(청크 다시 보내기). 그 사이 다른 스레드에서 구독을 풀었으면
     * 다시 넣지 않고 null — 떠난 플레이어의 구독이 남아 청크가 내려가지 않는 일을 막는다.
     */
    public Subscription resubscribeIfPresent(ChunkPos cp, Player p) {
        Subscription sub = new Subscription();
        boolean[] done = new boolean[1];
        chunkSubscribers.computeIfPresent(cp, (k, m) -> {
            if (m.containsKey(p)) {
                m.put(p, sub);
                done[0] = true;
            }
            return m;
        });
        return done[0] ? sub : null;
    }

    public void unsubscribeChunk(ChunkPos cp, Player p) {
        chunkSubscribers.computeIfPresent(cp, (k, m) -> {
            m.remove(p);
            return m.isEmpty() ? null : m;
        });
    }

    /** 청크 구독자(블록 변경 브로드캐스트 대상). 비었으면 빈 Set. */
    public Set<Player> subscribers(ChunkPos cp) {
        ConcurrentHashMap<Player, Subscription> s = chunkSubscribers.get(cp);
        return s == null ? Set.of() : s.keySet();
    }

    /** 현재 유효한 구독(없으면 null). 구독이 재생성됐으면 최신 것을 돌려준다. */
    public Subscription subscriptionOf(ChunkPos cp, Player p) {
        ConcurrentHashMap<Player, Subscription> s = chunkSubscribers.get(cp);
        return s == null ? null : s.get(p);
    }

    /** sub가 아직 유효한 구독인가(전달 도중 언로드·재구독됐으면 false → 그 청크는 버린다). */
    public boolean isSubscribed(ChunkPos cp, Player p, Subscription sub) {
        return sub != null && subscriptionOf(cp, p) == sub;
    }

    /**
     * 청크 패킷을 채널에 넘긴 직후 호출 — 소유 region 스레드로 돌아가 구독을 ready로 바꾸고 보류된 변경을 청크 뒤에 보낸다.
     * (청크 패킷이 채널 큐에 먼저 들어갔으므로 이후 enqueue되는 변경 패킷은 항상 청크보다 늦게 나간다.)
     */
    public void chunkDelivered(ChunkPos cp, Player p, Subscription sub) {
        runAtChunk(cp, r -> {
            if (!isSubscribed(cp, p, sub)) return;   // 전달 중 언로드/재구독됨
            sub.ready = true;
            for (Packet u : sub.pending) p.send(u);
            sub.pending.clear();
        });
    }

    /** 청크 소유 region 스레드서 호출 — 전달 완료(ready)된 구독자에겐 즉시, 아닌 구독자에겐 청크 뒤로 보류. */
    public void broadcastBlockUpdate(ChunkPos cp, Packet pkt) {
        ConcurrentHashMap<Player, Subscription> s = chunkSubscribers.get(cp);
        if (s == null) return;
        for (Map.Entry<Player, Subscription> e : s.entrySet()) {
            Subscription sub = e.getValue();
            if (sub.ready) e.getKey().send(pkt);
            else sub.pending.add(pkt);
        }
    }

    /**
     * 빛 갱신(ChunkHolder.broadcastChanges → ChunkMap.getPlayers(pos, true)): 그 청크를 받은(isChunkTracked = 구독 && 전송 끝) 플레이어 중
     * 둘레 8칸 중 하나라도 받지 않은 사람에게만. 안쪽 청크는 클라이언트가 스스로 빛을 다시 계산한다. 아직 청크를 못 받은 구독자는 건너뛴다
     * (그 빛은 청크 패킷에 들어간다).
     */
    public void broadcastLightUpdate(ChunkPos cp, Packet pkt) {
        ConcurrentHashMap<Player, Subscription> s = chunkSubscribers.get(cp);
        if (s == null) return;
        for (Map.Entry<Player, Subscription> e : s.entrySet()) {
            if (e.getValue().ready && onTrackedBorder(cp, e.getKey())) e.getKey().send(pkt);
        }
    }

    /** ChunkMap.isChunkOnTrackedBorder: 둘레 8칸 중 그 플레이어가 받지 않은(구독 없음 또는 전송 전) 청크가 있다. */
    boolean onTrackedBorder(ChunkPos cp, Player p) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                ConcurrentHashMap<Player, Subscription> n = chunkSubscribers.get(new ChunkPos(cp.x() + dx, cp.z() + dz));
                Subscription sub = n == null ? null : n.get(p);
                if (sub == null || !sub.ready) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 청크 티켓(바닐라 TicketType.PORTAL·DRAGON)

    /** 청크 키 → 이 gameTime 까지는 구독자가 없어도 내리지 않는다. */
    private final ConcurrentHashMap<Long, Long> tickets = new ConcurrentHashMap<>();

    /**
     * addRegionTicket: (cx, cz) 둘레 반경 radius 청크를 ticks 동안 올려 두고(없으면 올린다) 내리지 않는다. 바닐라 포털 티켓은
     * 거리 3(레벨 30 → 반경 3 청크 로드)·300틱, 드래곤은 (0,0) 거리 9. 어느 스레드에서 불러도 된다.
     */
    public void addTicket(int cx, int cz, int radius, long ticks) {
        long until = gameTime() + ticks;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                ChunkPos cp = new ChunkPos(cx + dx, cz + dz);
                Long prev = tickets.merge(Regionizer.chunkKey(cp.x(), cp.z()), until, Math::max);
                if (prev != null && prev.longValue() == until) runAtChunk(cp, r -> r.loadChunk(cp));   // 새로 걸렸거나 늘었다 — 올려 둔다(이미 있으면 그대로)
            }
        }
        markEntityTicking(cx, cz, radius - 2, until);
        markBlockTicking(cx, cz, radius - 1, until);
    }

    /**
     * 티켓 레벨 33 − 거리 의 블록 틱 범위(TickingTracker 레벨 32 이하 — 예약 틱·블록 이벤트·블록 엔티티): 가운데에서 거리 − 1 청크 안.
     * 포털(3)은 반경 2, 드래곤(9)은 반경 8, 순간 이동 뒤 티켓(1)은 그 청크만.
     */
    private final ConcurrentHashMap<Long, Long> blockTickets = new ConcurrentHashMap<>();

    private void markBlockTicking(int cx, int cz, int radius, long until) {
        sweepExpired(blockTickets, blockSweepAt);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) blockTickets.merge(Regionizer.chunkKey(cx + dx, cz + dz), until, Math::max);
        }
    }

    /** 다음에 지난 항목을 쓸 크기. 조회될 때만 지우면 묻지 않는 청크(블록 엔티티·예약 없는 순간 이동 도착지)의 키가 쌓인다. */
    private final java.util.concurrent.atomic.AtomicInteger blockSweepAt = new java.util.concurrent.atomic.AtomicInteger(256),
            entitySweepAt = new java.util.concurrent.atomic.AtomicInteger(256);

    /** 크기가 기준을 넘으면 지난 항목을 지우고 기준을 남은 크기의 두 배(최소 256)로 — 한 항목당 상수 비용. */
    private void sweepExpired(ConcurrentHashMap<Long, Long> map, java.util.concurrent.atomic.AtomicInteger at) {
        if (map.size() < at.get()) return;
        long now = gameTime();
        map.values().removeIf(v -> v < now);
        at.set(Math.max(256, map.size() * 2));
    }

    /** 그 청크에 블록 틱 단계의 티켓이 살아 있는가(지난 것은 지운다). 강제 로드·플러그인 티켓은 따로 본다. */
    boolean blockTicketed(int cx, int cz) {
        if (spawnTicketWithin(cx, cz, 1)) return true;   // 스폰 청크(START, 레벨 ≤ 32)
        if (blockTickets.isEmpty()) return false;
        long key = Regionizer.chunkKey(cx, cz);
        Long until = blockTickets.get(key);
        if (until == null) return false;
        if (until < gameTime()) {
            blockTickets.remove(key, until);
            return false;
        }
        return true;
    }

    /**
     * 티켓 레벨 33 − 거리 의 엔티티 틱 범위(레벨 31 이하 = ENTITY_TICKING): 가운데에서 거리 − 2 청크 안. 포털 티켓(거리 3)은 반경 1,
     * 드래곤(9)은 반경 7. 그 청크는 플레이어가 없어도 엔티티·블록 엔티티가 틱을 받는다(엔더 진주가 관문·포털 너머에서 계속 난다).
     */
    private final ConcurrentHashMap<Long, Long> entityTickets = new ConcurrentHashMap<>();

    private void markEntityTicking(int cx, int cz, int radius, long until) {
        sweepExpired(entityTickets, entitySweepAt);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) entityTickets.merge(Regionizer.chunkKey(cx + dx, cz + dz), until, Math::max);
        }
    }

    /** 그 청크에 엔티티 틱 단계의 티켓이 살아 있는가(지난 것은 지운다). 강제 로드는 따로 본다. */
    boolean entityTicketed(int cx, int cz) {
        if (spawnTicketWithin(cx, cz, 2)) return true;   // 스폰 청크(START, 레벨 ≤ 31)
        if (entityTickets.isEmpty()) return false;
        long key = Regionizer.chunkKey(cx, cz);
        Long until = entityTickets.get(key);
        if (until == null) return false;
        if (until < gameTime()) {
            entityTickets.remove(key, until);
            return false;
        }
        return true;
    }

    private volatile TickState tickState = new TickState();

    /** /tick 상태(속도·정지·step·틱 시간 표본). 차원 사이에 같은 객체를 공유한다(ServerCore.dimensions). */
    public TickState tickState() { return tickState; }

    public void tickState(TickState state) { this.tickState = state; }

    /** /tick rate: 이 월드 모든 region 의 틱 주기(ns). */
    public void setTickNanos(long nanos) { regionizer.setTickNanos(nanos); }

    public long tickNanos() { return regionizer.tickNanos(); }

    private volatile LightStaleChunks lightStale;

    /** 저장 빛을 믿으면 안 되는 청크(T-012, <차원 폴더>/data/furnace_light_stale.dat). 디스크 월드면 처음 쓸 때 읽는다. */
    public LightStaleChunks lightStale() {
        var f = lightStale;
        if (f != null) return f;
        synchronized (this) {
            if (lightStale == null) {
                lightStale = worldSource instanceof AnvilWorldSource && worldDir != null
                        ? LightStaleChunks.open(dimensionType.storageDir(levelRoot())) : LightStaleChunks.detached();
            }
            return lightStale;
        }
    }

    private volatile ForcedChunks forcedChunks;

    /** 아직 열지 않았으면 null — 매 틱 부르는 곳에서 파일 읽기를 일으키지 않으려는 용도. */
    ForcedChunks forcedChunksOrNull() { return forcedChunks; }

    /** /forceload 로 붙잡은 청크(<차원 폴더>/data/chunks.dat). 디스크 월드면 처음 쓸 때 읽고, 아니면 메모리에서만. */
    public ForcedChunks forcedChunks() {
        var f = forcedChunks;
        if (f != null) return f;
        synchronized (this) {
            if (forcedChunks == null) {
                forcedChunks = worldSource instanceof AnvilWorldSource && worldDir != null ? ForcedChunks.open(dimensionType.storageDir(levelRoot())) : ForcedChunks.detached();
            }
            return forcedChunks;
        }
    }

    /**
     * 바닐라 ServerLevel.setChunkForced: 붙잡거나(add) 놓는다. 바뀌었으면 참. 붙잡은 청크는 구독자가 없어도 내리지 않고, 붙잡는 즉시
     * 올려 둔다(주인 region 이 불러온다).
     */
    public boolean setChunkForced(int cx, int cz, boolean forced) {
        boolean changed = forcedChunks().set(cx, cz, forced);
        if (changed && forced) {
            ChunkPos cp = new ChunkPos(cx, cz);
            runAtChunk(cp, r -> r.loadChunk(cp));
        }
        return changed;
    }

    /** 그 청크에 살아 있는 티켓이 있는가(지난 것은 지운다). 강제 로드 청크는 늘 있다. */
    boolean ticketed(long chunkKey) {
        var forced = forcedChunks;
        if (forced != null && forced.size() > 0 && forced.contains(Regionizer.chunkX(chunkKey), Regionizer.chunkZ(chunkKey))) return true;
        if (!pluginHolds.isEmpty() && pluginHolds.containsKey(chunkKey)) return true;   // 플러그인 티켓
        if (spawnTicketWithin(Regionizer.chunkX(chunkKey), Regionizer.chunkZ(chunkKey), 0)) return true;   // 스폰 청크(START)
        Long until = tickets.get(chunkKey);
        if (until == null) return false;
        if (until < gameTime()) {
            tickets.remove(chunkKey, until);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ 플러그인 청크 티켓(Paper addPluginChunkTicket 와 같은 의미)

    /** 청크 키 → 그 청크를 붙잡은 플러그인 티켓 수. 있는 동안 구독자가 없어도 내리지 않는다. */
    private final ConcurrentHashMap<Long, Integer> pluginHolds = new ConcurrentHashMap<>();
    /** 청크 키 → 엔티티·블록 엔티티 틱까지 주는 플러그인 티켓 수(/forceload 와 같은 단계). 붙잡기의 부분집합이다. */
    private final ConcurrentHashMap<Long, Integer> pluginTicking = new ConcurrentHashMap<>();

    /**
     * 플러그인 티켓 하나를 건다: (cx, cz) 둘레 반경 radius 청크(정사각형)를 붙잡고, ticking 이면 엔티티·블록 엔티티도 틱을 받게 한다.
     * 겹치는 티켓은 세어 두므로 하나를 놓아도 다른 티켓이 걸린 청크는 그대로다. 아직 안 올라온 청크는 주인 region 이 불러오고,
     * 모두 올라오면(실패한 청크가 있으면 예외로) 끝나는 Future 를 돌려준다. 어느 스레드에서 불러도 된다.
     */
    public CompletableFuture<Void> addPluginTicket(int cx, int cz, int radius, boolean ticking) {
        return addPluginTicket(cx - radius, cz - radius, cx + radius, cz + radius, ticking);
    }

    /**
     * 직사각형(청크 좌표, 양 끝 포함) 판. 개수는 먼저 모두 올리고(던지지 않는다) 그다음 청크를 불러오게 한다 — 내린 월드나 닫힌 스케줄러 때문에
     * 불러오기가 실패해도 개수는 {@link #removePluginTicket(int, int, int, int, boolean)} 와 짝이 맞는다. 그런 청크는 Future 가 예외로 끝난다.
     */
    public CompletableFuture<Void> addPluginTicket(int minCx, int minCz, int maxCx, int maxCz, boolean ticking) {
        for (int cz = minCz; cz <= maxCz; cz++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                long key = Regionizer.chunkKey(cx, cz);
                pluginHolds.merge(key, 1, Integer::sum);
                if (ticking) pluginTicking.merge(key, 1, Integer::sum);
            }
        }
        if (unloaded) return CompletableFuture.failedFuture(new java.util.concurrent.RejectedExecutionException("내린 월드: " + dimension()));
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int cz = minCz; cz <= maxCz; cz++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                ChunkPos cp = new ChunkPos(cx, cz);
                try {
                    loads.add(submitAtChunk(cp, r -> r.loadChunk(cp)).thenCompose(f -> f));
                } catch (RuntimeException e) {   // 월드가 그새 내려갔다 등
                    loads.add(CompletableFuture.failedFuture(e));
                }
            }
        }
        return CompletableFuture.allOf(loads.toArray(new CompletableFuture[0]));
    }

    /** {@link #addPluginTicket} 로 건 티켓을 한 번 놓는다(같은 인자로). 청크는 퇴출 유예가 지나면 보통 규칙대로 내려간다. */
    public void removePluginTicket(int cx, int cz, int radius, boolean ticking) {
        removePluginTicket(cx - radius, cz - radius, cx + radius, cz + radius, ticking);
    }

    public void removePluginTicket(int minCx, int minCz, int maxCx, int maxCz, boolean ticking) {
        for (int cz = minCz; cz <= maxCz; cz++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                long key = Regionizer.chunkKey(cx, cz);
                pluginHolds.computeIfPresent(key, (k, v) -> v <= 1 ? null : v - 1);
                if (ticking) pluginTicking.computeIfPresent(key, (k, v) -> v <= 1 ? null : v - 1);
            }
        }
    }

    /** 플러그인 티켓이 그 청크를 붙잡고 있는가. */
    public boolean pluginHeld(int cx, int cz) { return !pluginHolds.isEmpty() && pluginHolds.containsKey(Regionizer.chunkKey(cx, cz)); }

    /** 플러그인 티켓이 그 청크에 엔티티·블록 엔티티 틱을 주는가. */
    boolean pluginTicking(int cx, int cz) { return !pluginTicking.isEmpty() && pluginTicking.containsKey(Regionizer.chunkKey(cx, cz)); }

    /** Entity.placePortalTicket: 그 칸 청크에 포털 티켓(거리 3, 300틱). */
    public void placePortalTicket(int blockX, int blockZ) { addTicket(blockX >> 4, blockZ >> 4, 3, 300); }

    // ------------------------------------------------------------------ 엔드 드래곤 전투(ServerLevel.dragonFight)

    private volatile NbtCompound dragonFightData;
    private volatile org.domi.furnace.server.entity.EndDragonFight dragonFight;
    // 드래곤 티켓을 걸어 둔 동안 참(그 동안 (0,0) 둘레 반경 9 를 붙잡는다)
    private volatile boolean dragonTicket;

    private volatile WanderingTraderState wanderingTraderState;

    /** 오버월드의 방랑 상인 타이머(level.dat 와 공유). 없으면 스포너가 region 마다 따로 센다(시험·단독 월드). */
    public WanderingTraderState wanderingTraderState() { return wanderingTraderState; }

    public void wanderingTraderState(WanderingTraderState s) { this.wanderingTraderState = s; }

    private volatile DimensionSpawners dimensionSpawners;

    /**
     * 차원에 하나뿐인 특수 스포너(마을 포위·방랑 상인) — 오버월드만(바닐라 createLevels 가 다른 차원엔 빈 목록을 준다), 다른 차원은 null.
     * 전역 틱이 tick 을 부른다.
     */
    public DimensionSpawners dimensionSpawners() {
        if (!"minecraft:overworld".equals(dimension())) return null;
        DimensionSpawners d = dimensionSpawners;
        if (d == null) {
            synchronized (this) {
                d = dimensionSpawners;
                if (d == null) dimensionSpawners = d = new DimensionSpawners(this);
            }
        }
        return d;
    }

    /** 이 월드(엔드)에 드래곤 전투를 단다 — 저장된 EndDragonFight.Data 로 시작. */
    public void enableDragonFight(NbtCompound data) { this.dragonFightData = data == null ? new NbtCompound() : data; }

    /** 드래곤 전투(없거나 아직 중앙 청크 주인이 만들지 않았으면 null). */
    public org.domi.furnace.server.entity.EndDragonFight dragonFight() { return dragonFight; }

    /**
     * 엔드 (0,0) 청크 주인 region 에서만 전투를 돌린다: 처음이면 만들고, 주인이 바뀌었으면 그 레벨로 다시 묶는다. 다른 region 의
     * 레벨은 전투를 모른다(가정: 중앙 섬 밖 수정은 전투에 알리지 않는다).
     */
    void tickDragonFight(Region r, RegionLevel level) {
        NbtCompound data = dragonFightData;
        if (data == null || !regionizer.owns(r, 0, 0)) return;
        var f = dragonFight;
        if (f == null) {
            f = new org.domi.furnace.server.entity.EndDragonFight(level, level.worldSeed(), new org.domi.furnace.server.block.Pos(0, 0, 0));
            f.load(data);
            f.ticketHook(this::dragonTicket);
            dragonFight = f;
        } else if (f.level() != level) {
            if (f.level() != null) f.level().setDragonFight(null);
            f.rebind(level);
        }
        if (level.dragonFight() != f) level.setDragonFight(f);
        f.tick();
    }

    /** addRegionTicket(DRAGON, (0,0), 9) — 걸어 둔 동안 2틱씩 늘린다(지나면 저절로 풀린다 = removeRegionTicket). */
    private void dragonTicket(boolean on) {
        if (!on) {
            dragonTicket = false;
            return;
        }
        if (!dragonTicket) {
            dragonTicket = true;
            addTicket(0, 0, 9, 40);
            return;
        }
        long until = gameTime() + 40;
        for (int dz = -9; dz <= 9; dz++) for (int dx = -9; dx <= 9; dx++) tickets.merge(Regionizer.chunkKey(dx, dz), until, Math::max);
        markEntityTicking(0, 0, 7, until);
        markBlockTicking(0, 0, 8, until);
    }

    /** 저장용: 중앙 청크 주인 region 스레드에서 전투 상태를 떠 둔다(저장 스레드는 이 사본만 읽는다). */
    void snapshotDragonFight(Region r, java.util.function.Consumer<NbtCompound> out) {
        var f = dragonFight;
        if (f == null || !regionizer.owns(r, 0, 0)) return;
        out.accept(f.save());
    }

    private volatile java.util.function.Consumer<NbtCompound> dragonFightSaver;

    void autosaveDragonFight(Region r) {
        var saver = dragonFightSaver;
        if (saver != null) snapshotDragonFight(r, saver);
    }

    /** 저장 때 떠 둔 전투 상태를 받을 곳(level.dat DragonFight). */
    public void dragonFightSaver(java.util.function.Consumer<NbtCompound> s) { this.dragonFightSaver = s; }

    // ------------------------------------------------------------------ 차원 이동

    private final org.domi.furnace.server.block.LevelServices.DimensionTravel travel = new org.domi.furnace.server.block.LevelServices.DimensionTravel() {
        @Override
        public boolean isLevelEnabled(String dimension) {
            Dimensions d = dimensions;
            return d != null && d.get(dimension) != null && d.isLevelEnabled(dimension);
        }

        @Override
        public boolean frozen() { return travelFrozen; }

        @Override
        public void departing() { outgoing.incrementAndGet(); }

        @Override
        public void settled() { outgoing.decrementAndGet(); }

        @Override
        public void travel(org.domi.furnace.server.entity.PortalTravel plan,
                           java.util.function.BiConsumer<org.domi.furnace.server.block.ChunkLevel, org.domi.furnace.server.entity.DimensionTransition> arrive) {
            World target = otherWorld(plan.targetDimension());
            if (target == null) {
                arrive.accept(null, null);
                return;
            }
            // 오는 중인 이동을 센다 — 그동안은 그 추가 월드를 내리지 않는다(플레이어 차원은 도착 직전에야 바뀐다)
            target.incoming.incrementAndGet();
            try {
                target.completeTravel(plan, (lvl, t) -> {
                    try {
                        arrive.accept(lvl, t);
                    } finally {
                        target.incoming.decrementAndGet();
                    }
                });
            } catch (RuntimeException e) {
                target.incoming.decrementAndGet();
                throw e;
            }
        }

        @Override
        public void prewarm(org.domi.furnace.server.entity.PortalTravel plan) {
            World target = otherWorld(plan.targetDimension());
            if (target != null && target != World.this) target.prewarmTravel(plan);
        }

        @Override
        public boolean departPassenger(org.domi.furnace.server.entity.Player p) {
            ChunkPos c = p.chunkPos();
            Region r = regionizer.ownerOf(c.x(), c.z());
            return r != null && r.isOwnerThread() && r.detachCarried(p);
        }

        @Override
        public void arrivePassenger(org.domi.furnace.server.entity.Player p, org.domi.furnace.server.block.ChunkLevel target,
                                    org.domi.furnace.server.entity.DimensionTransition t, org.domi.furnace.server.entity.Entity vehicle) {
            if (!(target instanceof RegionLevel rl)) {
                runAt(p.x(), p.z(), r -> r.adopt(p));   // 목적 레벨이 region 이 아니다(있을 수 없다) — 원래 자리로
                return;
            }
            World tw = rl.world();
            if (tw == World.this) {
                // 같은 레벨(엔드 관문): 연결이 보정 텔레포트를 처리한 뒤 출구 칸 주인 region 에 편입하고 다시 태운다
                Runnable adopt = () -> tw.runAt(t.x(), t.z(), r -> r.adoptCarriedWithinLevel(p, t, vehicle));
                if (!p.correctToThen(t.x(), t.y(), t.z(), t.yRot(), t.xRot(), adopt)) rl.region().adoptCarriedWithinLevel(p, t, vehicle);
                return;
            }
            p.carriedVehicle(vehicle);
            if (!p.changeDimension(tw, t, p.portalCooldown)) {   // 연결이 없다 — 원래 자리로(내린 채)
                p.carriedVehicle(null);
                runAt(p.x(), p.z(), r -> r.adopt(p));
            }
        }

        @Override
        public void returnPassenger(org.domi.furnace.server.entity.Player p, org.domi.furnace.server.block.ChunkLevel source,
                                    org.domi.furnace.server.entity.Entity vehicle) {
            ChunkPos c = p.chunkPos();
            if (source instanceof RegionLevel rl && rl.world() == World.this && rl.region().isOwnerThread() && regionizer.ownerOf(c.x(), c.z()) == rl.region()) {
                rl.region().returnCarried(p, vehicle);
            } else {
                runAt(p.x(), p.z(), r -> r.adopt(p));   // 탈것과 다른 region 칸이다 — 내린 채로 제자리에
            }
        }

        @Override
        public void runAtSource(int blockX, int blockZ, java.util.function.Consumer<org.domi.furnace.server.block.ChunkLevel> task) {
            runAt(blockX, blockZ, r -> task.accept(r.level()));
        }

        @Override
        public void withLoadedChunks(int centerX, int centerZ, int createRadius, java.util.function.Consumer<org.domi.furnace.server.block.ChunkLevel> task) {
            withTravelChunks(new org.domi.furnace.server.entity.PortalTravel(dimension(), centerX, centerZ, 0, createRadius, lvl -> null), task);
        }
    };

    /** 이 월드에서 출발하는 차원 이동 경로(블록 레벨 서비스가 내준다). */
    public org.domi.furnace.server.block.LevelServices.DimensionTravel travelService() { return travel; }

    /** 목적 쪽 청크가 한 region 에 모이기를 기다리는 최대 틱(넘으면 있는 그대로 계산한다). */
    private static final int TRAVEL_GATHER_TICKS = 100;

    /**
     * 이 월드(목적 차원)에서 plan 을 마무리한다: 탐색 반경 안의 디스크에 있는(또는 이미 올라온) 청크와 생성 반경 안의 모든 청크를
     * 올린 뒤, 그 청크들이 한 region 에 모이면 그 region 스레드에서 plan.complete → arrive. 블로킹 없이 비동기로.
     */
    public void completeTravel(org.domi.furnace.server.entity.PortalTravel plan,
                               java.util.function.BiConsumer<org.domi.furnace.server.block.ChunkLevel, org.domi.furnace.server.entity.DimensionTransition> arrive) {
        withTravelChunks(plan, lvl -> {
            if (lvl == null) {
                arrive.accept(null, null);
                return;
            }
            org.domi.furnace.server.entity.DimensionTransition t;
            try {
                t = plan.complete().apply(lvl);
            } catch (RuntimeException e) {
                log.error("[PORTAL] 목적지 계산 실패 {}", plan.targetDimension(), e);
                t = null;
            }
            arrive.accept(t == null ? null : lvl, t);
        });
    }

    /**
     * plan 의 탐색·생성 반경 청크를 올리고 한 region 에 모이면 그 region 스레드에서 task(레벨). 청크 준비에 실패하면 task(null).
     * plan.complete 는 쓰지 않는다.
     * 빠른 길(M10): 범위 청크가 모두 이미 올라와 있으면 디스크 확인(IO 레인)·로드 왕복 없이 곧바로 그 region 으로 넘긴다 — 목적 region 의
     * 다음 작업 처리(다음 게임 틱 전)에 도착한다. 바닐라는 같은 틱에 동기로 옮긴다.
     */
    public void withTravelChunks(org.domi.furnace.server.entity.PortalTravel plan, java.util.function.Consumer<org.domi.furnace.server.block.ChunkLevel> task) {
        List<ChunkPos> resident = residentTravelChunks(plan);
        if (resident != null) {
            finishTravel(plan, resident, task, 0, 0);
            return;
        }
        loadTravelChunks(plan).whenComplete((list, err) -> {
            if (err != null) {
                log.warn("[PORTAL] 목적 청크 준비 실패 {}", plan.targetDimension(), err);
                task.accept(null);
                return;
            }
            finishTravel(plan, list, task, 0, 0);
        });
    }

    /**
     * 차원 이동 미리 올리기(M10): 플레이어가 지옥문에서 기다리는 동안(바닐라 기본 80틱) 목적 쪽 범위 청크를 올려 둔다 — 넘어갈 때 빠른 길을 타
     * 도착이 늦지 않게. 생성 반경 청크는 만들어서라도 올린다(바닐라는 넘어가는 그 틱에 서버를 멈추고 만든다). 결과·목적지 계산은 없다.
     */
    public void prewarmTravel(org.domi.furnace.server.entity.PortalTravel plan) {
        if (unloaded) return;
        List<ChunkPos> resident = residentTravelChunks(plan);
        if (resident != null) {
            holdTravelChunks(resident);   // 이미 있다 — 기다리는 동안 내려가지 않게 붙잡기만 늘린다
            return;
        }
        loadTravelChunks(plan).whenComplete((list, err) -> {
            if (err != null) log.debug("[PORTAL] 목적 청크 미리 올리기 실패 {}", plan.targetDimension(), err);
            else holdTravelChunks(list);
        });
    }

    /** 시험용: 지금 그 계획이 빠른 길(IO 확인 없이 바로)을 타는가. */
    boolean travelChunksResident(org.domi.furnace.server.entity.PortalTravel plan) { return residentTravelChunks(plan) != null; }

    /** 미리 올린 청크를 붙잡아 두는 틱 수(미리 올리기 간격 60틱보다 길게 — 기다리는 동안 계속 이어진다). */
    private static final int PREWARM_HOLD_TICKS = 100;

    /**
     * 미리 올린 청크를 잠깐 내리지 않게 붙잡는다(퇴출 유예와 무관하게, 포털 대기가 길어도). 내리기 표(tickets)에만 걸고 엔티티 틱 표는
     * 건드리지 않는다 — 그 청크의 블록·엔티티는 바닐라처럼 틱을 받지 않는다.
     */
    private void holdTravelChunks(List<ChunkPos> list) {
        long until = gameTime() + PREWARM_HOLD_TICKS;
        for (ChunkPos cp : list) tickets.merge(Regionizer.chunkKey(cp.x(), cp.z()), until, Math::max);
    }

    /** 디스크 확인 결과(탐색 범위에서 디스크에도 메모리에도 없던 청크) — 미리 올린 뒤 빠른 길 판정에 쓴다. 잠깐만 믿는다. */
    private record TravelScanKey(int ccx, int ccz, int radius) {}

    private record TravelScan(long expiresNanos, java.util.Set<Long> absent) {}

    private final ConcurrentHashMap<TravelScanKey, TravelScan> travelScans = new ConcurrentHashMap<>();

    /** 디스크 확인을 믿는 시간(10초). 그 사이 새로 만들어졌다가 다시 내려간 청크는 놓칠 수 있다(가정 — 포털이 그 안에 생길 일은 사실상 없다). */
    private static final long TRAVEL_SCAN_TTL_NANOS = 10_000_000_000L;

    private static TravelScanKey scanKey(org.domi.furnace.server.entity.PortalTravel plan) {
        return new TravelScanKey(plan.centerX() >> 4, plan.centerZ() >> 4, travelRadiusChunks(plan));
    }

    private void rememberScan(org.domi.furnace.server.entity.PortalTravel plan, List<ChunkPos> have) {
        long now = System.nanoTime();
        if (travelScans.size() > 64) travelScans.values().removeIf(sc -> now - sc.expiresNanos() > 0);
        TravelScanKey k = scanKey(plan);
        java.util.Set<Long> present = new java.util.HashSet<>(have.size() * 2);
        for (ChunkPos cp : have) present.add(Regionizer.chunkKey(cp.x(), cp.z()));
        java.util.Set<Long> absent = new java.util.HashSet<>();
        for (int dz = -k.radius(); dz <= k.radius(); dz++) {
            for (int dx = -k.radius(); dx <= k.radius(); dx++) {
                long key = Regionizer.chunkKey(k.ccx() + dx, k.ccz() + dz);
                if (!present.contains(key)) absent.add(key);
            }
        }
        travelScans.put(k, new TravelScan(now + TRAVEL_SCAN_TTL_NANOS, absent));
    }

    /**
     * 범위 청크(생성 반경 전부 + 탐색 반경)가 모두 이미 올라와 있으면 그 목록, 하나라도 없으면 null(아무 스레드 — 로드 표는 동시 맵).
     * 최근 디스크 확인에서 디스크에 없던 탐색 범위 청크는 없어도 된다(만든 적 없는 땅에는 포털이 없다).
     */
    private List<ChunkPos> residentTravelChunks(org.domi.furnace.server.entity.PortalTravel plan) {
        TravelScanKey k = scanKey(plan);
        TravelScan scan = travelScans.get(k);
        if (scan != null && System.nanoTime() - scan.expiresNanos() > 0) scan = null;
        int cr = plan.createRadius() < 0 ? -1 : (plan.createRadius() + 15) >> 4;
        int r = k.radius();
        List<ChunkPos> out = new ArrayList<>((2 * r + 1) * (2 * r + 1));
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int cx = k.ccx() + dx, cz = k.ccz() + dz;
                if (loadedChunk(cx, cz) != null) {
                    out.add(new ChunkPos(cx, cz));
                    continue;
                }
                boolean create = Math.abs(dx) <= cr && Math.abs(dz) <= cr;
                if (create || scan == null || !scan.absent().contains(Regionizer.chunkKey(cx, cz))) return null;
            }
        }
        return out;
    }

    private static int travelRadiusChunks(org.domi.furnace.server.entity.PortalTravel plan) {
        int cr = plan.createRadius() < 0 ? -1 : (plan.createRadius() + 15) >> 4;
        int sr = (plan.searchRadius() + 15) >> 4;
        return Math.max(cr, sr);
    }

    /** 디스크 확인(IO 레인) 뒤 올라와 있지 않은 범위 청크만 올린다. 결과는 범위 청크 목록(실패한 청크도 넣는다 — 모으기가 다시 본다). */
    private CompletableFuture<List<ChunkPos>> loadTravelChunks(org.domi.furnace.server.entity.PortalTravel plan) {
        return CompletableFuture.supplyAsync(() -> {
                    List<ChunkPos> list = travelChunks(plan);
                    rememberScan(plan, list);
                    return list;
                }, ioLane(plan.centerX() >> 4, plan.centerZ() >> 4))
                .thenCompose(list -> loadMissing(list).thenApply(v -> list));
    }

    /** 올라와 있지 않은 청크만 그 주인 region 에서 올린다(이미 있는 청크는 왕복하지 않는다). 개별 실패는 삼킨다. */
    private CompletableFuture<Void> loadMissing(List<ChunkPos> list) {
        List<CompletableFuture<ChunkColumn>> fs = new ArrayList<>();
        for (ChunkPos cp : list) {
            if (loadedChunk(cp.x(), cp.z()) != null) continue;
            fs.add(submitAtChunk(cp, r -> r.loadChunk(cp)).thenCompose(f -> f));
        }
        if (fs.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).handle((v, t) -> null);
    }

    /** 모은 뒤 사이에 내려간(또는 올리기에 실패한) 청크를 다시 올려 보는 횟수 — 넘으면 있는 그대로 계산한다. */
    private static final int TRAVEL_RELOAD_TRIES = 2;

    private void finishTravel(org.domi.furnace.server.entity.PortalTravel plan, List<ChunkPos> chunks,
                              java.util.function.Consumer<org.domi.furnace.server.block.ChunkLevel> task, int attempt, int reloads) {
        ChunkPos center = ChunkPos.ofBlock(plan.centerX(), plan.centerZ());
        runAtChunk(center, r -> {
            boolean gathered = true;
            List<ChunkPos> evicted = null;
            for (ChunkPos cp : chunks) {
                if (!regionizer.owns(r, cp.x(), cp.z())) { gathered = false; break; }
                if (loadedChunk(cp.x(), cp.z()) == null) {   // 확인한 뒤 내려갔다(유예가 지났다) — 다시 올린다
                    if (evicted == null) evicted = new ArrayList<>();
                    evicted.add(cp);
                }
            }
            if (attempt < TRAVEL_GATHER_TICKS) {
                if (!gathered) {   // 합치기는 틱 사이에만 — 다음 틱에 다시 본다
                    r.schedule(Regionizer.chunkKey(center.x(), center.z()), r2 -> finishTravel(plan, chunks, task, attempt + 1, reloads), 1);
                    return;
                }
                if (evicted != null && reloads < TRAVEL_RELOAD_TRIES) {
                    loadMissing(evicted).whenComplete((v, t) -> finishTravel(plan, chunks, task, attempt + 1, reloads + 1));
                    return;
                }
            }
            task.accept(r.level());
        });
    }

    /** 올릴 청크: 생성 반경 안 전부 + 탐색 반경 안에서 이미 올라왔거나 디스크에 있는 것(IO 레인에서). */
    private List<ChunkPos> travelChunks(org.domi.furnace.server.entity.PortalTravel plan) {
        int ccx = plan.centerX() >> 4, ccz = plan.centerZ() >> 4;
        List<ChunkPos> out = new ArrayList<>();
        int cr = plan.createRadius() < 0 ? -1 : (plan.createRadius() + 15) >> 4;
        int r = travelRadiusChunks(plan);
        var store = worldSource instanceof AnvilWorldSource anvil ? anvil.store() : null;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int cx = ccx + dx, cz = ccz + dz;
                boolean create = Math.abs(dx) <= cr && Math.abs(dz) <= cr;
                boolean have = create || loadedChunk(cx, cz) != null;
                if (!have && store != null) {
                    try {
                        have = store.hasChunk(cx, cz);
                    } catch (java.io.IOException e) {
                        have = false;
                    }
                }
                if (have) out.add(new ChunkPos(cx, cz));
            }
        }
        return out;
    }

    /** 실제 엔티티 가시 거리(청크). view-distance를 넘으면 미렌더 청크 방지 위해 클램프. */
    public int effectiveEntityChunks() {
        return Math.min(entityViewDistance, viewDistance);
    }

    public ExecutorService ioPool() { return ioPool; }

    /** 디스크 청크 압축 풀기·디코드(레인 밖, 순서 없음). IO 풀이 없거나 닫혔으면 부른 스레드에서. */
    public java.util.concurrent.Executor ioDecodeExecutor() {
        ExecutorService p = ioPool;
        if (p == null) return Runnable::run;
        return r -> {
            try {
                p.execute(r);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                r.run();   // 종료 중 — 레인과 같이 그 자리에서
            }
        };
    }

    private volatile java.util.concurrent.Executor playerIo;

    /** 플레이어 데이터 읽기·쓰기 실행기. 기본은 PlayerData 전용 단일 스레드(청크 IO 풀과 분리, 제출 순서 직렬). */
    public java.util.concurrent.Executor playerIo() {
        java.util.concurrent.Executor e = playerIo;
        return e != null ? e : org.domi.furnace.server.entity.PlayerData.io();
    }

    /** 테스트용: 플레이어 데이터 실행기 교체. */
    public void playerIo(java.util.concurrent.Executor executor) { this.playerIo = executor; }
    public ExecutorService genPool() { return genPool; }

    /** 빛 미리 계산 전용 풀(T-170a): 생성 대기열(genPool, FIFO) 뒤에 다시 줄서지 않게 분리. 모든 차원이 공유하는 작은 데몬 풀. */
    private static final class LightPre {
        static final ExecutorService POOL = java.util.concurrent.Executors.newFixedThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2), new java.util.concurrent.ThreadFactory() {
                    private final java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
                    @Override public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "furnace-light-pre-" + n.incrementAndGet());
                        t.setDaemon(true);
                        t.setPriority(Thread.NORM_PRIORITY - 1);
                        return t;
                    }
                });
    }
    public static ExecutorService lightPre() { return LightPre.POOL; }
    public ExecutorService lightPreExecutor() { return LightPre.POOL; }
    public FurnaceScheduler scheduler() { return scheduler; }
    public WorldSource worldSource() { return worldSource; }
    public ChunkWorkerPool chunkPool() { return chunkPool; }

    /** 청크 스트리밍 설정 주입(FurnaceMain). */
    public void configureStreaming(int spawnChunkRadius, int chunksPerTick) {
        this.spawnChunkRadius = Math.max(0, spawnChunkRadius);
        this.chunksPerTick = chunksPerTick == org.domi.furnace.server.config.Config.CHUNKS_PER_TICK_AUTO ? chunksPerTick : Math.max(1, chunksPerTick);
    }
    /** chunks-per-tick=auto: 클라이언트가 알린 속도만 따르고, 준비된 청크만 배치에 넣는다(Region.sendQueuedChunksAuto). */
    public boolean autoChunkRate() { return chunksPerTick == org.domi.furnace.server.config.Config.CHUNKS_PER_TICK_AUTO; }
    public int spawnChunkRadius() { return spawnChunkRadius; }
    public int chunksPerTick() { return chunksPerTick; }

    /** 플레이어당 동시에 진행 중인 청크 전송(생성·직렬화) 상한 — 생성 스레드보다 넉넉히(이미 로드된 청크는 금방 끝난다). */
    public int maxChunkSendsInFlight() { return maxChunkSendsInFlight; }

    private int maxChunkSendsInFlight = Math.max(16, 2 * Runtime.getRuntime().availableProcessors());

    /** 살아 있는 region 수(테스트·모니터링용). */
    public int regionCount() { return regionizer.regionCount(); }
    public int viewDistance() { return viewDistance; }

    /** 바닐라 ChunkMap.setServerViewDistance(2~32 로 자른다). 플레이어별 거리·패킷은 ServerCore.setViewDistance 가 맞춘다. */
    public void viewDistance(int vd) { this.viewDistance = Math.clamp(vd, org.domi.furnace.server.config.Config.MIN_VIEW_DISTANCE, org.domi.furnace.server.config.Config.MAX_VIEW_DISTANCE); }
    public int entityViewDistance() { return entityViewDistance; }
    public int blockDirectBits() { return blockDirectBits; }
    public int biomeDirectBits() { return biomeDirectBits; }
}
