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
        this.worldSource = worldSource;
        this.ioPool = ioPool;
        this.genPool = genPool;
        this.chunkPool = chunkPool;
        this.scheduler = scheduler;
        this.viewDistance = viewDistance;
        this.entityViewDistance = entityViewDistance;
        this.blockDirectBits = blockDirectBits;
        this.biomeDirectBits = biomeDirectBits;
        long tickNanos = scheduler instanceof org.domi.furnace.server.sched.FurnaceSchedulerImpl impl ? impl.tickNanos() : 50_000_000L;
        this.regionizer = new Regionizer(this, regionThreads, sectionShift, mergeRadius, tickNanos);
        if (scheduler instanceof org.domi.furnace.server.sched.FurnaceSchedulerImpl impl) {
            impl.installRouter((cx, cz) -> chunkScheduler(cx, cz), regionizer::close);
        }
    }

    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int regionSize, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits, ExecutorService ioPool) {
        this(worldSource, chunkPool, scheduler, regionThreads, regionSize, viewDistance,
            entityViewDistance, blockDirectBits, biomeDirectBits, ioPool, ioPool);
    }

    public Regionizer regionizer() { return regionizer; }

    /** 게임 틱을 켠다(서버 준비가 끝난 뒤). 꺼져 있으면 region 은 작업만 처리한다. */
    public void startTicking() { regionizer.gameTicks(true); }

    /** 모든 region 에서 게임 틱을 한 번씩(테스트용 — 게임 틱이 꺼져 있을 때 손으로 돌린다). */
    public CompletableFuture<Void> tickOnce() {
        return CompletableFuture.allOf(regionizer.forEachRegion(Region::tick).toArray(new CompletableFuture[0]));
    }

    // ------------------------------------------------------------------ 청크 저장소(주인 region 스레드에서 쓴다)

    ChunkColumn loadedChunk(int cx, int cz) { return chunks.get(Regionizer.chunkKey(cx, cz)); }

    void putChunk(int cx, int cz, ChunkColumn c) { chunks.put(Regionizer.chunkKey(cx, cz), c); }

    void removeChunk(int cx, int cz) { chunks.remove(Regionizer.chunkKey(cx, cz)); }

    CompletableFuture<ChunkColumn> loadingChunk(long key) { return loading.get(key); }

    void putLoading(long key, CompletableFuture<ChunkColumn> f) { loading.put(key, f); }

    void removeLoading(long key, CompletableFuture<ChunkColumn> f) { loading.remove(key, f); }

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

    /** runAtChunk 의 결과판. */
    public <T> CompletableFuture<T> submitAtChunk(ChunkPos cp, java.util.function.Function<Region, T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        runAtChunk(cp, r -> {
            try { f.complete(task.apply(r)); } catch (Throwable t) { f.completeExceptionally(t); }
        });
        return f;
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
        return forEachRegion(Region::saveDirtyChunks);
    }

    /** owner 스레드서 스냅샷 뜬 뒤 IO 풀서 .mca write. worldSource가 AnvilWorldSource일 때만. */
    public void saveChunkAsync(ChunkSnapshot snap) {
        if (!(worldSource instanceof AnvilWorldSource anvil)) return;
        ioPool.execute(() -> {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                NbtCompound nbt = anvil.codec().toNbt(snap);
                NbtIo.write(new DataOutputStream(bos), nbt, "");
                anvil.store().writeChunk(snap.chunkX(), snap.chunkZ(), bos.toByteArray());
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(World.class)
                        .error("[SAVE] 청크 {},{} 저장 실패", snap.chunkX(), snap.chunkZ(), e);
            }
        });
    }

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
    public java.nio.file.Path worldDir() { return worldDir; }
    public void defaultSpawn(SpawnPoint spawn) { this.defaultSpawn = spawn; }

    // 월드 시계: 모든 region 스레드·연결이 같은 값을 보도록 단조 시계(nanoTime)에서 계산한다(스레드별 tickCount는 비교 불가).
    // 시간·날씨: 전역 틱(ServerCore.tick)이 진행한다.
    private final WorldClock clock = new WorldClock();

    public WorldClock clock() { return clock; }

    public long gameTime() { return clock.gameTime(); }

    public long dayTime() { return clock.dayTime(); }

    // 접속 중 플레이어(eid→Player). 탭 목록은 바닐라처럼 거리와 무관하게 이 집합 전체를 기준으로 한다.
    private final ConcurrentHashMap<Integer, Player> online = new ConcurrentHashMap<>();
    /** 서버 없이(테스트·도구) 쓸 때: 기본값 + 이 월드의 접속자. */
    private volatile org.domi.furnace.server.block.LevelServices levelServices = new org.domi.furnace.server.block.LevelServices.Delegating(
            org.domi.furnace.server.block.LevelServices.DEFAULT) {
        @Override
        public void forEachPlayer(java.util.function.Consumer<Player> c) { for (Player p : online.values()) c.accept(p); }

        @Override
        public long gameTime() { return clock.gameTime(); }

        @Override
        public long dayTime() { return clock.dayTime(); }
    };

    /** 블록 레벨이 보는 서버 쪽 정보(ServerCore 가 설치한다). */
    public org.domi.furnace.server.block.LevelServices levelServices() { return levelServices; }

    public void levelServices(org.domi.furnace.server.block.LevelServices s) { this.levelServices = s; }

    public void playerOnline(Player p) { online.put(p.entityId(), p); }
    public void playerOffline(int eid) { online.remove(eid); trackers.remove(eid); }

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
    public java.util.Collection<Player> onlinePlayers() { return online.values(); }
    public Player onlinePlayer(int eid) { return online.get(eid); }
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

    public CompletableFuture<byte[]> serializedChunkBody(ChunkPos cp) {
        return submitAtChunk(cp, r -> r.loadChunk(cp))
                .thenCompose(f -> f)
                // 컬럼 접근(dirty/캐시/스냅샷)은 항상 주인 region 스레드서 — 로드 완료 스레드(IO 풀 등)서 돌지 않게 한다.
                .thenCompose(col -> submitAtChunk(cp, r -> {
                    // 캐시는 청크가 바뀔 때마다(ChunkColumn.touch) 비워진다 — 새로 생성돼 아직 저장 전(dirty)인 청크도 캐시한다.
                    byte[] cached = col.cachedSerializedBody();
                    if (cached != null) return CompletableFuture.<byte[]>completedFuture(cached);

                    ChunkSnapshot snapshot = col.cachedSnapshot();
                    if (snapshot == null) {
                        snapshot = ChunkSnapshot.of(cp.x(), cp.z(), col);
                        col.cachedSnapshot(snapshot);
                    }
                    // 스냅샷과 같은 동기 구간에서 수정 카운터를 잡는다 — 풀 직렬화가 도는 동안 편집이 끼면
                    // 카운터가 달라져 낡은 본문을 캐시에 넣지 않는다.
                    long version = col.modCount();
                    ChunkSnapshot finalSnapshot = snapshot;
                    return chunkPool.submit(() -> serializeChunkBody(finalSnapshot)).thenCompose(body -> submitAtChunk(cp, r2 -> {
                        if (col.modCount() == version) col.cachedSerializedBody(body);
                        return body;
                    }));
                }).thenCompose(f -> f));
    }

    private byte[] serializeChunkBody(ChunkSnapshot snap) {
        ByteBuf body = Unpooled.buffer();
        try {
            ChunkSerializer.serialize(body, snap, blockDirectBits, biomeDirectBits);
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

    /** 실제 엔티티 가시 거리(청크). view-distance를 넘으면 미렌더 청크 방지 위해 클램프. */
    public int effectiveEntityChunks() {
        return Math.min(entityViewDistance, viewDistance);
    }

    public ExecutorService ioPool() { return ioPool; }
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
