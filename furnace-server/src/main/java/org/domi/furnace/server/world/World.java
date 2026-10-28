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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public final class World {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(World.class);

    private final WorldSource worldSource;
    private final ChunkWorkerPool chunkPool;
    private final FurnaceScheduler scheduler;
    private final Regionizer regionizer;
    private final int viewDistance;
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
    private int chunksPerTick = 16;     // 플레이어당 틱 전송 상한
    private java.nio.file.Path worldDir = java.nio.file.Path.of("world");
    private BlockRegistry blockRegistry;
    private ItemRegistry itemRegistry;
    private SpawnPoint defaultSpawn = new SpawnPoint(0, 64, 0, 0.0f);
    // 이 월드의 차원(기본 오버월드 — 단일 차원 테스트·도구 호환). 저장 폴더·높이·하늘빛이 여기서 나온다.
    private volatile DimensionType dimensionType;
    private volatile Dimensions dimensions;

    // 로드된 청크와 로드 중인 청크(월드 공용). 청크 내용은 그 청크 구역을 가진 region 스레드만 만진다.
    private final ConcurrentHashMap<Long, ChunkColumn> chunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CompletableFuture<ChunkColumn>> loading = new ConcurrentHashMap<>();

    /**
     * 청크 구독 상태. 청크가 클라에 전달되기 전(ready=false)의 블록 변경은 pending에 쌓았다가 청크 뒤에 보낸다 —
     * 스냅샷 이후·전송 이전의 변경이 청크보다 먼저 도착해 클라가 "청크 미로드"로 버리면 그 변경을 영구히 놓친다.
     * ready/pending은 청크 소유 region 스레드서만 접근한다(브로드캐스트와 전달 완료 처리가 같은 스레드라 직렬화됨).
     */
    public static final class Subscription {
        boolean ready;
        final List<Packet> pending = new ArrayList<>();
    }

    // 청크별 구독자(그 청크를 로드한 플레이어)와 구독 상태. 블록 변경 시 이들에게만 전송.
    private final ConcurrentHashMap<ChunkPos, ConcurrentHashMap<Player, Subscription>> chunkSubscribers = new ConcurrentHashMap<>();

    /** 기본 region 설정(구역 16×16 청크, 합치기 반경 1 구역). regionSize 인자는 예전 고정 격자용이라 쓰지 않는다. */
    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int regionSize, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits, ExecutorService ioPool, ExecutorService genPool) {
        this(worldSource, chunkPool, scheduler, regionThreads, 4, 1, viewDistance, entityViewDistance,
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

    /** 이미 있는 작업 스레드 풀에 이 월드를 건다(패키지 전용 — 공유 풀 시험. 실서버 연결은 다음 단계 S4). 풀 수명은 부르는 쪽이 갖는다. */
    World(DimensionType type, WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler, SharedScheduler pool,
          int sectionShift, int mergeRadius, int viewDistance, int entityViewDistance,
          int blockDirectBits, int biomeDirectBits, ExecutorService ioPool, ExecutorService genPool) {
        this(type, worldSource, chunkPool, scheduler, pool.threadCount(), pool, sectionShift, mergeRadius, viewDistance, entityViewDistance,
                blockDirectBits, biomeDirectBits, ioPool, genPool);
    }

    private World(DimensionType type, WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                  int regionThreads, SharedScheduler pool, int sectionShift, int mergeRadius, int viewDistance, int entityViewDistance,
                  int blockDirectBits, int biomeDirectBits, ExecutorService ioPool, ExecutorService genPool) {
        this.dimensionType = type;
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
        this.regionizer = pool == null
                ? new Regionizer(this, regionThreads, sectionShift, mergeRadius, tickNanos)
                : new Regionizer(this, pool, sectionShift, mergeRadius, tickNanos);
        if (scheduler instanceof org.domi.furnace.server.sched.FurnaceSchedulerImpl impl) {
            if (type == DimensionType.OVERWORLD) impl.installRouter((cx, cz) -> chunkScheduler(cx, cz), regionizer::close);
            else impl.addCloseHook(regionizer::close);
        }
    }

    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int regionSize, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits, ExecutorService ioPool) {
        this(worldSource, chunkPool, scheduler, regionThreads, regionSize, viewDistance,
            entityViewDistance, blockDirectBits, biomeDirectBits, ioPool, ioPool);
    }

    public Regionizer regionizer() { return regionizer; }

    public DimensionType dimensionType() { return dimensionType; }

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

    ChunkColumn loadedChunk(int cx, int cz) { return chunks.get(mapKey(Regionizer.chunkKey(cx, cz))); }

    void putChunk(int cx, int cz, ChunkColumn c) { chunks.put(mapKey(Regionizer.chunkKey(cx, cz)), c); }

    void removeChunk(int cx, int cz) { chunks.remove(mapKey(Regionizer.chunkKey(cx, cz))); }

    CompletableFuture<ChunkColumn> loadingChunk(long key) { return loading.get(mapKey(key)); }

    void putLoading(long key, CompletableFuture<ChunkColumn> f) { loading.put(mapKey(key), f); }

    void removeLoading(long key, CompletableFuture<ChunkColumn> f) { loading.remove(mapKey(key), f); }

    /** 로드된 청크 수(테스트·모니터링). */
    public int loadedChunkCount() { return chunks.size(); }

    // ------------------------------------------------------------------ 작업 보내기

    /** 그 청크 주인 region 에서 task 실행(주인이 없으면 그 구역의 region 을 만든다). 실패는 로그로 남긴다. */
    public void runAtChunk(ChunkPos cp, java.util.function.Consumer<Region> task) {
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
        CompletableFuture<T> f = new CompletableFuture<>();
        runAtChunk(cp, r -> {
            try { f.complete(task.apply(r)); } catch (Throwable t) { f.completeExceptionally(t); }
        });
        return f;
    }

    /** submitAtChunk 의 조회판: 그 청크에 주인 region 이 없으면 region 을 만들지 않고 null. */
    public <T> CompletableFuture<T> submitAtOwnedChunk(ChunkPos cp, java.util.function.Function<Region, T> task) {
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
            runAtChunk(cp, r -> r.schedule(key, r2 -> {
                try { task.run(); f.complete(null); } catch (Throwable t) { f.completeExceptionally(t); }
            }, delayTicks));
            return f;
        }

        @Override
        public boolean isOwnerThread() {
            Region r = regionizer.ownerOf(cp.x(), cp.z());
            return r != null && r.isOwnerThread();
        }

        @Override
        public String ownerName() { return "region@chunk" + cp; }
    }

    /** 저장·종료용: 모든 region 의 dirty 청크 저장(각 region 스레드에서). 플레이어 저장은 각 연결의 channelInactive 가 한다. */
    public List<CompletableFuture<Void>> flushAll() {
        org.domi.furnace.server.map.MapStore.saveDirty();   // 바뀐 지도 데이터·idcounts
        List<CompletableFuture<Void>> fs = new ArrayList<>(forEachRegion(r -> {
            r.saveDirtyChunks();
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
            return null;
        }));
        return fs;
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
        int cx = snap.chunkX(), cz = snap.chunkZ();
        ioLane(cx, cz).execute(() -> {
            boolean ok;
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                NbtCompound nbt = anvil.codec().toNbt(snap);
                NbtIo.write(new DataOutputStream(bos), nbt, "");
                byte[] bytes = bos.toByteArray();
                ok = runWithRetry(() -> anvil.store().writeChunk(cx, cz, bytes), "청크 " + cx + "," + cz + " 저장");
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(World.class).error("[SAVE] 청크 {},{} 직렬화 실패", cx, cz, e);
                ok = false;
            }
            if (!ok) onChunkSaveFailed(new ChunkPos(cx, cz), col);
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

    /** 가장 긴 IO 레인 대기열 길이. */
    public int ioBacklog() { return ioLanes == null ? 0 : ioLanes.maxBacklog(); }

    /** 저장 역압: 레인이 밀려 있으면 이번 틱 저장 제출을 멈춘다. */
    public boolean ioBackedUp() { return ioBacklog() >= ioBacklogLimit; }

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
                    NbtCompound root = anvil.readEntities(chunkX, chunkZ);
                    if (root == null) {
                        root = new NbtCompound();
                        root.putInt("DataVersion", org.domi.furnace.server.world.anvil.AnvilChunkCodec.DATA_VERSION);
                        root.putIntArray("Position", new int[] {chunkX, chunkZ});
                    }
                    org.domi.furnace.server.net.io.nbt.NbtList list = root.tags().get("Entities") instanceof org.domi.furnace.server.net.io.nbt.NbtList l
                            ? l : new org.domi.furnace.server.net.io.nbt.NbtList(NbtIo.TAG_COMPOUND);
                    for (NbtCompound t : tags) list.add(t);
                    root.putList("Entities", list);
                    anvil.writeEntities(chunkX, chunkZ, root);
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

    private volatile org.domi.furnace.server.world.raid.RaidStore raidStore;
    private volatile CommandStorage commandStorage;

    /** /data storage 저장소(차원 data 폴더 — 명령은 주 월드 것을 쓴다). 디스크 월드가 아니면 메모리에서만. */
    public CommandStorage commandStorage() {
        var s = commandStorage;
        if (s != null) return s;
        synchronized (this) {
            if (commandStorage == null) {
                commandStorage = new CommandStorage(worldSource instanceof AnvilWorldSource && worldDir != null
                        ? dimensionType.storageDir(worldDir).resolve("data") : null);
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
                        ? org.domi.furnace.server.world.raid.RaidStore.open(dimensionType.storageDir(worldDir),
                                dimensionType == DimensionType.END ? "raids_end" : "raids")
                        : org.domi.furnace.server.world.raid.RaidStore.detached();
            }
            return raidStore;
        }
    }
    public java.nio.file.Path worldDir() { return worldDir; }
    public void defaultSpawn(SpawnPoint spawn) { this.defaultSpawn = spawn; }

    // 월드 시계: 모든 region 스레드·연결이 같은 값을 보도록 단조 시계(nanoTime)에서 계산한다(스레드별 tickCount는 비교 불가).
    // 시간·날씨: 전역 틱(ServerCore.tick)이 진행한다.
    private volatile WorldClock clock = new WorldClock();

    public WorldClock clock() { return clock; }

    public long gameTime() { return clock.gameTime(); }

    public long dayTime() { return clock.dayTime(); }

    /**
     * 접속 중 플레이어(eid→Player) — 서버 전체(모든 차원 월드가 같은 것을 쓴다). 탭 목록은 바닐라처럼 거리·차원과 무관하게
     * 이 집합 전체를 기준으로 한다. array 는 복사본 — 몹마다·스폰 시도마다 부르는 forEachPlayer 가 맵 반복자를 만들지 않게
     * (접속·퇴장 때만 새로 만든다).
     */
    static final class Online {
        final ConcurrentHashMap<Integer, Player> map = new ConcurrentHashMap<>();
        volatile Player[] array = new Player[0];

        void put(Player p) {
            synchronized (map) {
                map.put(p.entityId(), p);
                array = map.values().toArray(new Player[0]);
                Player.bumpRoster();
            }
        }

        void remove(int eid) {
            synchronized (map) {
                map.remove(eid);
                array = map.values().toArray(new Player[0]);
                Player.bumpRoster();
            }
        }
    }

    private volatile Online online = new Online();
    /** 서버 없이(테스트·도구) 쓸 때: 기본값 + 이 월드의 접속자. */
    private volatile org.domi.furnace.server.block.LevelServices levelServices = new org.domi.furnace.server.block.LevelServices.Delegating(
            org.domi.furnace.server.block.LevelServices.DEFAULT) {
        @Override
        public void forEachPlayer(java.util.function.Consumer<Player> c) { World.this.forEachPlayerHere(c); }

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
    public org.domi.furnace.server.block.LevelServices levelServices() { return levelServices; }

    public void levelServices(org.domi.furnace.server.block.LevelServices s) { this.levelServices = s; }

    // ------------------------------------------------------------------ 잠(바닐라 ServerLevel.sleepStatus)

    private final SleepStatus sleepStatus = new SleepStatus();

    public SleepStatus sleepStatus() { return sleepStatus; }

    /** 이 차원 접속자 목록(ServerLevel.players). */
    public java.util.List<Player> playersHere() {
        java.util.List<Player> list = new java.util.ArrayList<>();
        forEachPlayerHere(list::add);
        return list;
    }

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
        int pct = levelServices.gameRuleInt("playersSleepingPercentage");
        org.domi.furnace.api.text.Text msg = sleepStatus.areEnoughSleeping(pct)
                ? org.domi.furnace.api.text.Text.translatable("sleep.skipping_night")
                : org.domi.furnace.api.text.Text.translatable("sleep.players_sleeping", sleepStatus.amountSleeping(), sleepStatus.sleepersNeeded(pct));
        for (Player p : players) p.sendSystemMessage(msg, true);
    }

    public void playerOnline(Player p) { online.put(p); }

    public void playerOffline(int eid) {
        online.remove(eid);
        trackers.remove(eid);
    }

    /** 이 차원에 있는 접속자마다(바닐라 ServerLevel.players — 차원을 옮기는 중인 플레이어는 도착한 쪽). */
    public void forEachPlayerHere(java.util.function.Consumer<Player> c) {
        String dim = dimensionType.levelKey();
        for (Player p : online.array) if (dim.equals(p.dimension())) c.accept(p);
    }

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
    public java.util.Collection<Player> onlinePlayers() { return online.map.values(); }
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

    /** 본문 직렬화용 스냅샷을 뜬 횟수(테스트가 "스냅샷이 떠졌다"를 기다리는 신호). */
    final java.util.concurrent.atomic.AtomicInteger bodySnapshots = new java.util.concurrent.atomic.AtomicInteger();

    public CompletableFuture<byte[]> serializedChunkBody(ChunkPos cp) {
        return submitAtChunk(cp, r -> r.loadChunk(cp))
                .thenCompose(f -> f)
                // 컬럼 접근(dirty/캐시/스냅샷)은 항상 주인 region 스레드서 — 로드 완료 스레드(IO 풀 등)서 돌지 않게 한다.
                .thenCompose(col -> submitAtChunk(cp, r -> {
                    // 캐시는 청크가 바뀔 때마다(ChunkColumn.touch) 비워진다 — 새로 생성돼 아직 저장 전(dirty)인 청크도 캐시한다.
                    byte[] cached = col.cachedSerializedBody();
                    if (cached != null) return CompletableFuture.<byte[]>completedFuture(cached);
                    // 같은 버전이 이미 직렬화 중이면 그 결과를 같이 받는다. 필드 읽기·쓰기·touch 가 모두 주인 region 스레드
                    // 태스크라, 남아 있으면 지금 modCount 와 같은 버전이고 아직 끝나지 않은 것이다.
                    CompletableFuture<byte[]> inflight = col.inflightSerializedBody();
                    if (inflight != null) return inflight;

                    // 스냅샷(약 390KB)은 본문 직렬화에만 쓰므로 컬럼에 붙들어 두지 않는다 — 본문 캐시가 그 뒤를 대신한다.
                    ChunkSnapshot snapshot = ChunkSnapshot.of(cp.x(), cp.z(), col);
                    bodySnapshots.incrementAndGet();
                    // 스냅샷과 같은 동기 구간에서 수정 카운터를 잡는다 — 풀 직렬화가 도는 동안 편집이 끼면
                    // 카운터가 달라져 낡은 본문을 캐시에 넣지 않는다.
                    long version = col.modCount();
                    ChunkSnapshot finalSnapshot = snapshot;
                    CompletableFuture<byte[]> f = new CompletableFuture<>();
                    col.inflightSerializedBody(f);
                    // 결과(성공·실패)는 주인 스레드 태스크 안에서 진행 표시를 내린 뒤 완료한다 — 그 뒤 요청이 끝난(실패한)
                    // future 를 물려받지 않는다. 캐시 쓰기도 같은 태스크라 내림과 캐시 사이에 틈이 없다.
                    chunkPool.submit(() -> serializeChunkBody(finalSnapshot)).handle((body, ex) -> submitAtChunk(cp, r2 -> {
                        if (col.inflightSerializedBody() == f) col.inflightSerializedBody(null);
                        if (ex == null && col.modCount() == version) col.cachedSerializedBody(body);
                        return body;
                    }).whenComplete((body2, ex2) -> {
                        if (ex != null) f.completeExceptionally(ex);
                        else if (ex2 != null) f.completeExceptionally(ex2);
                        else f.complete(body);
                    }));
                    return f;
                }).thenCompose(f -> f));
    }

    /** 시험용: 본문 직렬화 실패 주입(null 이면 없음). */
    volatile Runnable serializeFaultHook;

    private byte[] serializeChunkBody(ChunkSnapshot snap) {
        Runnable hook = serializeFaultHook;
        if (hook != null) hook.run();
        ByteBuf body = Unpooled.buffer();
        try {
            DimensionType t = dimensionType;
            ChunkSerializer.serialize(body, snap, blockDirectBits, biomeDirectBits, t.minY(), t.hasSkyLight());
            byte[] out = new byte[body.readableBytes()];
            body.getBytes(0, out);
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
     * 빛 갱신(ChunkHolder.broadcastChanges → ChunkMap.getPlayers(pos, true)): 그 청크를 보면서 둘레 8칸 중 하나라도 안 보는 플레이어에게만.
     * 안쪽 청크는 클라이언트가 스스로 빛을 다시 계산한다. 이웃은 구독 여부로만 본다(이웃 ready 는 다른 region 소유라 읽지 않는다).
     * 아직 청크가 안 간 구독은 청크 뒤로 보류한다(청크 스냅숏이 변경 전일 수 있다).
     */
    public void broadcastLightUpdate(ChunkPos cp, Packet pkt) {
        ConcurrentHashMap<Player, Subscription> s = chunkSubscribers.get(cp);
        if (s == null) return;
        for (Map.Entry<Player, Subscription> e : s.entrySet()) {
            if (!onTrackedBorder(cp, e.getKey())) continue;
            Subscription sub = e.getValue();
            if (sub.ready) e.getKey().send(pkt);
            else sub.pending.add(pkt);
        }
    }

    /** ChunkMap.isChunkOnTrackedBorder: 둘레 8칸 중 그 플레이어가 구독하지 않은 청크가 있다. */
    boolean onTrackedBorder(ChunkPos cp, Player p) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                ConcurrentHashMap<Player, Subscription> n = chunkSubscribers.get(new ChunkPos(cp.x() + dx, cp.z() + dz));
                if (n == null || !n.containsKey(p)) return true;
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
    }

    private volatile TickState tickState = new TickState();

    /** /tick 상태(속도·정지·step·틱 시간 표본). 차원 사이에 같은 객체를 공유한다(ServerCore.dimensions). */
    public TickState tickState() { return tickState; }

    public void tickState(TickState state) { this.tickState = state; }

    /** /tick rate: 이 월드 모든 region 의 틱 주기(ns). */
    public void setTickNanos(long nanos) { regionizer.setTickNanos(nanos); }

    public long tickNanos() { return regionizer.tickNanos(); }

    private volatile ForcedChunks forcedChunks;

    /** 아직 열지 않았으면 null — 매 틱 부르는 곳에서 파일 읽기를 일으키지 않으려는 용도. */
    ForcedChunks forcedChunksOrNull() { return forcedChunks; }

    /** /forceload 로 붙잡은 청크(<차원 폴더>/data/chunks.dat). 디스크 월드면 처음 쓸 때 읽고, 아니면 메모리에서만. */
    public ForcedChunks forcedChunks() {
        var f = forcedChunks;
        if (f != null) return f;
        synchronized (this) {
            if (forcedChunks == null) {
                forcedChunks = worldSource instanceof AnvilWorldSource && worldDir != null ? ForcedChunks.open(dimensionType.storageDir(worldDir)) : ForcedChunks.detached();
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
        Long until = tickets.get(chunkKey);
        if (until == null) return false;
        if (until < gameTime()) {
            tickets.remove(chunkKey, until);
            return false;
        }
        return true;
    }

    /** Entity.placePortalTicket: 그 칸 청크에 포털 티켓(거리 3, 300틱). */
    public void placePortalTicket(int blockX, int blockZ) { addTicket(blockX >> 4, blockZ >> 4, 3, 300); }

    // ------------------------------------------------------------------ 엔드 드래곤 전투(ServerLevel.dragonFight)

    private volatile NbtCompound dragonFightData;
    private volatile org.domi.furnace.server.entity.EndDragonFight dragonFight;
    // 드래곤 티켓을 걸어 둔 동안 참(그 동안 (0,0) 둘레 반경 9 를 붙잡는다)
    private volatile boolean dragonTicket;

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
        public void travel(org.domi.furnace.server.entity.PortalTravel plan,
                           java.util.function.BiConsumer<org.domi.furnace.server.block.ChunkLevel, org.domi.furnace.server.entity.DimensionTransition> arrive) {
            World target = otherWorld(plan.targetDimension());
            if (target == null) {
                arrive.accept(null, null);
                return;
            }
            target.completeTravel(plan, arrive);
        }

        @Override
        public void runAtSource(int blockX, int blockZ, java.util.function.Consumer<org.domi.furnace.server.block.ChunkLevel> task) {
            runAt(blockX, blockZ, r -> task.accept(r.level()));
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
        CompletableFuture.supplyAsync(() -> travelChunks(plan), ioLane(plan.centerX() >> 4, plan.centerZ() >> 4))
                .thenCompose(list -> {
                    List<CompletableFuture<ChunkColumn>> fs = new ArrayList<>(list.size());
                    for (ChunkPos cp : list) fs.add(submitAtChunk(cp, r -> r.loadChunk(cp)).thenCompose(f -> f));
                    return CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).handle((v, t) -> list);
                })
                .whenComplete((list, err) -> {
                    if (err != null) {
                        log.warn("[PORTAL] 목적 청크 준비 실패 {}", plan.targetDimension(), err);
                        arrive.accept(null, null);
                        return;
                    }
                    finishTravel(plan, list, arrive, 0);
                });
    }

    private void finishTravel(org.domi.furnace.server.entity.PortalTravel plan, List<ChunkPos> chunks,
                              java.util.function.BiConsumer<org.domi.furnace.server.block.ChunkLevel, org.domi.furnace.server.entity.DimensionTransition> arrive,
                              int attempt) {
        ChunkPos center = ChunkPos.ofBlock(plan.centerX(), plan.centerZ());
        runAtChunk(center, r -> {
            boolean gathered = true;
            for (ChunkPos cp : chunks) {
                if (!regionizer.owns(r, cp.x(), cp.z())) { gathered = false; break; }
            }
            if (!gathered && attempt < TRAVEL_GATHER_TICKS) {   // 합치기는 틱 사이에만 — 다음 틱에 다시 본다
                r.schedule(Regionizer.chunkKey(center.x(), center.z()), r2 -> finishTravel(plan, chunks, arrive, attempt + 1), 1);
                return;
            }
            org.domi.furnace.server.entity.DimensionTransition t;
            try {
                t = plan.complete().apply(r.level());
            } catch (RuntimeException e) {
                log.error("[PORTAL] 목적지 계산 실패 {}", plan.targetDimension(), e);
                t = null;
            }
            arrive.accept(t == null ? null : r.level(), t);
        });
    }

    /** 올릴 청크: 생성 반경 안 전부 + 탐색 반경 안에서 이미 올라왔거나 디스크에 있는 것(IO 레인에서). */
    private List<ChunkPos> travelChunks(org.domi.furnace.server.entity.PortalTravel plan) {
        int ccx = plan.centerX() >> 4, ccz = plan.centerZ() >> 4;
        List<ChunkPos> out = new ArrayList<>();
        int cr = plan.createRadius() < 0 ? -1 : (plan.createRadius() + 15) >> 4;
        int sr = (plan.searchRadius() + 15) >> 4;
        int r = Math.max(cr, sr);
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

    private volatile java.util.concurrent.Executor playerIo;

    /** 플레이어 데이터 읽기·쓰기 실행기. 기본은 PlayerData 전용 단일 스레드(청크 IO 풀과 분리, 제출 순서 직렬). */
    public java.util.concurrent.Executor playerIo() {
        java.util.concurrent.Executor e = playerIo;
        return e != null ? e : org.domi.furnace.server.entity.PlayerData.io();
    }

    /** 테스트용: 플레이어 데이터 실행기 교체. */
    public void playerIo(java.util.concurrent.Executor executor) { this.playerIo = executor; }
    public ExecutorService genPool() { return genPool; }
    public FurnaceScheduler scheduler() { return scheduler; }
    public WorldSource worldSource() { return worldSource; }
    public ChunkWorkerPool chunkPool() { return chunkPool; }

    /** 청크 스트리밍 설정 주입(FurnaceMain). */
    public void configureStreaming(int spawnChunkRadius, int chunksPerTick) {
        this.spawnChunkRadius = Math.max(0, spawnChunkRadius);
        this.chunksPerTick = Math.max(1, chunksPerTick);
    }
    public int spawnChunkRadius() { return spawnChunkRadius; }
    public int chunksPerTick() { return chunksPerTick; }

    /** 플레이어당 동시에 진행 중인 청크 전송(생성·직렬화) 상한 — 생성 스레드보다 넉넉히(이미 로드된 청크는 금방 끝난다). */
    public int maxChunkSendsInFlight() { return maxChunkSendsInFlight; }

    private int maxChunkSendsInFlight = Math.max(16, 2 * Runtime.getRuntime().availableProcessors());

    /** 살아 있는 region 수(테스트·모니터링용). */
    public int regionCount() { return regionizer.regionCount(); }
    public int viewDistance() { return viewDistance; }
    public int entityViewDistance() { return entityViewDistance; }
    public int blockDirectBits() { return blockDirectBits; }
    public int biomeDirectBits() { return biomeDirectBits; }
}
