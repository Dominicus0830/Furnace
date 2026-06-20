// region 컨테이너 + 공유 자원. 스레드별 region 맵을 들고 매 틱 자기 region들을 틱한다.
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
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public final class World {

    private final WorldSource worldSource;
    private final ChunkWorkerPool chunkPool;
    private final FurnaceScheduler scheduler;
    private final int regionThreads;
    private final int regionSize;
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

    // 인덱스 i 맵은 region 스레드 i만 접근(스레드 확정). 동기화 불필요.
    private final Map<RegionPos, Region>[] regionsByThread;

    // 교차region 가시성 스냅샷: region 스레드가 공개, 인접 region 스레드가 락프리로 읽음.
    private final ConcurrentHashMap<RegionPos, List<EntityView>> snapshots = new ConcurrentHashMap<>();

    // 청크별 구독자(그 청크를 로드한 플레이어). 블록 변경 시 이들에게만 전송.
    private final ConcurrentHashMap<ChunkPos, Set<Player>> chunkSubscribers = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int regionSize, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits, ExecutorService ioPool, ExecutorService genPool) {
        this.worldSource = worldSource;
        this.ioPool = ioPool;
        this.genPool = genPool;
        this.chunkPool = chunkPool;
        this.scheduler = scheduler;
        this.regionThreads = regionThreads;
        this.regionSize = regionSize;
        this.viewDistance = viewDistance;
        this.entityViewDistance = entityViewDistance;
        this.blockDirectBits = blockDirectBits;
        this.biomeDirectBits = biomeDirectBits;
        this.regionsByThread = new Map[regionThreads];
        for (int i = 0; i < regionThreads; i++) regionsByThread[i] = new HashMap<>();
    }

    public World(WorldSource worldSource, ChunkWorkerPool chunkPool, FurnaceScheduler scheduler,
                 int regionThreads, int regionSize, int viewDistance, int entityViewDistance,
                 int blockDirectBits, int biomeDirectBits, ExecutorService ioPool) {
        this(worldSource, chunkPool, scheduler, regionThreads, regionSize, viewDistance,
            entityViewDistance, blockDirectBits, biomeDirectBits, ioPool, ioPool);
    }

    /** region 스레드 i가 매 틱 호출 — 자기 region들 틱. */
    public void tickThread(int index) {
        var it = regionsByThread[index].values().iterator();
        while (it.hasNext()) {
            Region r = it.next();
            try {
                r.tick();
            } catch (Throwable t) {
                org.slf4j.LoggerFactory.getLogger(World.class)
                        .error("[TICK] region {} 실패", r.pos(), t);
            }
            // 빈 region(플레이어·청크·로딩 0) 제거 — 매 틱 순찰 비용·누수 방지. 재방문 시 regionAt이 재생성.
            if (r.isEmpty()) {
                it.remove();
                snapshots.remove(r.pos());
            }
        }
    }

    /** 종료용: 스레드 idx의 모든 region의 dirty 청크 + 접속 플레이어 저장(owner 스레드서 호출). */
    public void flushThread(int index) {
        for (Region r : regionsByThread[index].values()) {
            r.saveDirtyChunks();
            r.savePlayers(worldDir, ioPool);
        }
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
    public void worldDir(java.nio.file.Path dir) { this.worldDir = dir; }
    public java.nio.file.Path worldDir() { return worldDir; }
    public void defaultSpawn(SpawnPoint spawn) { this.defaultSpawn = spawn; }
    public SpawnPoint defaultSpawn() { return defaultSpawn; }

    public void setRegistries(BlockRegistry blocks, ItemRegistry items) {
        this.blockRegistry = blocks;
        this.itemRegistry = items;
    }

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
        RegionPos owner = RegionGrid.regionPosOfChunk(cp.x(), cp.z(), regionSize);
        return scheduler.region(owner)
                .submit(() -> regionAt(owner).loadChunk(cp))
                .thenCompose(f -> f)
                .thenApplyAsync(col -> {
                    ChunkSnapshot cached = col.cachedSnapshot();
                    if (!col.dirty() && cached != null) return cached;
                    ChunkSnapshot snapshot = ChunkSnapshot.of(cp.x(), cp.z(), col);
                    if (!col.dirty()) col.cachedSnapshot(snapshot);
                    return snapshot;
                }, r -> scheduler.region(owner).execute(r));
    }

    public CompletableFuture<byte[]> serializedChunkBody(ChunkPos cp) {
        RegionPos owner = RegionGrid.regionPosOfChunk(cp.x(), cp.z(), regionSize);
        return scheduler.region(owner)
                .submit(() -> regionAt(owner).loadChunk(cp))
                .thenCompose(f -> f)
                .thenCompose(col -> {
                    byte[] cached = col.cachedSerializedBody();
                    if (!col.dirty() && cached != null) return CompletableFuture.completedFuture(cached);

                    ChunkSnapshot snapshot = col.cachedSnapshot();
                    if (snapshot == null || col.dirty()) {
                        snapshot = ChunkSnapshot.of(cp.x(), cp.z(), col);
                        if (!col.dirty()) col.cachedSnapshot(snapshot);
                    }
                    boolean cacheable = !col.dirty();
                    ChunkSnapshot finalSnapshot = snapshot;
                    return chunkPool.submit(() -> serializeChunkBody(finalSnapshot)).thenApplyAsync(body -> {
                        if (cacheable && !col.dirty()) col.cachedSerializedBody(body);
                        return body;
                    }, r -> scheduler.region(owner).execute(r));
                });
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

    /** pos의 region을 그 소유 스레드서 get/create. */
    public Region regionAt(RegionPos pos) {
        int idx = RegionGrid.threadIndex(pos, regionThreads);
        return regionsByThread[idx].computeIfAbsent(pos, p -> new Region(p, this));
    }

    /** 디버그 소유권 가드: pos를 소유한 스레드서 호출 중인지 확인. */
    public void assertOwns(RegionPos pos) {
        ThreadOwner owner = (ThreadOwner) scheduler.region(pos);
        assert owner.isOwnerThread()
                : "region " + pos + " 를 비소유 스레드서 접근: " + Thread.currentThread().getName();
    }

    /** region이 자기 플레이어 스냅샷을 공개(덮어쓰기). views는 불변이어야 한다. */
    public void publishSnapshot(RegionPos pos, List<EntityView> views) {
        snapshots.put(pos, views);
    }

    /** center±radius region들의 스냅샷을 모아 반환. */
    public List<EntityView> candidatesAround(RegionPos center, int radius) {
        List<EntityView> out = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                List<EntityView> s = snapshots.get(new RegionPos(center.x() + dx, center.z() + dz));
                if (s != null) out.addAll(s);
            }
        }
        return out;
    }

    /** 플레이어가 청크를 로드 — 블록 변경 수신 등록. */
    public void subscribeChunk(ChunkPos cp, Player p) {
        chunkSubscribers.computeIfAbsent(cp, k -> ConcurrentHashMap.newKeySet()).add(p);
    }

    /** 플레이어가 청크를 언로드 — 등록 해제. */
    public void unsubscribeChunk(ChunkPos cp, Player p) {
        Set<Player> s = chunkSubscribers.get(cp);
        if (s != null) s.remove(p);
    }

    /** 청크 구독자(블록 변경 브로드캐스트 대상). 비었으면 빈 Set. */
    public Set<Player> subscribers(ChunkPos cp) {
        Set<Player> s = chunkSubscribers.get(cp);
        return s == null ? Set.of() : s;
    }

    /** 엔티티 가시 거리(view-distance로 클램프)가 닿는 인접 region 반경(region 단위). */
    public int neighborRadius() {
        return (int) Math.ceil((double) effectiveEntityChunks() / regionSize) + 1;
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
    public int regionSize() { return regionSize; }

    /** 청크 스트리밍 설정 주입(FurnaceMain). */
    public void configureStreaming(int spawnChunkRadius, int chunksPerTick) {
        this.spawnChunkRadius = Math.max(0, spawnChunkRadius);
        this.chunksPerTick = Math.max(1, chunksPerTick);
    }
    public int spawnChunkRadius() { return spawnChunkRadius; }
    public int chunksPerTick() { return chunksPerTick; }

    /** 로드된 region 총 개수(테스트·모니터링용). owner 스레드 idle 시 호출. */
    public int regionCount() {
        int n = 0;
        for (var m : regionsByThread) n += m.size();
        return n;
    }
    public int viewDistance() { return viewDistance; }
    public int entityViewDistance() { return entityViewDistance; }
    public int blockDirectBits() { return blockDirectBits; }
    public int biomeDirectBits() { return biomeDirectBits; }
}
