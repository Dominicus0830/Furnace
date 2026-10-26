// 동적 region(Folia 방식): 구역(section) 묶음을 가진 틱 단위. 자기 구역의 청크·플레이어만 만지고, 한 번에 한 작업 스레드가 돈다.
// 합치기·나누기는 Regionizer 가 틱 사이에 하며, 그때 이 region 의 자료를 위치(청크)에 따라 옮긴다(absorbData·splitData).
package org.domi.furnace.server.world;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import org.domi.furnace.server.entity.EntityView;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerVitals;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.ChunkBatchFinished;
import org.domi.furnace.server.net.protocol.play.ChunkBatchStart;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.MoveEntityPosRot;
import org.domi.furnace.server.net.protocol.play.MoveEntityRot;
import java.util.UUID;
import org.domi.furnace.server.net.protocol.play.PlayerInfoLatency;
import org.domi.furnace.server.net.protocol.play.PlayerInfoRemove;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.SetHeadRotation;
import org.domi.furnace.server.net.protocol.play.SpawnEntity;
import org.domi.furnace.server.net.protocol.play.TeleportEntity;
import org.domi.furnace.server.net.protocol.play.UnloadChunk;

public final class Region {

    // 추적 엔티티가 후보에서 사라진 뒤 despawn까지 견디는 틱 수(합치기·나누기 갭 흡수).
    private static final int ENTITY_DESPAWN_GRACE = 3;
    private static final int LATENCY_BROADCAST_TICKS = 600;   // 바닐라 PlayerList.tick의 핑 갱신 주기
    /** 한 틱에 작업 큐를 비우는 시간 예산(틱의 절반) — 폭주하는 작업이 게임 틱을 굶기지 못하게. */
    private static final long DRAIN_BUDGET_NANOS = 25_000_000L;

    /** 청크 키가 붙은 작업. */
    record Task(long chunkKey, Consumer<Region> run) {}

    private record Delayed(long dueTick, long chunkKey, Consumer<Region> run) {}

    final int id;
    private final World world;

    // ---- Regionizer 가 잠금 아래에서 다루는 상태
    volatile boolean alive = true;
    volatile Region mergedInto;
    volatile boolean ticking;
    volatile Thread tickThread;
    boolean yielded;
    boolean queued;
    long nextTickNanos;
    int ticksSinceRecalc;
    int deadSections;
    final Set<Long> sections = new HashSet<>();
    final Set<Region> pendingMerges = new HashSet<>();
    /** 이 region 구역의 "있는" 청크 키(로드·로드 중·시야). 쓰기는 Regionizer 잠금 안. */
    final Set<Long> present = ConcurrentHashMap.newKeySet();
    final ConcurrentLinkedQueue<Task> inbox = new ConcurrentLinkedQueue<>();

    // ---- 이 region 스레드만 다루는 상태
    private final PriorityQueue<Delayed> delayed = new PriorityQueue<>((a, b) -> Long.compare(a.dueTick, b.dueTick));
    private long tickCount;
    private int latencyCounter;
    private final Map<Integer, Player> players = new HashMap<>();
    private final Map<ChunkPos, Integer> evictGrace = new HashMap<>();
    private final Set<ChunkPos> dirtyChunks = new HashSet<>();
    private int autosaveCounter;
    private RegionLevel level;

    Region(int id, World world) {
        this.id = id;
        this.world = world;
    }

    public int id() { return id; }

    public Map<Integer, Player> players() { return players; }

    public long tickCount() { return tickCount; }

    /** 지금 스레드가 이 region 을 돌리고 있는가. */
    public boolean isOwnerThread() { return tickThread == Thread.currentThread(); }

    private Regionizer rz() { return world.regionizer(); }

    // ------------------------------------------------------------------ 틱(Regionizer 작업 스레드)

    /** 작업 큐 → 지연 작업 → (켜져 있으면) 게임 틱. */
    void runTick(boolean gameTick) {
        tickCount++;
        long deadline = System.nanoTime() + DRAIN_BUDGET_NANOS;
        Task t;
        int n = 0;
        while ((t = inbox.poll()) != null) {
            if (t.run() instanceof Regionizer.RegionWide || mine(t.chunkKey())) runTask(t.run());
            else rz().enqueue(t.chunkKey(), t.run());
            if ((++n & 15) == 0 && System.nanoTime() > deadline) break;
        }
        while (!delayed.isEmpty() && delayed.peek().dueTick <= tickCount) {
            Delayed d = delayed.poll();
            if (mine(d.chunkKey)) runTask(d.run);
            else rz().enqueue(d.chunkKey, d.run);
        }
        if (gameTick) tick();
    }

    /**
     * 그 청크가 지금 이 region 것인가. 큐에 넣은 뒤 돌기 전에 그 구역이 다른 region 으로 나뉘어 갔거나(나누기는 큐를 옮기지만
     * 넣는 쪽과 경합할 수 있다) 지워졌으면 아니다 — 그런 작업은 지금 주인에게 다시 보낸다. 틱 중에는 이 region 의 구역이 빠지지 않는다(더해질 수는 있다).
     */
    private boolean mine(long chunkKey) {
        return rz().owns(this, Regionizer.chunkX(chunkKey), Regionizer.chunkZ(chunkKey));
    }

    private void runTask(Consumer<Region> task) {
        try {
            task.accept(this);
        } catch (Throwable e) {
            org.slf4j.LoggerFactory.getLogger(Region.class).error("[TASK] region #{} 작업 실패", id, e);
        }
    }

    /** 이 region 스레드에서: delay 틱 뒤 그 청크 주인에게서 실행(합치기·나누기 때 남은 틱을 지키며 따라간다). */
    void schedule(long chunkKey, Consumer<Region> task, long delay) {
        delayed.add(new Delayed(tickCount + Math.max(0, delay), chunkKey, task));
    }

    // ------------------------------------------------------------------ 합치기·나누기(Regionizer 잠금 안, 둘 다 쉬는 중)

    void absorbData(Region o) {
        present.addAll(o.present);
        o.present.clear();
        for (Delayed d : o.delayed) delayed.add(new Delayed(tickCount + Math.max(0, d.dueTick - o.tickCount), d.chunkKey, d.run));
        o.delayed.clear();
        for (Player p : o.players.values()) players.put(p.entityId(), p);
        o.players.clear();
        dirtyChunks.addAll(o.dirtyChunks);
        o.dirtyChunks.clear();
        evictGrace.putAll(o.evictGrace);
        o.evictGrace.clear();
        if (o.level != null) level().absorbFrom(o.level);
    }

    /** targetOf(청크 키) 가 null 이면 이 region 에 남는다. */
    void splitData(List<Region> created, LongFunction<Region> targetOf) {
        for (var it = present.iterator(); it.hasNext(); ) {
            long k = it.next();
            Region t = targetOf.apply(k);
            if (t != null) {
                t.present.add(k);
                it.remove();
            }
        }
        List<Delayed> keep = new ArrayList<>();
        for (Delayed d : delayed) {
            Region t = targetOf.apply(d.chunkKey);
            if (t == null) keep.add(d);
            else t.delayed.add(new Delayed(t.tickCount + Math.max(0, d.dueTick - tickCount), d.chunkKey, d.run));
        }
        delayed.clear();
        delayed.addAll(keep);
        for (var it = players.values().iterator(); it.hasNext(); ) {
            Player p = it.next();
            ChunkPos c = p.chunkPos();
            Region t = targetOf.apply(Regionizer.chunkKey(c.x(), c.z()));
            if (t != null) {
                t.players.put(p.entityId(), p);
                it.remove();
            }
        }
        for (var it = dirtyChunks.iterator(); it.hasNext(); ) {
            ChunkPos c = it.next();
            Region t = targetOf.apply(Regionizer.chunkKey(c.x(), c.z()));
            if (t != null) {
                t.dirtyChunks.add(c);
                it.remove();
            }
        }
        for (var it = evictGrace.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Region t = targetOf.apply(Regionizer.chunkKey(e.getKey().x(), e.getKey().z()));
            if (t != null) {
                t.evictGrace.put(e.getKey(), e.getValue());
                it.remove();
            }
        }
        for (Region t : created) {
            t.tickCount = tickCount;
            t.autosaveCounter = autosaveCounter;
            t.latencyCounter = latencyCounter;
            if (level != null) level.splitInto(t.level(), (cx, cz) -> targetOf.apply(Regionizer.chunkKey(cx, cz)) == t);
        }
    }

    /** 나누기 뒤 아무것도 없는 region 인가(없애도 되는가). */
    boolean isIdleEmpty() {
        return players.isEmpty() && present.isEmpty() && inbox.isEmpty() && delayed.isEmpty()
                && (level == null || !level.hasPendingAcks() && level.entities().isEmpty());
    }

    // ------------------------------------------------------------------ 청크

    /** 이 region 구역의 로드된 청크(아니면 null). */
    ChunkColumn ownedChunk(int cx, int cz) {
        if (!rz().owns(this, cx, cz)) return null;
        return world.loadedChunk(cx, cz);
    }

    /** 이 region 구역의 청크를 얻거나(없으면 동기 생성 — 테스트·도구용, 편집은 로드된 청크 대상). */
    public ChunkColumn chunkAt(ChunkPos cp) {
        world.assertOwns(this);
        ChunkColumn c = world.loadedChunk(cp.x(), cp.z());
        if (c != null) return c;
        claim(cp);
        c = world.worldSource().generate(cp.x(), cp.z());
        world.putChunk(cp.x(), cp.z(), c);
        level().lightNewChunk(cp.x(), cp.z());
        level().runLightUpdates();
        loadEntities(cp, c);
        // 새로 생성된 청크는 바닐라처럼 저장 대상(dirty)으로 남긴다. 디스크에서 읽은 청크는 깨끗하다.
        if (c.dirty()) dirtyChunks.add(cp);
        return c;
    }

    /** 청크를 이 region 의 "있는 청크"로(구역이 다른 region 것이면 합칠 대상으로 잇는다). */
    private void claim(ChunkPos cp) {
        Region owner = rz().want(cp.x(), cp.z(), this);
        if (owner != this) {
            throw new IllegalStateException("청크 " + cp + " 는 region #" + owner.id + " 것인데 #" + id + " 에서 접근");
        }
    }

    /** 이 region 스레드에서 비동기 청크 로드(IO 풀 read+decode). 진행 중이면 같은 future. */
    public CompletableFuture<ChunkColumn> loadChunk(ChunkPos cp) {
        world.assertOwns(this);
        ChunkColumn present0 = world.loadedChunk(cp.x(), cp.z());
        if (present0 != null) return CompletableFuture.completedFuture(present0);
        long key = Regionizer.chunkKey(cp.x(), cp.z());
        CompletableFuture<ChunkColumn> inflight = world.loadingChunk(key);
        if (inflight != null) return inflight;
        claim(cp);
        CompletableFuture<ChunkColumn> sourceFuture =
                world.worldSource() instanceof org.domi.furnace.server.world.gen.AnvilWorldSource anvil
                        ? anvil.loadChunkAsync(cp.x(), cp.z(), world.ioPool(), world.genPool())
                        : CompletableFuture.supplyAsync(() -> world.worldSource().generate(cp.x(), cp.z()), world.genPool());
        // 청크 안쪽 조명은 생성 스레드에서 미리 계산한다(region 틱 스레드가 청크당 ~2ms 를 쓰던 것) — 이웃과의 가장자리는 올릴 때 맞춘다.
        final boolean sky = level().hasSkyLight();
        final int bedrock = level().lightBedrockState();
        sourceFuture = sourceFuture.thenApplyAsync(col -> {   // 디스크에서 읽은 청크는 IO 스레드가 아니라 생성 풀에서 계산한다
            if (col != null) {
                try {
                    org.domi.furnace.server.block.ChunkLevel.precomputeLight(col, cp.x(), cp.z(), bedrock, sky);
                } catch (RuntimeException e) {
                    org.slf4j.LoggerFactory.getLogger(Region.class).warn("[LIGHT] 미리 계산 실패 {} — 올릴 때 다시 계산", cp, e);
                    col.light(null);
                    col.lightPrecomputed(false);
                }
            }
            return col;
        }, world.genPool());
        CompletableFuture<ChunkColumn> f = new CompletableFuture<>();
        world.putLoading(key, f);
        // 완료는 그때의 주인 region 에서(그 사이 합쳐지거나 나뉘었을 수 있다). 실패해도 진행 중 표시는 반드시 지운다.
        sourceFuture.whenComplete((col, err) -> world.runAtChunk(cp, r -> r.finishLoad(cp, key, col, err, f)));
        return f;
    }

    /** -Dfurnace.tickstats: 청크 설치 비용 {개수, 넣기, 빛, 엔티티·블록엔티티·예약틱} ns 합계(region 스레드들이 함께 올리므로 근사). */
    static final long[] INSTALL_STATS = new long[4];

    private void finishLoad(ChunkPos cp, long key, ChunkColumn col, Throwable err, CompletableFuture<ChunkColumn> f) {
        world.removeLoading(key, f);
        if (err != null) {
            org.slf4j.LoggerFactory.getLogger(Region.class).warn("[CHUNK] 로드 실패 {} — 다음 요청에서 재시도", cp, err);
            rz().release(this, cp.x(), cp.z());
            f.completeExceptionally(err);
            return;
        }
        ChunkColumn existing = world.loadedChunk(cp.x(), cp.z());
        if (existing != null) {   // 그 사이 동기 생성 경로가 먼저 넣었다
            f.complete(existing);
            return;
        }
        long t0 = Regionizer.STATS ? System.nanoTime() : 0L;
        if (col.dirty()) dirtyChunks.add(cp);
        world.putChunk(cp.x(), cp.z(), col);
        long t1 = Regionizer.STATS ? System.nanoTime() : 0L;
        level().lightNewChunk(cp.x(), cp.z());
        level().runLightUpdates();
        long t2 = Regionizer.STATS ? System.nanoTime() : 0L;
        loadEntities(cp, col);
        if (!col.blockEntities().isEmpty()) level().registerBlockEntities(col);
        if (col.extraNbt() != null && (col.extraNbt().contains("block_ticks") || col.extraNbt().contains("fluid_ticks"))) {
            level().loadChunkTicks(col.extraNbt());   // 저장돼 있던 예약 틱을 다시 건다(남은 틱 기준)
        }
        if (col.freshlyGenerated()) {
            col.freshlyGenerated(false);
            try {
                org.domi.furnace.server.entity.spawn.NaturalSpawner.spawnMobsForChunkGeneration(level(), cp.x(), cp.z(), level().worldSeed());
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(Region.class).warn("[SPAWN] 청크 생성 동물 스폰 실패 {}", cp, e);
            }
        }
        if (Regionizer.STATS) {
            long t3 = System.nanoTime();
            INSTALL_STATS[0]++;
            INSTALL_STATS[1] += t1 - t0;
            INSTALL_STATS[2] += t2 - t1;
            INSTALL_STATS[3] += t3 - t2;
        }
        f.complete(col);
    }

    /** 그 청크의 엔티티 묶음(entities/)을 IO 풀에서 읽어 와 넣는다 — 그 사이 청크가 내려갔으면 버린다. 디스크가 없으면 바로 끝. */
    private void loadEntities(ChunkPos cp, ChunkColumn col) {
        if (!(world.worldSource() instanceof org.domi.furnace.server.world.gen.AnvilWorldSource)) {
            col.entitiesLoaded(true);
            return;
        }
        world.loadEntitiesAsync(cp.x(), cp.z()).whenComplete((root, err) -> world.runAtChunk(cp, r -> {
            if (world.loadedChunk(cp.x(), cp.z()) == col) r.level().loadChunkEntities(col, root);
        }));
    }

    /** 이 region 스레드에서 그 청크가 이미 로드돼 있는가. 클라 요청이 임의 좌표의 청크를 새로 생성시키지 못하게 하는 게이트용. */
    public boolean chunkLoaded(ChunkPos cp) {
        world.assertOwns(this);
        return ownedChunk(cp.x(), cp.z()) != null;
    }

    /** 거부된 요청의 클라 예측을 되돌린다 — 로드된 청크면 서버의 실제 블록 상태를 actor에게 다시 보낸다(미로드면 보낼 게 없음). */
    public void resendBlock(Player actor, int bx, int by, int bz) {
        world.assertOwns(this);
        ChunkColumn col = ownedChunk(bx >> 4, bz >> 4);
        if (col == null) return;
        actor.send(new BlockUpdate(bx, by, bz, col.getBlock(bx & 15, by, bz & 15)));
    }

    /** 이 region 의 블록 레벨(처음 쓸 때 만든다 — 블록 자료 적재가 무겁다). */
    public RegionLevel level() {
        RegionLevel l = level;
        if (l == null) level = l = new RegionLevel(this, world);
        return l;
    }

    void markChunkDirty(ChunkPos cp) { dirtyChunks.add(cp); }

    /**
     * 이 region 스레드에서 플레이어 파괴(바닐라 ServerPlayerGameMode.destroyBlock 의 월드 부분): 다른 플레이어에게 파괴 효과(2001),
     * removeBlock(이웃 갱신 포함). 변경 방송은 틱 끝. breaker 가 null 이면 효과를 모두에게.
     */
    public boolean breakBlock(Player breaker, int bx, int by, int bz) {
        world.assertOwns(this);
        chunkAt(ChunkPos.ofBlock(bx, bz));   // 로드 보장(호출 쪽이 보통 이미 확인)
        RegionLevel l = level();
        org.domi.furnace.server.block.Pos p = new org.domi.furnace.server.block.Pos(bx, by, bz);
        int state = l.getBlockState(p);
        org.domi.furnace.server.block.BlockActor except = breaker == null ? null : new org.domi.furnace.server.block.PlayerActor(breaker);
        l.levelEvent(except, 2001, p, state);
        return l.removeBlock(p, false);
    }

    /** 테스트·도구용: 파괴하고 바로 방송한다. */
    public void breakBlock(int bx, int by, int bz) {
        breakBlock(null, bx, by, bz);
        level().broadcastChanges();
    }

    /** 이 region 소속 플레이어(읽기 전용 — region 스레드에서만). */
    java.util.Collection<Player> playersView() { return java.util.Collections.unmodifiableCollection(players.values()); }

    /** 이 region 소속 플레이어 중 그 상자를 열어 둔 사람의 화면을 닫는다(§3.5). 다른 region 의 플레이어는 틱의 stillValid 검사가 닫는다. */
    void closeChestScreens(int bx, int by, int bz) {
        for (Player pl : players.values()) {
            var menu = pl.containerMenu();
            int[] open = menu == null ? null : menu.containerBlockPos();
            if (open != null && open[0] == bx && open[1] == by && open[2] == bz) {
                var host = pl.inventoryHost();
                if (host != null) host.closeContainer(menu);
            }
        }
    }

    /** 이 region 스레드에서 블록 조회(0x38 체스트 판별용). */
    public int getBlock(int x, int y, int z) {
        world.assertOwns(this);
        return chunkAt(ChunkPos.ofBlock(x, z)).getBlock(x & 15, y, z & 15);
    }

    /** 바이옴 quart 의 레지스트리 id(청크가 없거나 이 region 것이 아니면 -1). 이 region 스레드에서만. */
    public int biomeIdAtQuart(int qx, int qy, int qz) {
        world.assertOwns(this);
        ChunkColumn col = ownedChunk(qx >> 2, qz >> 2);
        if (col == null) return -1;
        int sec = ((qy << 2) - col.minY()) >> 4;
        if (sec < 0 || sec >= col.sections().length) return -1;
        return col.sections()[sec].getBiome(qx & 3, qy & 3, qz & 3);
    }

    /** 이 region 스레드에서 체스트 블록엔티티 조회(없으면 null). */
    public ChestBlockEntity chestAt(int x, int y, int z) {
        world.assertOwns(this);
        return chunkAt(ChunkPos.ofBlock(x, z)).chestAt(x, y, z);
    }

    /** 체스트 슬롯 갱신(Container Click). 존재하지 않으면 무시. 청크 dirty 마크(영속, §3.6). */
    public void setChestItem(int cx, int cy, int cz, int slot, ItemStack item) {
        world.assertOwns(this);
        ChunkPos cp = ChunkPos.ofBlock(cx, cz);
        ChestBlockEntity chest = chunkAt(cp).chestAt(cx, cy, cz);
        if (chest == null || slot < 0 || slot >= chest.items.length) return;
        chest.items[slot] = item;
        chunkAt(cp).markDirty();
        dirtyChunks.add(cp);
        chest.setChanged(level());   // 비교기에 알린다
    }

    /** 용기 블록 엔티티 내용이 바뀌었음 — 청크를 저장 대상으로, 비교기에 알린다. 이 region 이 가진 블록이어야 한다. */
    public void markBlockEntityChanged(org.domi.furnace.server.block.entity.BlockEntity be) {
        world.assertOwns(this);
        var p = be.pos();
        ChunkPos cp = ChunkPos.ofBlock(p.x(), p.z());
        chunkAt(cp).markDirty();
        dirtyChunks.add(cp);
        be.setChanged(level());
    }

    /** 상자 내용이 바뀌었음(전리품 채우기 등) — 청크를 저장 대상으로. */
    public void markChestChanged(int cx, int cy, int cz) {
        world.assertOwns(this);
        ChunkPos cp = ChunkPos.ofBlock(cx, cz);
        chunkAt(cp).markDirty();
        dirtyChunks.add(cp);
        ChestBlockEntity chest = chunkAt(cp).chestAt(cx, cy, cz);
        if (chest != null) chest.setChanged(level());
    }

    /** 면 인덱스(0~5) → 설치 위치 오프셋 {dx,dy,dz}. 0down 1up 2north 3south 4west 5east. */
    public static int[] placeOffset(int face) {
        return switch (face) {
            case 0 -> new int[]{0, -1, 0};
            case 1 -> new int[]{0, 1, 0};
            case 2 -> new int[]{0, 0, -1};
            case 3 -> new int[]{0, 0, 1};
            case 4 -> new int[]{-1, 0, 0};
            case 5 -> new int[]{1, 0, 0};
            default -> new int[]{0, 0, 0};
        };
    }

    /**
     * 도구·테스트용 고정 상태 설치(BlockItem.place 의 상태 고르기 없이): 대상이 바뀔 수 있는 칸이고 엔티티에 막히지 않으면
     * 플래그 11 로 놓고 바로 방송한다. 플레이어 설치는 BlockPlacement 경로를 탄다.
     */
    public boolean placeBlock(int bx, int by, int bz, int stateId) {
        world.assertOwns(this);
        if (org.domi.furnace.server.block.BlockInfo.get().isAir(stateId)) return false;
        chunkAt(ChunkPos.ofBlock(bx, bz));
        RegionLevel l = level();
        var p = new org.domi.furnace.server.block.Pos(bx, by, bz);
        if (!org.domi.furnace.server.block.BlockInfo.get().replaceable(l.getBlockState(p))) return false;
        if (!l.isUnobstructed(stateId, p, null)) return false;
        boolean ok = l.setBlock(p, stateId, 11);
        l.broadcastChanges();
        return ok;
    }

    /** 로드된 dirty 청크 전부 저장(스냅샷→IO write), dirty clear. */
    public void saveDirtyChunks() {
        autosaveEntityQueue = null;
        autosaveChunkQueue = null;
        saveEntities();
        if (dirtyChunks.isEmpty()) return;
        for (ChunkPos cp : new ArrayList<>(dirtyChunks)) {
            ChunkColumn col = ownedChunk(cp.x(), cp.z());
            if (col != null && col.dirty()) {
                writeChunkTicks(cp.x(), cp.z(), col, false);
                world.saveChunkAsync(ChunkSnapshot.forSave(cp.x(), cp.z(), col));
                col.clearDirty();
            }
            dirtyChunks.remove(cp);
        }
    }

    // 자동 저장은 한 틱에 몰아 하지 않고 틱마다 시간 예산(AUTOSAVE_BUDGET_NANOS) 안에서 나눠 한다 — 청크가 많이 바뀐 서버에서 5분마다 틱이 튀던 것.
    private static final long AUTOSAVE_BUDGET_NANOS = 4_000_000L;
    private java.util.ArrayDeque<Long> autosaveEntityQueue;
    private java.util.ArrayDeque<ChunkPos> autosaveChunkQueue;

    private void beginAutosave() {
        var io = world.ioPool();
        if (io != null) io.execute(org.domi.furnace.server.map.MapStore::saveDirty);   // 지도 데이터는 IO 풀에서(바뀐 것만)
        autosaveEntityQueue = new java.util.ArrayDeque<>(present);
        autosaveChunkQueue = new java.util.ArrayDeque<>(dirtyChunks);
    }

    private void stepAutosave() {
        long deadline = System.nanoTime() + AUTOSAVE_BUDGET_NANOS;
        Long key;
        while (autosaveEntityQueue != null && (key = autosaveEntityQueue.poll()) != null) {
            int cx = Regionizer.chunkX(key), cz = Regionizer.chunkZ(key);
            ChunkColumn col = level == null ? null : ownedChunk(cx, cz);
            if (col != null) {
                var root = level.chunkEntitiesForSave(col, cx, cz, false);
                if (root != null) world.saveEntitiesAsync(cx, cz, root);
            }
            if (System.nanoTime() > deadline) return;
        }
        ChunkPos cp;
        while (autosaveChunkQueue != null && (cp = autosaveChunkQueue.poll()) != null) {
            ChunkColumn col = ownedChunk(cp.x(), cp.z());
            if (col != null && col.dirty()) {
                writeChunkTicks(cp.x(), cp.z(), col, false);
                world.saveChunkAsync(ChunkSnapshot.forSave(cp.x(), cp.z(), col));
                col.clearDirty();
            }
            dirtyChunks.remove(cp);
            if (System.nanoTime() > deadline) return;
        }
        autosaveEntityQueue = null;
        autosaveChunkQueue = null;
    }

    /** 로드된 청크의 엔티티를 저장한다(엔티티가 있거나 디스크에 있던 청크 — 바닐라 자동 저장의 storeChunkSections). */
    private void saveEntities() {
        if (level == null) return;
        for (long key : present) {
            int cx = Regionizer.chunkX(key), cz = Regionizer.chunkZ(key);
            ChunkColumn col = ownedChunk(cx, cz);
            if (col == null) continue;
            var root = level.chunkEntitiesForSave(col, cx, cz, false);
            if (root != null) world.saveEntitiesAsync(cx, cz, root);
        }
    }

    /** 구독자 0인 "있는 청크"를 grace 후 퇴출(dirty면 저장). 로드 중인 청크는 끝날 때까지 둔다. 매 틱 호출. */
    private void evictIdleChunks() {
        List<Long> drop = null;
        for (long key : present) {
            ChunkPos cp = new ChunkPos(Regionizer.chunkX(key), Regionizer.chunkZ(key));
            if (!world.subscribers(cp).isEmpty() || world.loadingChunk(key) != null) { evictGrace.remove(cp); continue; }
            int g = evictGrace.merge(cp, 1, Integer::sum);
            if (g >= world.chunkEvictionGraceTicks()) {
                if (drop == null) drop = new ArrayList<>();
                drop.add(key);
            }
        }
        if (drop == null) return;
        for (long key : drop) {
            int cx = Regionizer.chunkX(key), cz = Regionizer.chunkZ(key);
            ChunkPos cp = new ChunkPos(cx, cz);
            ChunkColumn col = world.loadedChunk(cx, cz);
            if (col != null) {
                if (level != null) {   // 엔티티는 저장한 뒤 내린다(UNLOADED_TO_CHUNK)
                    var root = level.chunkEntitiesForSave(col, cx, cz, true);
                    if (root != null) world.saveEntitiesAsync(cx, cz, root);
                }
                for (var be : col.blockEntities().values()) be.setRemoved();   // 틱 목록에서 빠진다(다시 불러오면 새로 만든다)
                // 예약 틱이 남아 있으면 바뀐 게 없어도 저장한다(내리면 사라지므로)
                if (writeChunkTicks(cx, cz, col, true) || col.dirty()) {
                    world.saveChunkAsync(ChunkSnapshot.forSave(cx, cz, col));
                    col.clearDirty();
                }
                world.removeChunk(cx, cz);
            }
            if (level != null) level.chunkUnloaded();
            evictGrace.remove(cp);
            dirtyChunks.remove(cp);
            rz().release(this, cx, cz);
        }
    }

    /** 그 청크의 예약 틱을 청크 NBT(block_ticks·fluid_ticks)에 쓴다. 예약이 있었는가. remove 면 레벨에서 꺼낸다. */
    private boolean writeChunkTicks(int cx, int cz, ChunkColumn col, boolean remove) {
        if (level == null) return false;
        boolean had = level.hasChunkTicks(cx, cz);
        if (!had && col.extraNbt() == null) return false;
        if (col.extraNbt() == null) col.extraNbt(new org.domi.furnace.server.net.io.nbt.NbtCompound());
        level.saveChunkTicks(cx, cz, col.extraNbt(), remove);
        return had;
    }

    int dirtyChunkCount() {
        return dirtyChunks.size();
    }

    /** 이 region 스레드에서 플레이어 편입. */
    public void adopt(Player p) {
        world.assertOwns(this);
        players.put(p.entityId(), p);
        world.playerOnline(p);   // 멱등 — 접속 등록이 누락돼도 탭/가시성 기준 집합에 항상 포함
    }

    /** 이 region 스레드에서 매 틱(게임 틱) 호출. */
    /** 틱 단계별 시간(-Dfurnace.tickstats): 0 블록틱, 1 조명, 2 플레이어 처리, 3 보임, 4 엔티티 넘김·정리·저장. 합계·최대(ns). */
    final long[] phaseSum = new long[5], phaseMax = new long[5];
    private long lapT;

    private void lap(int i) {
        if (!Regionizer.STATS) return;
        long n = System.nanoTime();
        long d = n - lapT;
        phaseSum[i] += d;
        if (d > phaseMax[i]) phaseMax[i] = d;
        lapT = n;
    }

    public void tick() {
        world.assertOwns(this);
        if (Regionizer.STATS) lapT = System.nanoTime();
        if (level != null) {   // 바닐라 ServerLevel.tick: 블록·유체 예약 틱 → 청크 틱(무작위 틱) → 청크 변경 방송 → 블록 이벤트 → 엔티티
            int n = collectTickingChunks();
            level.tickBlocks(tickingChunks, n, this::blockEntityTicking, pl -> players.containsKey(pl.entityId()));
            lap(0);
            level.runLightUpdates();
            lap(1);
        }
        List<Player> handoff = new ArrayList<>();
        var it = players.values().iterator();
        while (it.hasNext()) {
            Player p = it.next();
            if (p.isDisconnected()) {
                it.remove();
                for (ChunkPos cp : p.loadedChunks()) world.unsubscribeChunk(cp, p);
                for (Integer eid : p.tracked().keySet()) world.removeTracker(eid, p);
                // 저장은 ConnectionHandler.channelInactive가 IO 위치+인벤토리로 수행(여기선 0,0,0 위험).
                world.playerOffline(p.entityId());   // 다른 관찰자는 grace 후 탭에서 뺀다
                broadcastRemove(p.entityId());
                if (level != null) level.playerLeft(p);
                continue;
            }
            p.tickDropSpam();
            p.tickCooldowns();
            if (processMoves(p) && level != null) level.playerMoved(p);   // 바닐라 ChunkMap.move — 이동 패킷마다 엔티티 보임을 다시 본다
            p.drainPending();
            tickVitals(p);
            org.domi.furnace.server.map.MapItems.tick(p, MapTerrain::new, p.dimension(), world.dayTime());   // Inventory.tick 의 지도 + 갱신 패킷
            if (p.takeXpDelay > 0) p.takeXpDelay--;
            org.domi.furnace.server.entity.Consuming.tick(p, this);
            p.syncLivingFlags();   // 사용 중(먹기·활·방패) 플래그를 다른 사람에게
            touchEntities(p);
            tickBreaking(p);
            broadcastInventory(p);
            ChunkPos c = p.chunkPos();
            Region owner = rz().ownerOf(c.x(), c.z());
            if (owner != this && (owner == null || !rz().merging(this, owner))) {
                // 텔레포트 등으로 다른 region(또는 아무도 없는 곳)에 섰다 → 그 청크 주인에게 넘긴다
                it.remove();
                handoff.add(p);
                continue;
            }
            updateChunks(p);
            sendQueuedChunks(p);
        }
        lap(2);
        if (!players.isEmpty()) {
            // 시야가 닿는 플레이어는 모두 같은 region 에 있다(시야 청크를 가진 region 끼리는 합쳐진다).
            List<EntityView> candidates = new ArrayList<>(players.size());
            for (Player p : players.values()) candidates.add(EntityView.of(p));
            for (Player p : players.values()) {
                updateVisibility(p, candidates);
            }
            if (++latencyCounter >= LATENCY_BROADCAST_TICKS) {
                latencyCounter = 0;
                broadcastLatencies();
            }
        }
        lap(3);
        for (Player p : handoff) {
            ChunkPos c = p.chunkPos();
            world.runAtChunk(c, r -> r.adopt(p));
        }
        if (level != null) level.flushAcks();   // 바닐라: 연결 틱 첫머리의 블록 변경 ack — 이 틱의 방송 뒤
        handOffEntities();
        evictIdleChunks();
        if (++autosaveCounter >= world.autosaveTicks() && world.autosaveEnabled()) {
            autosaveCounter = 0;
            beginAutosave();
        }
        if (autosaveEntityQueue != null || autosaveChunkQueue != null) stepAutosave();
        lap(4);
    }

    private void startTracking(Player a, int eid, double[] last) {
        a.tracked().put(eid, last);
        world.addTracker(eid, a);
    }

    private boolean stopTracking(Player a, int eid) {
        if (a.tracked().remove(eid) == null) return false;
        world.removeTracker(eid, a);
        return true;
    }

    // ------------------------------------------------------------------ 생존(낙하·물·공기·허기)

    /** 이 region 이 가진 청크의 블록(없으면 -1). 마지막 청크를 기억해 같은 청크 연속 조회를 싸게 한다. */
    private final PlayerProbe.Blocks blocks = new PlayerProbe.Blocks() {
        private ChunkPos lastPos;
        private ChunkColumn last;

        @Override
        public int stateAt(int x, int y, int z) {
            int cx = x >> 4, cz = z >> 4;
            ChunkColumn c;
            if (lastPos != null && lastPos.x() == cx && lastPos.z() == cz) {
                c = last;
            } else {
                lastPos = new ChunkPos(cx, cz);
                last = c = ownedChunk(cx, cz);
            }
            if (c == null) return -1;
            if (y < ChunkColumn.MIN_Y || y > ChunkColumn.MAX_Y) return ChunkSection.AIR;
            return c.getBlock(x & 15, y, z & 15);
        }
    };

    /** 이번 틱까지 들어온 위치 패킷들을 순서대로 — 점프·이동 소모와 낙하 판정(바닐라 handleMovePlayer 마다). */
    private boolean processMoves(Player p) {
        PlayerProbe probe = world.probe();
        Player.MoveSample m;
        boolean any = false;
        while ((m = p.pollMove()) != null) {
            any = true;
            Player.MoveSample prev = p.moveAnchor();
            p.moveAnchor(m);
            if (m.teleport() || prev == null || probe == null) continue;
            boolean sneaking = p.sneaking();
            double height = sneaking ? PlayerProbe.HEIGHT_CROUCHING : PlayerProbe.HEIGHT_STANDING;
            double eye = sneaking ? PlayerProbe.EYE_CROUCHING : PlayerProbe.EYE_STANDING;
            double dx = m.x() - prev.x(), dy = m.y() - prev.y(), dz = m.z() - prev.z();
            String landing = probe.onPosLegacy(blocks, m.x(), m.y(), m.z(), m.onGround());
            boolean inWater = probe.inWater(blocks, m.x(), m.y(), m.z(), height);
            boolean eyeInWater = probe.eyeInWater(blocks, m.x(), m.y(), m.z(), eye);
            String climbable = probe.climbable(blocks, m.x(), m.y(), m.z());
            boolean waterOnPath = dy < 0 && probe.waterOnPath(blocks, m.x(), prev.y(), m.y(), m.z());
            p.vitals().onMove(dx, dy, dz, prev.onGround(), m.onGround(), p.sprinting(), sneaking,
                    inWater, eyeInWater, climbable, landing, waterOnPath);
        }
        return any;
    }

    /**
     * Player.touch(바닐라 aiStep 끝): 살아 있고 관전자가 아니면 몸통을 (1, 0.5, 1) 넓힌 상자에 걸친 엔티티마다 playerTouch(아이템 줍기).
     */
    private void touchEntities(Player p) {
        if (level == null || level.entities().isEmpty()) return;
        if (p.vitals().health() <= 0.0F || p.gameMode() == GameMode.SPECTATOR) return;
        double h = p.sneaking() ? PlayerProbe.HEIGHT_CROUCHING : PlayerProbe.HEIGHT_STANDING;
        var box = new org.domi.furnace.server.entity.AABB(p.x() - 0.3, p.y(), p.z() - 0.3, p.x() + 0.3, p.y() + h, p.z() + 0.3)
                .inflate(1.0, 0.5, 1.0);
        for (var e : level.getEntities(null, box, null)) {
            if (!e.isRemoved()) e.playerTouch(p);
        }
    }

    /** 다른 region 의 청크로 들어간 엔티티를 그 주인에게 넘긴다(추적 상태는 그대로 — 그쪽에 없는 보는 사람은 grace 뒤 빠진다). */
    private void handOffEntities() {
        if (level == null || level.entities().isEmpty()) return;
        for (var e : level.entities().tickSnapshot()) {
            int cx = e.chunkX(), cz = e.chunkZ();
            Region owner = rz().ownerOf(cx, cz);
            if (owner == null) {   // 아무도 갖지 않은(로드되지 않은) 청크 — 저장해 내린다
                if (world.loadedChunk(cx, cz) == null && world.loadingChunk(Regionizer.chunkKey(cx, cz)) == null) level.evictToDisk(e);
                continue;
            }
            if (owner == this || rz().merging(this, owner)) continue;
            level.entities().remove(e);
            world.runAtChunk(new ChunkPos(cx, cz), r -> r.level().adoptEntity(e));
        }
    }

    // ------------------------------------------------------------------ 청크 틱 대상(ServerChunkCache.tickChunks)

    private long[] tickingChunks = new long[64];

    /**
     * 무작위 틱 받을 청크: 이 region 이 가진 로드된 청크 중, 관전자가 아닌 플레이어가 시뮬레이션 거리 안(체비셰프 ≤ min(거리, 11) − 1,
     * 자연 스폰 범위 8 청크 안)이고 청크 가운데에서 128 블록 안(anyPlayerCloseEnoughForSpawning)인 것. 순서는 레벨이 섞는다.
     */
    private int collectTickingChunks() {
        if (players.isEmpty()) return 0;
        org.domi.furnace.server.block.BlockBehaviors.ofBlock(0);   // 무작위 틱 표를 섹션에 건다
        int sim = Math.clamp(world.levelServices().simulationDistance(), 2, 11) - 1;
        int range = Math.min(sim, 8);
        int n = 0;
        for (long key : present) {
            int cx = Regionizer.chunkX(key), cz = Regionizer.chunkZ(key);
            if (!closeToPlayer(cx, cz, range)) continue;
            ChunkColumn col = ownedChunk(cx, cz);
            if (col == null) continue;
            col.inhabitedTime(col.inhabitedTime() + 1);   // LevelChunk.incrementInhabitedTime(1)
            if (n == tickingChunks.length) tickingChunks = java.util.Arrays.copyOf(tickingChunks, n * 2);
            tickingChunks[n++] = key;
        }
        return n;
    }

    /**
     * 그 청크가 블록 엔티티 틱을 받는가 — 바닐라 LevelChunk.isTicking: 엔티티 틱 단계(플레이어 표가 시뮬레이션 거리 - 1 안,
     * 체비셰프)인 청크. 키는 (x << 32 | z).
     */
    private boolean blockEntityTicking(long key) {
        int cx = (int) (key >> 32), cz = (int) key;
        int range = Math.max(world.levelServices().simulationDistance(), 2) - 1;
        for (Player p : players.values()) {
            int pcx = (int) Math.floor(p.x()) >> 4, pcz = (int) Math.floor(p.z()) >> 4;
            if (Math.max(Math.abs(pcx - cx), Math.abs(pcz - cz)) <= range) return true;
        }
        return false;
    }

    private boolean closeToPlayer(int cx, int cz, int chunkRange) {
        double mx = cx * 16 + 8, mz = cz * 16 + 8;
        for (Player p : players.values()) {
            if (p.gameMode() == GameMode.SPECTATOR) continue;
            int pcx = (int) Math.floor(p.x()) >> 4, pcz = (int) Math.floor(p.z()) >> 4;
            if (Math.max(Math.abs(pcx - cx), Math.abs(pcz - cz)) > chunkRange) continue;
            double dx = mx - p.x(), dz = mz - p.z();
            if (dx * dx + dz * dz < 16384.0) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ 블록 부수기(ServerPlayerGameMode)

    /** 플레이어가 살아 있는 엔티티를 때린다(Player.attack 의 기본 부분): 손 아이템의 attack_damage 속성 합 + 1, 쿨다운은 가득 찬 것으로 본다. */
    public void attack(Player p, org.domi.furnace.server.entity.LivingEntity target) {
        world.assertOwns(this);
        if (!players.containsKey(p.entityId()) || !target.isAlive()) return;
        var inv = p.inventory();
        ItemStack held = inv.get(inv.heldWindowSlot());
        float damage = 1.0F + org.domi.furnace.server.item.ItemTools.attackDamageBonus(held);
        // TODO(공격 쿨다운·치명타·인챈트·내구도): attackStrengthTicker, 낙하 치명타, sharpness, 무기 내구 감소
        p.vitals().causeFoodExhaustion(0.1F);
        p.setLastHurtMob(target);
        target.hurtBy(new org.domi.furnace.server.entity.Entity.Damage("player_attack", false, false, false), damage, p);
    }

    /** 생물이 아닌 엔티티(액자·그림·수정 …)를 때린다: skipAttackInteraction 이면 그쪽이 처리하고, 아니면 기본 피해. */
    public void attackEntity(Player p, org.domi.furnace.server.entity.Entity target) {
        world.assertOwns(this);
        if (!players.containsKey(p.entityId()) || target.isRemoved()) return;
        if (target.skipAttackInteraction(p)) return;
        var inv = p.inventory();
        ItemStack held = inv.get(inv.heldWindowSlot());
        float damage = 1.0F + org.domi.furnace.server.item.ItemTools.attackDamageBonus(held);
        p.vitals().causeFoodExhaustion(0.1F);
        target.hurtBy(new org.domi.furnace.server.entity.Entity.Damage("player_attack", false, false, false), damage, p);
    }

    /**
     * 플레이어 아이템 사용(use_item — 바닐라 handleUseItem): ack 를 걸고, 보낸 시선이 다르면 돌린 뒤 ServerPlayerGameMode.useItem.
     * 바뀐 손·갑옷 칸은 연결의 인벤토리로. 그 플레이어가 이 region 소속일 때만.
     */
    public void handleUseItem(Player p, int hand, int seq, float yRot, float xRot) {
        world.assertOwns(this);
        level().ackBlockChangesUpTo(p, seq);
        if (!players.containsKey(p.entityId())) return;
        var actor = breakerActor(p);
        if (actor.itemInHand(hand).isEmpty()) return;
        if (p.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR && org.domi.furnace.server.entity.Consuming.tryStart(p, hand, actor.itemInHand(hand))) return;
        float y = wrapDegrees(yRot), x = wrapDegrees(xRot);
        if (x != p.pitch() || y != p.yaw()) p.applyRotation(y, Math.clamp(x, -90.0F, 90.0F), p.onGround());   // absRotateTo
        actor = breakerActor(p);
        if (p.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR && !actor.isOnCooldown(actor.itemInHand(hand).itemId())
                && org.domi.furnace.server.entity.RangedWeapons.use(level(), actor, p, hand)) {   // 활·쇠뇌·낚싯대·삼지창
            writeBackHand(p, actor);
            return;
        }
        org.domi.furnace.server.block.ItemUse.useItem(level(), actor, hand);
        // shouldSwing(SUCCESS) 는 서버 쪽 결과(CONSUME)에서 나오지 않는다
        writeBackHand(p, actor);
    }

    /** 사용 끝(player_action 5 — ServerPlayer.releaseUsingItem): 활·쇠뇌면 발사·장전, 아니면 그냥 멈춘다. */
    public void handleReleaseUsing(Player p) {
        world.assertOwns(this);
        if (p.usingHand >= 0 && players.containsKey(p.entityId())) {
            var actor = breakerActor(p);
            if (org.domi.furnace.server.entity.RangedWeapons.usesRelease(actor.itemInHand(p.usingHand))) {
                org.domi.furnace.server.entity.RangedWeapons.release(level(), actor, p);
                writeBackHand(p, actor);
            }
        }
        org.domi.furnace.server.entity.Consuming.stop(p);
    }

    /**
     * Player.drop(stack, dropAround, includeThrower) + ServerPlayer.drop: 눈 높이 - 0.3 에서 줍기 지연 40 으로 던진다. dropAround 면 아무
     * 쪽으로(죽을 때), 아니면 보는 쪽으로 0.3 + 흔들림. 속도는 플레이어 난수(아이템 생성자는 레벨 난수 두 번을 먼저 쓴다).
     */
    public org.domi.furnace.server.entity.ItemEntity playerDrop(Player p, ItemStack stack, boolean dropAround, boolean includeThrower) {
        world.assertOwns(this);
        if (stack.isEmpty()) return null;
        RegionLevel l = level();
        double eye = p.y() + (p.sneaking() ? PlayerProbe.EYE_CROUCHING : PlayerProbe.EYE_STANDING) - 0.3F;
        var ie = new org.domi.furnace.server.entity.ItemEntity(l, l.nextEntityId(), p.x(), eye, p.z(), stack);
        ie.setPickUpDelay(40);
        if (includeThrower) ie.setThrower(p.uuid());
        var r = p.random();
        if (dropAround) {
            float f = r.nextFloat() * 0.5F;
            float g = r.nextFloat() * (float) (Math.PI * 2);
            ie.setDeltaMovement(-org.domi.furnace.server.block.Dir.sin(g) * f, 0.2F, org.domi.furnace.server.block.Dir.cos(g) * f);
        } else {
            float xr = p.pitch() * (float) (Math.PI / 180.0), yr = p.yaw() * (float) (Math.PI / 180.0);
            float g = org.domi.furnace.server.block.Dir.sin(xr), h = org.domi.furnace.server.block.Dir.cos(xr);
            float i = org.domi.furnace.server.block.Dir.sin(yr), j = org.domi.furnace.server.block.Dir.cos(yr);
            float k = r.nextFloat() * (float) (Math.PI * 2);
            float m = 0.02F * r.nextFloat();
            ie.setDeltaMovement((double) (-i * h * 0.3F) + Math.cos(k) * (double) m,
                    (double) (-g * 0.3F + 0.1F + (r.nextFloat() - r.nextFloat()) * 0.1F),
                    (double) (j * h * 0.3F) + Math.sin(k) * (double) m);
        }
        l.addFreshEntity(ie);
        if (includeThrower) {   // ITEM_DROPPED · DROP 통계
            p.awardStat("minecraft:dropped:" + org.domi.furnace.server.item.ItemTools.itemName(stack), stack.count());
            p.awardStat("minecraft:drop", 1);
        }
        return ie;
    }

    /** 엔티티 이벤트(부서짐 47·48 등)를 그 플레이어 자신과 추적 중인 플레이어에게. */
    public void broadcastEntityEvent(Player p, byte event) {
        var pkt = new org.domi.furnace.server.net.protocol.play.EntityEvent(p.entityId(), event);
        p.send(pkt);
        for (Player o : players.values()) if (o != p && o.tracked().containsKey(p.entityId())) o.send(pkt);
    }

    /** Mth.wrapDegrees(float). */
    private static float wrapDegrees(float v) {
        float f = v % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }

    /** 플레이어 블록 부수기 요청(player_action 0·1·2). 그 플레이어가 이 region 소속일 때만 — ack 는 부르는 쪽이. */
    public void handleBlockBreakAction(Player p, org.domi.furnace.server.block.Pos pos, int action, boolean mayInteract) {
        world.assertOwns(this);
        if (!players.containsKey(p.entityId())) return;
        var actor = breakerActor(p);
        p.breaking().handleAction(level(), actor, breakHost(p), pos, action, level().maxBuildHeight(), mayInteract);
        writeBackHand(p, actor);
    }

    private void tickBreaking(Player p) {
        var holder = new org.domi.furnace.server.block.BreakerActor[1];
        p.breaking().tick(level(), () -> holder[0] = breakerActor(p), breakHost(p));
        if (holder[0] != null) writeBackHand(p, holder[0]);
    }

    /** 부수는 플레이어 행위자 — 손·머리 장비 사본(연결의 인벤토리에서), 눈이 물에 잠겼는지(지금 블록으로). */
    private org.domi.furnace.server.block.BreakerActor breakerActor(Player p) {
        Player.InventoryHost inv = p.inventoryHost();
        int held = inv == null ? 36 : 36 + inv.selected();
        ItemStack main = inv == null ? ItemStack.EMPTY : inv.slot(held), off = inv == null ? ItemStack.EMPTY : inv.slot(45),
                head = inv == null ? ItemStack.EMPTY : inv.slot(5);
        PlayerProbe probe = world.probe();
        boolean eyeInWater = false;
        if (probe != null && ownedChunk((int) Math.floor(p.x()) >> 4, (int) Math.floor(p.z()) >> 4) != null) {
            double eye = p.sneaking() ? PlayerProbe.EYE_CROUCHING : PlayerProbe.EYE_STANDING;
            eyeInWater = probe.eyeInWater(blocks, p.x(), p.y(), p.z(), eye);
        }
        return new org.domi.furnace.server.block.BreakerActor(p, main, off, head, inv == null ? 0 : inv.permissionLevel(), eyeInWater, held) {
            @Override
            public void drop(ItemStack stack) { playerDrop(p, stack, false, false); }

            @Override
            protected void brokenEvent(int hand, ItemStack broken) {
                var pkt = new org.domi.furnace.server.net.protocol.play.EntityEvent(p.entityId(), (byte) (47 + hand));
                p.send(pkt);
                for (Player o : players.values()) if (o != p && o.tracked().containsKey(p.entityId())) o.send(pkt);
            }
        }.withInventory(inv);
    }

    private org.domi.furnace.server.block.BlockBreaking.Host breakHost(Player p) {
        return new org.domi.furnace.server.block.BlockBreaking.Host() {
            public void sendBlockUpdate(org.domi.furnace.server.block.BlockLevel level, org.domi.furnace.server.block.Pos pos) {
                p.send(new BlockUpdate(pos.x(), pos.y(), pos.z(), level.getBlockState(pos)));
            }

            public boolean allowBreak(org.domi.furnace.server.block.BlockLevel level, org.domi.furnace.server.block.Pos pos, int state) {
                Player.InventoryHost inv = p.inventoryHost();
                return inv == null || inv.allowBreak(pos.x(), pos.y(), pos.z(), state);
            }

            public boolean owns(org.domi.furnace.server.block.Pos pos) { return ownedChunk(pos.x() >> 4, pos.z() >> 4) != null; }
        };
    }

    /** 바뀐 손·갑옷 칸(도구 내구도·부서짐·양동이·장착)을 연결의 인벤토리에 반영한다. */
    private static void writeBackHand(Player p, org.domi.furnace.server.block.BreakerActor actor) {
        Player.InventoryHost inv = p.inventoryHost();
        if (inv == null) return;
        if (actor.handChanged(org.domi.furnace.server.block.BlockActor.MAIN_HAND)) {
            inv.replace(actor.heldWindowSlot(), actor.originalMainHand(), actor.itemInHand(org.domi.furnace.server.block.BlockActor.MAIN_HAND));
        }
        if (actor.handChanged(org.domi.furnace.server.block.BlockActor.OFF_HAND)) {
            inv.replace(45, actor.originalOffHand(), actor.itemInHand(org.domi.furnace.server.block.BlockActor.OFF_HAND));
        }
        for (int ws = 5; ws <= 8; ws++) {
            if (actor.armorChanged(ws)) inv.replace(ws, actor.originalArmor(ws), actor.equipment(ws));
        }
    }

    /**
     * 열린 창(없으면 인벤토리 창)의 틱 처리(ServerPlayer.tick): 창이 더는 유효하지 않으면(상자가 사라졌거나 멀어짐) 닫고,
     * AbstractContainerMenu.broadcastChanges 로 클라이언트가 아는 것과 다른 칸·커서만 보낸다(inventory_changed 는 슬롯 리스너가 건다).
     */
    private void broadcastInventory(Player p) {
        var menu = p.containerMenu();
        if (menu == null) return;
        var host = p.inventoryHost();
        if (menu != p.inventoryMenu() && host != null) {
            int[] bp = menu.containerBlockPos();
            if (bp != null) {
                // 블록에 매인 창의 유효성(블록 존재·거리·청크)은 그 블록의 region 스레드에서 — 그 region 의 큐에서 이미 대기 중인 클릭 뒤에 판정된다
                var m = menu;
                world.runAt(bp[0], bp[2], r -> host.validateBlockMenu(r, m));
            } else if (!host.menuStillValid(menu)) {
                host.closeContainer(menu);
            }
        }
        synchronized (p.menuLock()) {
            menu = p.containerMenu();
            menu.broadcastChanges();
        }
    }

    /** 지도가 읽는 지형: 이 region 이 가진 로드된 청크만(마지막 청크를 기억해 열 단위 연속 조회를 싸게). */
    private final class MapTerrain implements org.domi.furnace.server.map.MapRenderer.Terrain {
        private int lastCx = Integer.MIN_VALUE, lastCz;
        private ChunkColumn last;

        private ChunkColumn chunk(int cx, int cz) {
            if (cx != lastCx || cz != lastCz || lastCx == Integer.MIN_VALUE) {
                last = ownedChunk(cx, cz);
                lastCx = cx;
                lastCz = cz;
            }
            return last;
        }

        @Override public boolean hasCeiling() { return false; }

        @Override public int minY() { return ChunkColumn.MIN_Y; }

        @Override public boolean chunkAvailable(int cx, int cz) { return chunk(cx, cz) != null; }

        @Override public int surfaceHeight(int x, int z) {
            ChunkColumn c = chunk(x >> 4, z >> 4);
            return c == null ? ChunkColumn.MIN_Y : c.surfaceBlockY(x & 15, z & 15) + 1;
        }

        @Override public int blockState(int x, int y, int z) {
            ChunkColumn c = chunk(x >> 4, z >> 4);
            return c == null || y < ChunkColumn.MIN_Y || y > ChunkColumn.MAX_Y ? 0 : c.getBlock(x & 15, y, z & 15);
        }
    }

    /** 매 틱 생존 상태 — 주변 블록을 보고 공허·익사·허기·회복·사망 틱을 돌린다. */
    private void tickVitals(Player p) {
        PlayerProbe probe = world.probe();
        PlayerVitals.Surroundings s;
        double x = p.x(), y = p.y(), z = p.z();
        if (probe == null || ownedChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4) == null) {
            s = PlayerVitals.Surroundings.NONE;
        } else {
            boolean sneaking = p.sneaking();
            double height = sneaking ? PlayerProbe.HEIGHT_CROUCHING : PlayerProbe.HEIGHT_STANDING;
            double eye = sneaking ? PlayerProbe.EYE_CROUCHING : PlayerProbe.EYE_STANDING;
            s = new PlayerVitals.Surroundings(true, probe.inWater(blocks, x, y, z, height),
                    probe.eyeInWater(blocks, x, y, z, eye), probe.eyeInBubbleColumn(blocks, x, y, z, eye),
                    probe.climbable(blocks, x, y, z), probe.stuckInBlock(blocks, x, y, z, height), ChunkColumn.MIN_Y);
        }
        p.vitals().tick(s);
        if (p.stats() != null) {   // 바닐라 Player.tick 의 시간 통계 (잠은 아직 없어 time_since_rest 는 늘 센다)
            p.awardStat("minecraft:play_time", 1);
            p.awardStat("minecraft:total_world_time", 1);
            if (!p.vitals().isDeadOrDying()) p.awardStat("minecraft:time_since_death", 1);
            if (p.sneaking()) p.awardStat("minecraft:sneak_time", 1);
            p.awardStat("minecraft:time_since_rest", 1);
        }
        var adv = p.advancements();
        if (adv != null) adv.tickPlayer();
    }

    /** 이 region의 타 플레이어들에게서 eid를 디스폰(추적 중이면)하고 탭 목록에서도 뺀다(퇴장). */
    private void broadcastRemove(int eid) {
        for (Player other : players.values()) {
            if (stopTracking(other, eid)) {
                other.send(new RemoveEntities(java.util.List.of(eid)));
            }
            removeFromTabList(other, eid);
        }
    }

    /** 탭 목록에 올려둔 eid가 있으면 PlayerInfoRemove로 빼고 기록을 지운다. */
    private static void removeFromTabList(Player a, int eid) {
        java.util.UUID uuid = a.listed().remove(eid);
        a.missingTicks().remove(eid);
        if (uuid != null) a.send(new PlayerInfoRemove(java.util.List.of(uuid)));
    }

    /** 중심 이동 시 시야 청크를 pending 큐에 적재(+구독), 시야 밖은 언로드. 전송은 sendQueuedChunks가 틱당 스로틀. */
    private void updateChunks(Player p) {
        ChunkPos center = p.chunkPos();
        if (center.equals(p.lastCenter()) && center.equals(p.queuedCenter())) return;
        int vd = Math.min(world.viewDistance(), p.viewDistance()); // 서버 상한 + 클라 요청
        List<ChunkPos> toForget = new ArrayList<>();
        if (!center.equals(p.lastCenter())) {
            // 시야 밖이 된 청크는 대기 청크가 많든 적든 항상 정리한다(구독 해제·대기열 제거). 예전엔 대기가 512를 넘으면 건너뛰어
            // 클라가 버린 청크가 구독에 남고 오래된 위치의 대기 청크가 계속 생성·직렬화됐다.
            var it = p.loadedChunks().iterator();
            while (it.hasNext()) {
                ChunkPos cp = it.next();
                if (Math.abs(cp.x() - center.x()) > vd || Math.abs(cp.z() - center.z()) > vd) {
                    it.remove();
                    world.unsubscribeChunk(cp, p);
                    // 아직 안 보낸(pending) 청크면 forget 패킷 불필요 — 큐서만 제거.
                    if (!p.pendingChunks().remove(cp)) toForget.add(cp);
                }
            }
            p.send(new SetCenterChunk(center.x(), center.z()));
            for (ChunkPos cp : toForget) p.send(new UnloadChunk(cp.x(), cp.z()));
            p.lastCenter(center);
        }
        if (center.equals(p.queuedCenter())) return;
        // 대기열은 언제나 지금 시야 전체를 담고 새 중심 기준으로 정렬한다(대기열은 좌표뿐이라 싸다). 실제 작업량은
        // sendQueuedChunks 의 동시 진행 상한이 막는다 — 예전처럼 대기가 많다고 새 중심의 청크를 안 넣으면 큰 시야에서
        // 플레이어 바로 옆 청크가 대기열에 없어 한참 비어 보였다.
        List<ChunkPos> toQueue = new ArrayList<>();
        for (int dx = -vd; dx <= vd; dx++) {
            for (int dz = -vd; dz <= vd; dz++) {
                ChunkPos cp = new ChunkPos(center.x() + dx, center.z() + dz);
                if (p.loadedChunks().add(cp)) {   // 새로 시야 진입
                    world.subscribeChunk(cp, p);
                    rz().want(cp.x(), cp.z(), this);   // 시야 구역을 이 region 이 갖는다(다른 region 것이면 합칠 대상)
                    toQueue.add(cp);              // 전송 대기열(아직 미전송)
                }
            }
        }
        orderNearestFirst(center, toQueue);
        p.pendingChunks().addAll(toQueue);
        reorderPendingNearestFirst(center, p.pendingChunks());
        p.queuedCenter(center);
    }

    /** chunks를 중심 거리 오름차순 정렬 — 스폰/근접 청크 먼저 전송해 클라가 즉시 "로드된 청크"에 스폰(낙하 방지). */
    static void orderNearestFirst(ChunkPos center, List<ChunkPos> chunks) {
        chunks.sort(java.util.Comparator.comparingInt(cp -> {
            int dx = cp.x() - center.x(), dz = cp.z() - center.z();
            return dx * dx + dz * dz;
        }));
    }

    static void reorderPendingNearestFirst(ChunkPos center, java.util.Set<ChunkPos> pending) {
        if (pending.size() < 2) return;
        List<ChunkPos> ordered = new ArrayList<>(pending);
        orderNearestFirst(center, ordered);
        pending.clear();
        pending.addAll(ordered);
    }

    /** 매 틱: pending 큐서 최대 CHUNKS_PER_TICK개를 nearest-first로 한 배치 전송(vanilla PlayerChunkSender식 스로틀).
     *  스폰 청크가 첫 틱 소량 배치에 들어가 빨리 도착 → 클라가 즉시 안착(289개 일괄 async fan-out 회피).
     *  아직 끝나지 않은 요청이 상한(world.maxChunkSendsInFlight)에 닿으면 더 꺼내지 않는다 — 생성이 수요를 못 따라갈 때
     *  요청이 생성 큐에 한없이 쌓여(FIFO) 플레이어 바로 옆 청크까지 수십 초 밀리는 것을 막는다. 남은 청크는 대기열에 남아
     *  플레이어가 움직이면 새 중심 기준으로 다시 정렬되므로, 다음에 꺼내는 것은 언제나 지금 위치에서 가장 가까운 청크다. */
    private void sendQueuedChunks(Player p) {
        if (p.pendingChunks().isEmpty()) return;
        int budget = Math.min(world.chunksPerTick(), world.maxChunkSendsInFlight() - p.chunkSendsInFlight().get());
        if (budget <= 0) return;
        List<ChunkPos> batch = new ArrayList<>(Math.min(budget, p.pendingChunks().size()));
        var it = p.pendingChunks().iterator();
        while (it.hasNext() && batch.size() < budget) {
            batch.add(it.next());
            it.remove();
        }

        p.send(new ChunkBatchStart());
        // 실제 전송 수(stale 드롭으로 batch.size와 다를 수 있음) — ChunkBatchFinished에 정확히 보고.
        java.util.concurrent.atomic.AtomicInteger sent = new java.util.concurrent.atomic.AtomicInteger();
        List<CompletableFuture<?>> sends = new ArrayList<>(batch.size());
        for (ChunkPos cp : batch) {
            // 구독은 updateChunks에서 큐에 넣을 때 했다. 이 구독이 청크 전달 전까지 블록 변경을 보류한다.
            World.Subscription sub = world.subscriptionOf(cp, p);
            if (sub == null) continue;   // 그 사이 언로드됨 — 보낼 것 없음
            p.chunkSendsInFlight().incrementAndGet();
            CompletableFuture<?> f = world.serializedChunkBody(cp)
                    .whenComplete((b, ex) -> p.chunkSendsInFlight().decrementAndGet())
                    .thenApply(Unpooled::wrappedBuffer)
                    .thenAccept(body -> {
                        // gen 지연 동안 플레이어가 시야 밖으로 이동했거나(재구독 포함) 언로드됐으면 보내봐야 클라가
                        // "not in view range"로 버림 → 드롭. 위치는 volatile이라 완료 스레드서 안전하게 읽음.
                        if (inView(p, cp) && world.isSubscribed(cp, p, sub)) {
                            p.send(new LevelChunkWithLight(body));
                            sent.incrementAndGet();
                            world.chunkDelivered(cp, p, sub);   // 청크가 채널 큐에 들어간 뒤 보류된 변경을 흘려보낸다
                        }
                    })
                    .exceptionally(ex -> {           // 실패해도 배치 닫아 클라 무한 로딩 방지
                        org.slf4j.LoggerFactory.getLogger(Region.class)
                                .warn("[CHUNK] 전송 실패 {}", cp, ex);
                        return null;
                    });
            sends.add(f);
        }
        // allOf로 모든 future(전송/예외) 완료 후 Finished 1회 — 마샬 누락에 의한 batch 미종료(클라 stall) 방지.
        CompletableFuture.allOf(sends.toArray(new CompletableFuture[0]))
                .thenRun(() -> p.send(new ChunkBatchFinished(sent.get())));
    }

    /** 청크가 플레이어의 현재 시야(per-player view distance, Chebyshev) 안인지. stale 드롭·전송 게이트용. */
    private boolean inView(Player p, ChunkPos cp) {
        ChunkPos c = p.chunkPos();
        int vd = Math.min(world.viewDistance(), p.viewDistance());
        return Math.abs(cp.x() - c.x()) <= vd && Math.abs(cp.z() - c.z()) <= vd;
    }

    /**
     * 탭 목록 동기화(바닐라/Paper와 같은 관찰 결과): 본인 포함 접속 중인 모든 플레이어를 거리와 무관하게 올리고,
     * 새로 올릴 항목은 한 패킷으로 묶는다. 퇴장한 플레이어는 grace 없이 엔티티·탭에서 뺀다(관찰자 region의 다음 틱).
     * 스폰 패킷보다 반드시 먼저 호출해야 한다(탭 항목 없이 플레이어 엔티티를 스폰하면 안 됨).
     */
    private void syncTabList(Player a) {
        List<UUID> gone = new ArrayList<>();
        var it = a.listed().entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Player b = world.onlinePlayer(e.getKey());
            if (b == null || b.isDisconnected()) {
                if (stopTracking(a, e.getKey())) a.send(new RemoveEntities(java.util.List.of(e.getKey())));
                a.missingTicks().remove(e.getKey());
                gone.add(e.getValue());
                it.remove();
            }
        }
        if (!gone.isEmpty()) a.send(new PlayerInfoRemove(gone));
        List<PlayerInfoUpdate.Entry> add = null;
        for (Player b : world.onlinePlayers()) {
            if (b.isDisconnected()) continue;
            if (a.listed().putIfAbsent(b.entityId(), b.uuid()) == null) {
                if (add == null) add = new ArrayList<>();
                var session = b.chat().session();
                add.add(new PlayerInfoUpdate.Entry(b.uuid(), b.name(), b.profileProperties(), session == null ? null : session.asData(),
                        b.gameMode().id(), b.latencyMs()));
            }
        }
        if (add != null) a.send(new PlayerInfoUpdate(add));
    }

    /** 바닐라처럼 600틱마다 접속자 전원의 핑을 모든 플레이어에게 갱신해 보낸다. */
    private void broadcastLatencies() {
        List<PlayerInfoLatency.Entry> all = new ArrayList<>();
        for (Player b : world.onlinePlayers()) {
            if (!b.isDisconnected()) all.add(new PlayerInfoLatency.Entry(b.uuid(), b.latencyMs()));
        }
        if (all.isEmpty()) return;
        PlayerInfoLatency pkt = new PlayerInfoLatency(all);
        for (Player p : players.values()) p.send(pkt);
    }

    private void updateVisibility(Player a, List<EntityView> candidates) {
        syncTabList(a);
        double range = world.effectiveEntityChunks() * 16.0;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (EntityView b : candidates) {
            if (b.entityId() == a.entityId()) continue;
            seen.add(b.entityId());
            if (world.onlinePlayer(b.entityId()) == null) continue;   // 이미 퇴장 처리된 플레이어는 (재)스폰하지 않는다
            boolean near = !b.removed() && within(a, b, range);   // 사망 후 제거된 엔티티는 보이지 않는다
            boolean tracking = a.tracked().containsKey(b.entityId());
            if (near && tracking && (int) a.tracked().get(b.entityId())[5] != b.generation()) {
                // 리스폰(바닐라: 옛 ServerPlayer 제거 + 새 ServerPlayer 추가) — 지웠다 다시 스폰한다.
                a.send(new RemoveEntities(java.util.List.of(b.entityId())));
                stopTracking(a, b.entityId());
                tracking = false;
            }
            if (near && !tracking) {
                if (!a.listed().containsKey(b.entityId())) continue;   // syncTabList가 올리지 않은(퇴장 경합) 대상은 스폰 안 함
                a.send(new SpawnEntity(b.entityId(), b.uuid(), b.x(), b.y(), b.z(), b.yaw(), b.pitch()));
                a.send(new SetHeadRotation(b.entityId(), b.yaw()));
                startTracking(a, b.entityId(), new double[]{b.x(), b.y(), b.z(), b.yaw(), b.pitch(), b.generation()});
            } else if (!near && tracking) {
                a.send(new RemoveEntities(java.util.List.of(b.entityId())));
                stopTracking(a, b.entityId());
            } else if (near) {
                broadcastMove(a, b, a.tracked().get(b.entityId()));
            }
        }
        // 후보서 완전히 사라진 엔티티: grace 틱 후 despawn. 탭은 건드리지 않는다.
        // 핸드오프는 인접 region 스냅샷 갭이 1틱 정도라 grace 미만 → 깜빡임 없음.
        // (전제: 도착 region 스레드가 핸드오프 후 약 2틱(≈100ms) 안에 편입·공개할 때만 성립한다. 그보다 밀리면 관찰자가
        //  RemoveEntities→SpawnEntity 로 한 번 깜빡인다 — 자가 치유되는 외형 결함. 결정적으로 막으려면
        //  World 수준 in-transit 후보 집합이 필요하다: docs/remote-pr1-port/context-notes.md "F7" 참고.)
        for (var e : a.listed().entrySet()) {
            int eid = e.getKey();
            if (seen.contains(eid)) { a.missingTicks().remove(eid); continue; }
            if (a.missingTicks().merge(eid, 1, Integer::sum) >= ENTITY_DESPAWN_GRACE) {
                if (stopTracking(a, eid)) a.send(new RemoveEntities(java.util.List.of(eid)));
                a.missingTicks().remove(eid);   // 접속 중이면 엔티티만 디스폰하고 탭은 유지(퇴장은 위에서 즉시 처리)
            }
        }
    }

    /**
     * 상대 이동 패킷(1/4096 블록 단위 short)으로 인코딩 가능한 델타인가. 정확히 8블록 부근은 반올림하면 32768로
     * 넘쳐 반대 방향으로 이동하므로(예: 7.9999 → -32768) 임계값 비교 대신 인코딩 결과 범위로 판정한다.
     */
    static boolean fitsDelta(double d) {
        long v = Math.round(d * 4096);
        return v >= Short.MIN_VALUE && v <= Short.MAX_VALUE;
    }

    private static boolean within(Player a, EntityView b, double range) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return dx * dx + dz * dz <= range * range;
    }

    private void broadcastMove(Player a, EntityView b, double[] last) {
        double ddx = b.x() - last[0], ddy = b.y() - last[1], ddz = b.z() - last[2];
        boolean posChanged = ddx != 0 || ddy != 0 || ddz != 0;
        boolean rotChanged = b.yaw() != last[3] || b.pitch() != last[4];
        if (!posChanged && !rotChanged) return;
        boolean big = !fitsDelta(ddx) || !fitsDelta(ddy) || !fitsDelta(ddz);
        if (big) {
            a.send(new TeleportEntity(b.entityId(), b.x(), b.y(), b.z(), b.yaw(), b.pitch(), b.onGround()));
        } else if (posChanged) {
            short sdx = (short) Math.round(ddx * 4096);
            short sdy = (short) Math.round(ddy * 4096);
            short sdz = (short) Math.round(ddz * 4096);
            a.send(new MoveEntityPosRot(b.entityId(), sdx, sdy, sdz, b.yaw(), b.pitch(), b.onGround()));
        } else {
            a.send(new MoveEntityRot(b.entityId(), b.yaw(), b.pitch(), b.onGround()));
        }
        a.send(new SetHeadRotation(b.entityId(), b.yaw()));
        last[0] = b.x(); last[1] = b.y(); last[2] = b.z(); last[3] = b.yaw(); last[4] = b.pitch();
    }
}
