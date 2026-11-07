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
import org.domi.furnace.server.entity.Rider;
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

    /** 청크 키가 붙은 작업. urgent: 전역(명령) 스레드가 보냈다 — 명령 블록이 결과를 기다리는 동안 이것만 골라 돌린다. */
    record Task(long chunkKey, Consumer<Region> run, boolean urgent) {}

    private record Delayed(long dueTick, long seq, long chunkKey, Consumer<Region> run) {
        Delayed(long dueTick, long chunkKey, Consumer<Region> run) { this(dueTick, DELAYED_SEQ.getAndIncrement(), chunkKey, run); }
    }

    /** 같은 틱에 때가 된 지연 작업은 건 차례대로(Bukkit·Folia 처럼) — region 끼리 옮겨도 차례가 남게 서버 전체 번호. */
    private static final java.util.concurrent.atomic.AtomicLong DELAYED_SEQ = new java.util.concurrent.atomic.AtomicLong();

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
    /** 스케줄 계측(잠금 안에서만 쓰고 읽는다): 꺼낸 틱 수, 큐 지연(꺼낸 시각 - 마감) 합·최대(ns). */
    long schedTicks, schedDelaySum, schedDelayMax;
    /** 관측용: 직전 틱·가장 긴 틱의 소요 시간(ns). 이 region 을 돌린 워커만 쓴다(한 번에 한 워커). */
    volatile long lastTickNanos, maxTickNanos;
    /** 관측용(/tps): 이 region 의 플레이어 수 — 틱이 끝날 때와 합치기·나누기·편입 때 갱신한다(아무 스레드가 읽는다). */
    volatile int observedPlayers;
    /** 관측용(/tps): 최근 게임 틱 시작 시각. 이 region 을 돌린 워커만 쓴다(한 번에 한 워커). 20 TPS 로 6.4초를 덮는다. */
    final org.domi.furnace.server.sched.TickStartRing tickStarts = new org.domi.furnace.server.sched.TickStartRing(128);
    /** -Dfurnace.tickstats: [0]=직전 시작, [1]=개수, 이후 (간격, 소요) 쌍. 틱 중(이 region 이 RUNNING)에만 만진다. */
    final long[] tickStat = new long[2 + 200];
    int ticksSinceRecalc;
    int deadSections;
    final Set<Long> sections = new HashSet<>();
    final Set<Region> pendingMerges = new HashSet<>();
    /** 이 region 구역의 "있는" 청크 키(로드·로드 중·시야). 쓰기는 Regionizer 잠금 안. */
    final Set<Long> present = ConcurrentHashMap.newKeySet();
    final ConcurrentLinkedQueue<Task> inbox = new ConcurrentLinkedQueue<>();

    // ---- 명령 블록 기다림(awaitUrgent)
    /** 명령 블록이 명령 결과를 기다리는 region 수(서버 전체). 0 보다 크면 쉬는 region 도 급한 작업을 곧바로 돌린다(Regionizer.urgentArrived). */
    static final java.util.concurrent.atomic.AtomicInteger URGENT_WAITERS = new java.util.concurrent.atomic.AtomicInteger();
    /** 한 틱에 명령 블록이 명령 결과를 기다리는 데 쓸 수 있는 시간 합(ns). 넘으면 예전처럼 결과를 다음 틱 이후에 반영한다. */
    static final long URGENT_WAIT_BUDGET_NANOS = Long.getLong("furnace.commandBlockWaitMicros", 20_000L) * 1_000L;
    /** 지금 명령 결과를 기다리는 이 region 의 스레드(급한 작업이 오면 깨운다). */
    volatile Thread urgentWaiter;
    /** Regionizer 잠금 안: 이번 차례는 급한 작업만(drain) — 끝나면 savedNextTickNanos 로 돌아간다. */
    boolean drainPass;
    long savedNextTickNanos;
    /** 이번 틱에 기다린 시간(ns, 이 region 스레드만). */
    private long urgentWaitUsed;

    // ---- 이 region 스레드만 다루는 상태
    private final PriorityQueue<Delayed> delayed = new PriorityQueue<>((a, b) -> a.dueTick != b.dueTick ? Long.compare(a.dueTick, b.dueTick) : Long.compare(a.seq, b.seq));
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

    /**
     * 틱 사이(작업 큐·지연 작업·drain 차례를 도는 중)인가 — 게임 틱 한가운데(플레이어·엔티티 반복 중)가 아니다. 이 region 스레드만 읽고 쓴다.
     * drain 차례 안의 작업이 다시 awaitUrgent 로 기다려도 참이다 — 그 기다림도 플레이어 반복 밖이라 편입해도 된다.
     */
    private boolean betweenTicks;

    /** 이 region 스레드에서: 지금 틱 사이인가(차원 이동 도착을 그 자리에서 편입해도 되는가). */
    public boolean betweenTicks() { return isOwnerThread() && betweenTicks; }

    /** 이 region 스레드에서만: 그 플레이어가 지금 이 region 소속인가. */
    public boolean ownsPlayer(Player p) { return isOwnerThread() && players.get(p.entityId()) == p; }

    private Regionizer rz() { return world.regionizer(); }

    // ------------------------------------------------------------------ 틱(Regionizer 작업 스레드)

    /** 작업 큐 → 지연 작업 → (켜져 있으면) 게임 틱. */
    /** 마지막 runTick 이 게임 틱(tick)까지 돌았는가 — 붙잡힌(백업·일시정지) 차례는 틱 시간 표본에 넣지 않는다. */
    boolean lastRunTicked;

    void runTick(boolean gameTick) { runTick(gameTick, false); }

    /**
     * manualHold: 수동 시계(헤드리스 시험 서버)에서 이번 차례가 시험이 낸 틱이 아니다 — 작업 큐만 비우고 지연 작업 시계·월드·플레이어 처리는
     * 하지 않는다(sprint lockstep 의 기다림과 같은 길).
     */
    void runTick(boolean gameTick, boolean manualHold) {
        lastRunTicked = false;
        // /tick sprint lockstep: 전역을 앞서면 이번 차례는 작업 큐만 비우고 틱(지연 작업 시계·월드·플레이어 처리)은 하지 않는다
        sprintHeld = !manualHold && gameTick && sprintHoldsNow();
        boolean hold = sprintHeld || manualHold;
        if (!hold) tickCount++;
        urgentWaitUsed = 0L;
        long deadline = System.nanoTime() + DRAIN_BUDGET_NANOS;
        Task t;
        int n = 0;
        betweenTicks = true;
        try {
            while ((t = inbox.poll()) != null) {
                if (t.run() instanceof Regionizer.RegionWide || mine(t.chunkKey())) runTask(t.run());
                else rz().enqueue(t.chunkKey(), t.run(), t.urgent());
                if ((++n & 15) == 0 && System.nanoTime() > deadline) break;
            }
            if (hold) return;
            while (!delayed.isEmpty() && delayed.peek().dueTick <= tickCount) {
                Delayed d = delayed.poll();
                if (mine(d.chunkKey)) runTask(d.run);
                else rz().enqueue(d.chunkKey, d.run);
            }
        } finally {
            betweenTicks = false;
        }
        // 틱 붙잡기(백업 기준점·빈 서버 일시정지)는 작업 큐를 비운 뒤에 본다 — 붙잡은 뒤 넣은 작업(quiesce)이 돈 차례는 틱을 하지 않는다
        if (gameTick && !world.ticksHeld()) {
            tick();
            lastRunTicked = true;
        }
    }

    /**
     * 이 region 스레드에서(틱 중): done 이 참이 될 때까지, 작업 큐에서 전역(명령) 스레드가 보낸 작업만 골라 돌리며 기다린다.
     * 명령 블록이 바닐라처럼 명령을 그 자리에서 끝내게 하는 길 — 명령이 이 region 의 칸·엔티티를 만지는 왕복이 기다리는 동안 처리된다.
     * 기다리는 동안 쉬는 다른 region 도 급한 작업만 곧바로 돌린다(Regionizer.urgentArrived). 이번 틱 예산을 다 쓰면 거짓(호출자는 예전처럼 비동기로).
     */
    public boolean awaitUrgent(java.util.function.BooleanSupplier done) {
        if (done.getAsBoolean()) return true;
        if (!isOwnerThread()) return false;
        long start = System.nanoTime();
        long deadline = start + (URGENT_WAIT_BUDGET_NANOS - urgentWaitUsed);
        if (deadline - start <= 0) return false;
        Thread prev = urgentWaiter;
        urgentWaiter = Thread.currentThread();
        URGENT_WAITERS.incrementAndGet();
        try {
            while (true) {
                runUrgentTasks();
                if (done.getAsBoolean()) return true;
                if (Regionizer.helpDrainAny()) continue;   // 워커가 모자라 못 돈 남의 급한 작업(예: @e 가 도는 다른 region)
                long now = System.nanoTime();
                if (now - deadline >= 0) return false;
                java.util.concurrent.locks.LockSupport.parkNanos(this, Math.min(deadline - now, 1_000_000L));
            }
        } finally {
            URGENT_WAITERS.decrementAndGet();
            urgentWaiter = prev;
            urgentWaitUsed += System.nanoTime() - start;
        }
    }

    /** 작업 큐에서 급한 작업만 꺼내 돌린다(넣은 순서대로, 나머지는 그대로 둔다). 이 region 이 아닌 칸이면 지금 주인에게. */
    private void runUrgentTasks() {
        for (var it = inbox.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (!t.urgent()) continue;
            it.remove();
            if (t.run() instanceof Regionizer.RegionWide || mine(t.chunkKey())) runTask(t.run());
            else rz().enqueue(t.chunkKey(), t.run(), true);
        }
    }

    /** 작업 큐에 급한 작업이 있는가(아무 스레드, 힌트). */
    boolean hasUrgentTask() {
        for (Task t : inbox) if (t.urgent()) return true;
        return false;
    }

    /** drain 차례(틱 사이, 이 region 스레드): 급한 작업만 돌린다. 틱 사이 합치기·나누기가 있었을 수 있어 칸 조회 캐시를 먼저 비운다. */
    void runUrgentDrain() {
        if (level != null) level.chunkUnloaded();
        betweenTicks = true;
        try {
            runUrgentTasks();
        } finally {
            betweenTicks = false;
        }
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
        for (Delayed d : o.delayed) delayed.add(new Delayed(tickCount + Math.max(0, d.dueTick - o.tickCount), d.seq, d.chunkKey, d.run));
        o.delayed.clear();
        for (Player p : o.players.values()) players.put(p.entityId(), p);
        o.players.clear();
        observedPlayers = players.size();
        o.observedPlayers = 0;
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
            else t.delayed.add(new Delayed(t.tickCount + Math.max(0, d.dueTick - tickCount), d.seq, d.chunkKey, d.run));
        }
        delayed.clear();
        delayed.addAll(keep);
        for (var it = players.values().iterator(); it.hasNext(); ) {
            Player p = it.next();
            ChunkPos c = p.chunkPos();
            Region t = targetOf.apply(Regionizer.chunkKey(c.x(), c.z()));
            if (t != null) {
                t.players.put(p.entityId(), p);
                t.observedPlayers = t.players.size();
                it.remove();
            }
        }
        observedPlayers = players.size();
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


    /**
     * 로드된 청크를 돌려준다. 로드 안 됐으면 region 스레드를 막지 않고 null 을 돌려주며 비동기 로드({@link #loadChunk})를 건다.
     * 다른 region 구역이라 이 region 이 로드를 걸 수 없으면 로드 요청 없이 null.
     */
    public ChunkColumn chunkAt(ChunkPos cp) {
        world.assertOwns(this);
        ChunkColumn c = world.loadedChunk(cp.x(), cp.z());
        if (c != null) return c;
        requestLoad(cp);
        return null;
    }

    /** 비동기 로드 요청(결과는 버린다 — 실패는 loadChunk 가 로그로 남기고 다음 요청에서 재시도). 다른 region 구역이면 무시. */
    private void requestLoad(ChunkPos cp) {
        try {
            loadChunk(cp);
        } catch (IllegalStateException otherRegion) {
            // 다른 region 의 청크 — 이 region 이 로드할 수 없다(옛 동기 경로도 여기서 예외였다)
        }
    }

    /**
     * 시험·도구 전용: 청크를 얻거나 없으면 이 스레드에서 동기 생성·디스크 읽기. region 스레드를 막으므로 운영 경로에서 부르지 않는다
     * (외부 스레드는 {@link World#awaitChunk} 로 기다린다).
     */
    public ChunkColumn chunkAtBlocking(ChunkPos cp) {
        world.assertOwns(this);
        ChunkColumn c = world.loadedChunk(cp.x(), cp.z());
        if (c != null) return c;
        claim(cp);
        world.countBlockingLoad();
        // 디스크 읽기는 비동기 로드·저장과 같은 IO 레인을 거친다(대기 중인 저장보다 먼저 읽어 옛 내용을 되살리지 않게)
        c = world.worldSource() instanceof org.domi.furnace.server.world.gen.AnvilWorldSource anvil
                ? anvil.generate(cp.x(), cp.z(), world.ioLane(cp.x(), cp.z()))
                : world.worldSource().generate(cp.x(), cp.z());
        c.promotePendingBlockEntities(cp.x(), cp.z(), true);
        world.putChunk(cp.x(), cp.z(), c);
        level().lightNewChunk(cp.x(), cp.z());
        level().runLightUpdates();
        level().raids().claim(cp.x(), cp.z());   // raids.dat 에서 이 청크가 중심인 습격
        loadEntities(cp, c);
        level().addWorldGenChunkEntities(c);
        // 동기 경로도 비동기 finishLoad 와 같게 블록 엔티티를 틱 목록에
        if (!c.blockEntities().isEmpty()) level().registerBlockEntities(c);
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
                        ? anvil.loadChunkAsync(cp.x(), cp.z(), world.ioLane(cp.x(), cp.z()), world.ioDecodeExecutor(), world.genPool())
                        : CompletableFuture.supplyAsync(() -> world.worldSource().generate(cp.x(), cp.z()), world.genPool());
        // 청크 안쪽 조명은 생성 스레드에서 미리 계산한다(region 틱 스레드가 청크당 ~2ms 를 쓰던 것) — 이웃과의 가장자리는 올릴 때 맞춘다.
        final boolean sky = level().hasSkyLight();
        final int bedrock = level().lightBedrockState();
        sourceFuture = sourceFuture.thenApplyAsync(col -> {   // 디스크에서 읽은 청크는 IO 스레드가 아니라 생성 풀에서 계산한다
            if (col != null) {
                if (col.poi() == null) col.poi(org.domi.furnace.server.world.poi.PoiManager.restore(col, cp.x(), cp.z(), col.poiNbt()));
                // registerAllBlockEntitiesAfterLevelLoad: 남은 NBT·월드젠 칸을 블록 엔티티로(침대·종·상자는 없으면 클라이언트가 안 그린다).
                // 빈 칸 채우기(DUMMY)는 디스크 청크에도 한다 — 수정 전에 저장된 월드의 침대·종을 되살리고, Anvil+평지처럼 freshlyGenerated 가 안 서는 새 청크도 덮는다.
                // 비용은 생성 풀에서 구역당 블록 배열 한 번 훑기(POI mayHavePoi 와 같은 꼴).
                col.promotePendingBlockEntities(cp.x(), cp.z(), true);
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

    /** -Dfurnace.tickstats: 이 region 의 청크 설치 비용 {개수, 넣기, 빛, 엔티티·블록엔티티·예약틱} ns 합계. 틱 중에만 만진다. 누적값. */
    final long[] installStats = new long[4];
    /** 마지막 [TICKSTATS] 출력 때의 installStats 사본. 출력은 차이만 쓰고 installStats 는 누적으로 둔다(벤치가 총합을 읽는다). */
    final long[] installStatsLogged = new long[4];

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
        level().raids().claim(cp.x(), cp.z());   // raids.dat 에서 이 청크가 중심인 습격
        loadEntities(cp, col);
        if (level().addWorldGenChunkEntities(col)) dirtyChunks.add(cp);
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
            installStats[0]++;
            installStats[1] += t1 - t0;
            installStats[2] += t2 - t1;
            installStats[3] += t3 - t2;
        }
        world.levelServices().gameEvents().chunkLoad(world.dimension(), cp.x(), cp.z());   // 플러그인 ChunkLoadEvent
        f.complete(col);
    }

    /**
     * 청크 재생성(W3, 불만 표 41번)의 교체 — 이 region 스레드에서 로드된 청크를 새로 생성한 fresh 로 바꾼다. 그 청크가 지금 이 region 에
     * 로드돼 있지 않으면 null(부르는 쪽이 다시 불러와 재시도). 바꾸는 것은 그 청크 하나다: 블록·바이옴·블록 엔티티·예약 틱·구조물·POI·
     * 높이 지도. 빛은 옛 빛에서 바뀐 칸만 다시 계산해(블록을 하나씩 바꾼 것과 같다) 이웃으로 번진 옛 빛도 지운다. 엔티티·영속 데이터·
     * 머문 시간(InhabitedTime)은 이어 간다. 보는 플레이어에게는 새 청크를 다시 보낸다.
     */
    Boolean replaceRegenerated(ChunkPos cp, ChunkColumn fresh, boolean removeEntities, boolean generationEntities) {
        world.assertOwns(this);
        int cx = cp.x(), cz = cp.z();
        ChunkColumn old = world.loadedChunk(cx, cz);
        if (old == null || !rz().owns(this, cx, cz)) return null;
        // 디스크 엔티티를 아직 읽는 중이면 기다렸다 다시(바꾼 뒤 도착하면 버려져 엔티티가 사라지거나, FRESH 가 지우지 못한다)
        if (!old.entitiesLoaded() && old.pendingEntityLoad() != null) return null;
        RegionLevel lv = level();
        // 엔티티: 기본은 그대로 둔다. 지울 때도 플레이어와 플레이어를 태운 탈것은 남긴다
        if (removeEntities) {
            for (org.domi.furnace.server.entity.Entity e : new ArrayList<>(lv.entities().inChunk(cx, cz))) {
                if (!e.isRemoved() && !carriesPlayer(e)) e.remove(org.domi.furnace.server.entity.Entity.RemovalReason.DISCARDED);
            }
        } else {
            fresh.foreignEntities().addAll(old.foreignEntities());
        }
        fresh.entitiesLoaded(old.entitiesLoaded());
        fresh.entitiesOnDisk(old.entitiesOnDisk() || removeEntities);   // 지웠으면 디스크 묶음도 다음 저장에서 비운다
        if (!generationEntities) {
            fresh.clearGeneratedEntities();
            fresh.freshlyGenerated(false);
        }
        // 옛 블록 엔티티는 틱 목록에서 빠지고, 옛 예약 틱은 버린다
        for (var be : old.blockEntities().values()) be.setRemoved();
        if (lv.hasChunkTicks(cx, cz)) lv.saveChunkTicks(cx, cz, new org.domi.furnace.server.net.io.nbt.NbtCompound(), true);
        // 플러그인 영속 데이터(ChunkBukkitValues)·모르는 최상위 키·머문 시간은 지형이 아니라 이어 간다
        if (old.extraNbt() != null) {
            var keep = old.extraNbt().copy();
            keep.tags().remove("block_ticks");
            keep.tags().remove("fluid_ticks");
            keep.tags().remove("blending_data");         // 옛 청크 표시 — 바닐라가 이웃 새 청크의 지형 섞기를 바꾼다
            keep.tags().remove("below_zero_retrogen");
            if (fresh.extraNbt() != null) keep.tags().putAll(fresh.extraNbt().tags());
            fresh.extraNbt(keep);
        }
        fresh.inhabitedTime(old.inhabitedTime());
        // 빛: 옛 빛 배열을 이어받아 바뀐 칸만 다시 계산한다
        var light = old.light();
        fresh.light(light);
        fresh.lightPrecomputed(false);
        world.putChunk(cx, cz, fresh);
        lv.chunkUnloaded();   // 청크 조회 캐시가 옛 객체를 들고 있지 않게
        // 남긴 엔티티의 진동 리스너(워든·알레이)는 옛 청크의 구역에 걸려 있다 — 새 청크에 다시 건다
        for (org.domi.furnace.server.entity.Entity e : new ArrayList<>(lv.entities().inChunk(cx, cz))) {
            if (e.isRemoved()) continue;
            for (var l : e.dynamicGameEventListeners()) {
                l.remove(lv);
                l.add(lv);
            }
        }
        if (light == null) {
            lv.lightNewChunk(cx, cz);
        } else {
            var engine = lv.lightEngine();
            int minY = fresh.minY();
            for (int sec = 0; sec < fresh.sectionCount(); sec++) {
                ChunkSection a = old.sections()[sec], b = fresh.sections()[sec];
                int ua = a.uniformBlock(), ub = b.uniformBlock();
                if (ua >= 0 && ua == ub) continue;
                int baseY = minY + sec * 16;
                for (int ly = 0; ly < 16; ly++)
                    for (int lz = 0; lz < 16; lz++)
                        for (int lx = 0; lx < 16; lx++) {
                            int y = baseY + ly;
                            if (old.getBlock(lx, y, lz) != fresh.getBlock(lx, y, lz)) engine.blockChanged(cx * 16 + lx, y, cz * 16 + lz);
                        }
            }
        }
        lv.runLightUpdates();
        if (!fresh.blockEntities().isEmpty()) lv.registerBlockEntities(fresh);
        if (fresh.extraNbt() != null && (fresh.extraNbt().contains("block_ticks") || fresh.extraNbt().contains("fluid_ticks"))) {
            lv.loadChunkTicks(fresh.extraNbt());
        }
        if (generationEntities) {
            lv.addWorldGenChunkEntities(fresh);
            if (fresh.freshlyGenerated()) {
                fresh.freshlyGenerated(false);
                try {
                    org.domi.furnace.server.entity.spawn.NaturalSpawner.spawnMobsForChunkGeneration(lv, cx, cz, lv.worldSeed());
                } catch (RuntimeException e) {
                    org.slf4j.LoggerFactory.getLogger(Region.class).warn("[SPAWN] 재생성 청크 동물 스폰 실패 {}", cp, e);
                }
            }
        }
        fresh.markDirty();
        dirtyChunks.add(cp);
        // 보는 플레이어에게 새 청크를 다시 보낸다 — 새 구독(ready=false)으로 바꿔 그 사이 변경은 청크 뒤로 미루고, 아직 옛 청크를
        // 직렬화하던 전송은 구독이 바뀌어 버려진다(그래서 아직 받지 못한 플레이어에게도 지금 보낸다)
        for (Player p : new ArrayList<>(world.subscribers(cp))) {
            World.Subscription sub = world.subscribeChunk(cp, p);
            world.serializedChunkBody(cp).thenAccept(body -> {
                if (!world.isSubscribed(cp, p, sub)) return;
                p.send(LevelChunkWithLight.ofShared(body));
                world.chunkDelivered(cp, p, sub);
            }).exceptionally(ex -> {
                org.slf4j.LoggerFactory.getLogger(Region.class).warn("[CHUNK] 재생성 청크 재전송 실패 {}", cp, ex);
                return null;
            });
        }
        return Boolean.TRUE;
    }

    private static boolean carriesPlayer(org.domi.furnace.server.entity.Entity e) {
        for (var r : e.passengers()) {
            if (r instanceof Player) return true;
            if (r instanceof org.domi.furnace.server.entity.Entity pe && carriesPlayer(pe)) return true;
        }
        return false;
    }

    /** 그 청크의 엔티티 묶음(entities/)을 IO 풀에서 읽어 와 넣는다 — 그 사이 청크가 내려갔으면 버린다. 디스크가 없으면 바로 끝. */
    private void loadEntities(ChunkPos cp, ChunkColumn col) {
        if (!(world.worldSource() instanceof org.domi.furnace.server.world.gen.AnvilWorldSource)) {
            col.entitiesLoaded(true);
            return;
        }
        var done = new java.util.concurrent.CompletableFuture<Void>();
        col.pendingEntityLoad(done);
        world.loadEntitiesAsync(cp.x(), cp.z()).whenComplete((root, err) -> {
            try {
                world.runAtChunk(cp, r -> {
                    try {
                        if (world.loadedChunk(cp.x(), cp.z()) == col) r.level().loadChunkEntities(col, root);
                    } finally {
                        col.pendingEntityLoad(null);
                        done.complete(null);
                    }
                });
            } catch (RuntimeException e) {   // 월드를 내리는 중(스케줄러가 받지 않는다) — 기다리는 재생성이 걸리지 않게
                col.pendingEntityLoad(null);
                done.complete(null);
            }
        });
    }

    /** 이 region 이 가진 로드된 청크의 바이옴 항목(섹션별 4×4×4 번호). 없으면 null — 플레이어별 바이옴 층이 한 사람에게 다시 보낼 때 쓴다. */
    public org.domi.furnace.server.net.protocol.play.ChunksBiomes.Entry biomeEntry(ChunkPos cp) {
        world.assertOwns(this);
        ChunkColumn col = ownedChunk(cp.x(), cp.z());
        if (col == null) return null;
        var secs = col.sections();
        int[][] out = new int[secs.length][];
        for (int i = 0; i < secs.length; i++) out[i] = secs[i].biomes().clone();   // 다른 스레드(연결 이벤트 루프)가 인코딩하므로 소유 스레드에서 복사한다
        return new org.domi.furnace.server.net.protocol.play.ChunksBiomes.Entry(cp.x(), cp.z(), out);
    }

    /** 이 region 이 가진 로드된 청크(없으면 null, 로드를 걸지 않는다). 플러그인 API 용. */
    public ChunkColumn ownedColumn(ChunkPos cp) {
        world.assertOwns(this);
        return ownedChunk(cp.x(), cp.z());
    }

    /** 청크를 저장 대상으로 표시한다(플러그인이 청크 영속 데이터를 고쳤을 때). 이 region 이 가진 로드된 청크여야 한다. */
    public void markChunkChanged(ChunkPos cp) {
        world.assertOwns(this);
        ChunkColumn col = ownedChunk(cp.x(), cp.z());
        if (col == null) return;
        col.markDirty();
        dirtyChunks.add(cp);
    }

    /** 이 region 스레드에서 그 청크가 이미 로드돼 있는가. 클라 요청이 임의 좌표의 청크를 새로 생성시키지 못하게 하는 게이트용. */
    public boolean chunkLoaded(ChunkPos cp) {
        world.assertOwns(this);
        return ownedChunk(cp.x(), cp.z()) != null;
    }

    /**
     * Level.getHeight(type, x, z): 그 칸 위로 처음 비는 y(= 가장 높은 블록 y + 1). 이 region 구역의 로드된 청크가 아니면 {@link Integer#MIN_VALUE}.
     * 청크를 새로 올리지 않는다(/execute positioned over).
     */
    public int heightAt(HeightmapType type, int bx, int bz) {
        world.assertOwns(this);
        ChunkColumn col = ownedChunk(bx >> 4, bz >> 4);
        if (col == null) return Integer.MIN_VALUE;
        int x = bx & 15, z = bz & 15;
        return 1 + switch (type) {
            case WORLD_SURFACE -> col.surfaceBlockY(x, z);
            case OCEAN_FLOOR -> col.oceanFloorBlockY(x, z);
            case MOTION_BLOCKING -> col.motionBlockingBlockY(x, z);
            case MOTION_BLOCKING_NO_LEAVES -> col.motionBlockingNoLeavesBlockY(x, z);
        };
    }

    /** 거부된 요청의 클라 예측을 되돌린다 — 로드된 청크면 서버의 실제 블록 상태를 actor에게 다시 보낸다(미로드면 보낼 게 없음). */
    public void resendBlock(Player actor, int bx, int by, int bz) {
        world.assertOwns(this);
        ChunkColumn col = ownedChunk(bx >> 4, bz >> 4);
        if (col == null) return;
        org.domi.furnace.server.plugin.ClientResync.block(actor, level(), new org.domi.furnace.server.block.Pos(bx, by, bz));
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
        if (world.loadedChunk(bx >> 4, bz >> 4) == null) return false;   // 미로드 — 동기 생성하지 않는다
        RegionLevel l = level();
        org.domi.furnace.server.block.Pos p = new org.domi.furnace.server.block.Pos(bx, by, bz);
        int state = l.getBlockState(p);
        org.domi.furnace.server.block.BlockActor except = breaker == null ? null : new org.domi.furnace.server.block.PlayerActor(breaker);
        l.levelEvent(except, 2001, p, state);
        return l.removeBlock(p, false);
    }

    /** 테스트·도구용: 파괴하고 바로 방송한다. */
    public void breakBlock(int bx, int by, int bz) {
        chunkAtBlocking(ChunkPos.ofBlock(bx, bz));
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

    /** 이 region 스레드에서 블록 조회(0x38 체스트 판별용). 미로드 청크는 공기 — 로드 요청도 걸지 않는다(매 틱 stillValid 가 region 을 넓히지 않게). */
    public int getBlock(int x, int y, int z) {
        world.assertOwns(this);
        ChunkColumn c = world.loadedChunk(x >> 4, z >> 4);
        return c == null ? ChunkSection.AIR : c.getBlock(x & 15, y, z & 15);
    }

    /** 바이옴 quart 의 레지스트리 id(청크가 없거나 이 region 것이 아니면 -1). 이 region 스레드에서만. */
    /** ownedChunk 와 같은 답을 레벨의 조회 캐시로(레벨이 아직 없으면 바로 찾는다). */
    private ChunkColumn cachedChunk(int cx, int cz) {
        RegionLevel l = level;
        return l != null ? l.chunkCached(cx, cz) : ownedChunk(cx, cz);
    }

    /** ChunkAccess.getNoiseBiome(블록 좌표 >> 2): quart y 를 [최저, 최고] 로 잘라 그 청크의 바이옴 id(청크가 없으면 -1). */
    public int noiseBiomeIdAtBlock(int x, int y, int z) {
        world.assertOwns(this);
        ChunkColumn col = cachedChunk(x >> 4, z >> 4);
        if (col == null) return -1;
        int minQ = col.minY() >> 2, maxQ = minQ + col.sections().length * 4 - 1;
        int qy = Math.max(minQ, Math.min(maxQ, y >> 2));
        return col.sections()[((qy << 2) - col.minY()) >> 4].getBiome(x >> 2 & 3, qy & 3, z >> 2 & 3);
    }

    public int biomeIdAtQuart(int qx, int qy, int qz) {
        world.assertOwns(this);
        ChunkColumn col = cachedChunk(qx >> 2, qz >> 2);   // 스폰 시도마다 불린다 — 해시 맵 두 번 대신 레벨 조회 캐시
        if (col == null) return -1;
        int sec = ((qy << 2) - col.minY()) >> 4;
        if (sec < 0 || sec >= col.sections().length) return -1;
        return col.sections()[sec].getBiome(qx & 3, qy & 3, qz & 3);
    }

    /**
     * 바닐라 FillBiomeCommand 의 청크 쪽: 상자(블록 좌표, 포함) 안 바이옴 칸(quart)을 biomeId 로 바꾼다(filter 가 현재 바이옴 id 를 받아 참이면).
     * 청크는 z 바깥·x 안쪽 순서. 상자가 걸친 청크 중 이 region 이 로드해 가진 게 아닌 것이 있으면 아무것도 바꾸지 않고 -1.
     * 바뀐 청크는 저장 대상으로 표시하고 바이옴만 다시 보낸다(chunks_biomes). 반환: filter 를 통과한 칸 수(이미 같은 바이옴이어도 센다).
     */
    public int fillBiomes(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int biomeId, java.util.function.IntPredicate filter) {
        world.assertOwns(this);
        java.util.List<ChunkPos> cps = new java.util.ArrayList<>();
        java.util.List<ChunkColumn> cols = new java.util.ArrayList<>();
        for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                ChunkColumn col = ownedChunk(cx, cz);
                if (col == null) return -1;
                cps.add(new ChunkPos(cx, cz));
                cols.add(col);
            }
        }
        int count = 0;
        for (int k = 0; k < cols.size(); k++) {
            ChunkColumn col = cols.get(k);
            ChunkPos cp = cps.get(k);
            var secs = col.sections();
            java.util.List<int[]> after = new java.util.ArrayList<>();
            for (int i = 0; i < secs.length; i++) {
                int qyBase = (col.minY() >> 2) + i * 4;
                for (int lx = 0; lx < 4; lx++) for (int ly = 0; ly < 4; ly++) for (int lz = 0; lz < 4; lz++) {
                    int bx = ((cp.x() << 2) + lx) << 2, by = (qyBase + ly) << 2, bz = ((cp.z() << 2) + lz) << 2;
                    int cur = secs[i].getBiome(lx, ly, lz);
                    if (bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ && filter.test(cur)) {
                        count++;
                        secs[i].setBiome(lx, ly, lz, biomeId);
                    }
                }
                after.add(secs[i].biomes());
            }
            col.markDirty();
            dirtyChunks.add(cp);
            world.broadcastBlockUpdate(cp, new org.domi.furnace.server.net.protocol.play.ChunksBiomes(
                    java.util.List.of(new org.domi.furnace.server.net.protocol.play.ChunksBiomes.Entry(cp.x(), cp.z(), after.toArray(new int[0][]))),
                    world.biomeDirectBits()));
        }
        return count;
    }

    /** 이 region 스레드에서 체스트 블록엔티티 조회(없거나 미로드면 null). */
    public ChestBlockEntity chestAt(int x, int y, int z) {
        world.assertOwns(this);
        ChunkColumn c = world.loadedChunk(x >> 4, z >> 4);
        return c == null ? null : c.chestAt(x, y, z);
    }

    /** 체스트 슬롯 갱신(Container Click). 존재하지 않으면 무시. 청크 dirty 마크(영속, §3.6). */
    public void setChestItem(int cx, int cy, int cz, int slot, ItemStack item) {
        world.assertOwns(this);
        ChunkPos cp = ChunkPos.ofBlock(cx, cz);
        ChunkColumn col = world.loadedChunk(cp.x(), cp.z());
        ChestBlockEntity chest = col == null ? null : col.chestAt(cx, cy, cz);
        if (chest == null || slot < 0 || slot >= chest.items.length) return;
        chest.items[slot] = item;
        col.markDirty();
        dirtyChunks.add(cp);
        chest.setChanged(level());   // 비교기에 알린다
    }

    /** 용기 블록 엔티티 내용이 바뀌었음 — 청크를 저장 대상으로, 비교기에 알린다. 이 region 이 가진 블록이어야 한다. */
    public void markBlockEntityChanged(org.domi.furnace.server.block.entity.BlockEntity be) {
        world.assertOwns(this);
        var p = be.pos();
        ChunkPos cp = ChunkPos.ofBlock(p.x(), p.z());
        ChunkColumn col = world.loadedChunk(cp.x(), cp.z());
        if (col == null) return;   // 내린 청크의 블록 엔티티 — 저장은 이미 끝났다
        col.markDirty();
        dirtyChunks.add(cp);
        be.setChanged(level());
    }

    /** 상자 내용이 바뀌었음(전리품 채우기 등) — 청크를 저장 대상으로. */
    public void markChestChanged(int cx, int cy, int cz) {
        world.assertOwns(this);
        ChunkPos cp = ChunkPos.ofBlock(cx, cz);
        ChunkColumn col = world.loadedChunk(cp.x(), cp.z());
        if (col == null) return;
        col.markDirty();
        dirtyChunks.add(cp);
        ChestBlockEntity chest = col.chestAt(cx, cy, cz);
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
        chunkAtBlocking(ChunkPos.ofBlock(bx, bz));   // 도구·시험 경로 — 운영 설치는 BlockPlacement
        RegionLevel l = level();
        var p = new org.domi.furnace.server.block.Pos(bx, by, bz);
        if (!org.domi.furnace.server.block.BlockInfo.get().replaceable(l.getBlockState(p))) return false;
        if (!l.isUnobstructed(stateId, p, null)) return false;
        boolean ok = l.setBlock(p, stateId, 11);
        l.broadcastChanges();
        return ok;
    }

    /** 이 region 레벨의 습격 스냅샷을 월드 습격 저장소에 올린다(이 region 스레드에서). */
    void publishRaids() {
        if (level != null) level.raids().publishSnapshots();
    }

    /** 저장·종료(flushAll): 청크를 쓰기 전에 끊긴 플레이어의 탈것을 그 플레이어 데이터로 옮긴다(틱을 기다리지 않는다). */
    public void releaseDisconnectedMounts() {
        for (Player p : players.values()) {
            if (p.isDisconnected()) org.domi.furnace.server.entity.PlayerVehicleSave.onDisconnect(p, world.playerIo(), world.worldDir());
        }
    }

    /** save-all·백업: 접속 중 플레이어의 탈것(RootVehicle)을 그 플레이어 데이터에 덧쓴다(이 region 스레드에서 — 자동 저장과 같은 길). */
    public void saveOnlinePlayerVehicles() {
        for (Player p : players.values()) {
            if (!p.isDisconnected()) org.domi.furnace.server.entity.PlayerVehicleSave.autosave(p, world.playerIo(), world.worldDir());
        }
    }

    /** 로드된 dirty 청크 전부 저장(스냅샷→IO write), dirty clear. */
    public void saveDirtyChunks() {
        autosaveEntityQueue = null;
        autosaveChunkQueue = null;
        saveEntities();
        if (dirtyChunks.isEmpty()) return;
        for (ChunkPos cp : new ArrayList<>(dirtyChunks)) {
            ChunkColumn col = ownedChunk(cp.x(), cp.z());
            // 레인이 밀렸다 — 남은 dirty 청크(cp 포함, 아직 지우지 않았다)를 자동 저장 큐로 넘겨 다음 틱부터 나눠 저장
            if (world.ioBackedUp()) { autosaveChunkQueue = new java.util.ArrayDeque<>(dirtyChunks); return; }
            if (col != null && col.dirty()) {
                writeChunkTicks(cp.x(), cp.z(), col, false);
                world.saveChunkAsync(ChunkSnapshot.forSave(cp.x(), cp.z(), col), col);
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
        publishRaids();
        world.autosaveDragonFight(this);
        var store = world.raidStore();
        if (io != null) io.execute(store::saveIfDirty);   // raids.dat(다른 region 은 마지막으로 올린 스냅샷)
        world.logStorageOnce();
        autosaveEntityQueue = new java.util.ArrayDeque<>(present);
        autosaveChunkQueue = new java.util.ArrayDeque<>(dirtyChunks);
    }

    /** 이 틱의 저장 마감 시각 — 퇴출이 먼저 쓰고 남은 시간을 자동 저장이 쓴다(예산 하나를 공유). */
    private long saveDeadline;

    private void stepAutosave() {
        // 퇴출이 예산을 다 써도 틱마다 적어도 하나는 저장한다(아니면 자동 저장이 영영 진행되지 않을 수 있다). 마감은 처리 뒤에 본다.
        long deadline = saveDeadline;
        if (world.ioBackedUp()) return;
        Long key;
        while (autosaveEntityQueue != null && (key = autosaveEntityQueue.poll()) != null) {
            if (world.ioBackedUp()) { autosaveEntityQueue.addFirst(key); return; }
            int cx = Regionizer.chunkX(key), cz = Regionizer.chunkZ(key);
            ChunkColumn col = level == null ? null : ownedChunk(cx, cz);
            if (col != null) {
                var root = level.chunkEntitiesForSave(col, cx, cz, false);
                if (root != null) world.saveEntitiesAsync(cx, cz, root);
                savePoi(cx, cz, col);
            }
            if (System.nanoTime() > deadline) return;
        }
        ChunkPos cp;
        while (autosaveChunkQueue != null && (cp = autosaveChunkQueue.poll()) != null) {
            ChunkColumn col = ownedChunk(cp.x(), cp.z());
            if (world.ioBackedUp()) { autosaveChunkQueue.addFirst(cp); return; }   // 레인이 밀렸다 — 다음 틱에
            if (col != null && col.dirty()) {
                writeChunkTicks(cp.x(), cp.z(), col, false);
                world.saveChunkAsync(ChunkSnapshot.forSave(cp.x(), cp.z(), col), col);
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
            savePoi(cx, cz, col);
        }
    }

    /** POI 가 바뀌었으면 poi/ 에 쓴다(바닐라 SectionStorage.saveDirty — 자동 저장과 내릴 때). */
    private void savePoi(int cx, int cz, ChunkColumn col) {
        var poi = col.poi();
        if (poi == null || !poi.dirty()) return;
        var root = poi.save(org.domi.furnace.server.world.anvil.AnvilChunkCodec.DATA_VERSION);
        poi.clearDirty();
        if (root != null) world.savePoiAsync(cx, cz, root);
    }

    /** 구독자 0인 "있는 청크"를 grace 후 퇴출(dirty면 저장). 로드 중인 청크는 끝날 때까지 둔다. 매 틱 호출. */
    private void evictIdleChunks() {
        List<Long> drop = null;
        for (long key : present) {
            ChunkPos cp = new ChunkPos(Regionizer.chunkX(key), Regionizer.chunkZ(key));
            if (!world.subscribers(cp).isEmpty() || world.loadingChunk(key) != null || world.ticketed(key)) { evictGrace.remove(cp); continue; }
            int g = evictGrace.merge(cp, 1, Integer::sum);
            if (g >= world.chunkEvictionGraceTicks()) {
                if (drop == null) drop = new ArrayList<>();
                drop.add(key);
            }
        }
        if (drop == null) return;
        // 퇴출(저장 포함)도 틱당 시간 예산 안에서 — 생성 청크도 dirty 라(F-14) 한꺼번에 내리면 틱이 수백 ms 튄다. 남은 것은 다음 틱에.
        long deadline = saveDeadline;
        for (long key : drop) {
            if (System.nanoTime() > deadline || world.ioBackedUp()) break;   // 내리지 않고 다음 틱에(레인 역압 포함)
            int cx = Regionizer.chunkX(key), cz = Regionizer.chunkZ(key);
            ChunkPos cp = new ChunkPos(cx, cz);
            ChunkColumn col = world.loadedChunk(cx, cz);
            if (col != null) {
                if (level != null) {   // 엔티티는 저장한 뒤 내린다(UNLOADED_TO_CHUNK)
                    var root = level.chunkEntitiesForSave(col, cx, cz, true);
                    if (root != null) world.saveEntitiesAsync(cx, cz, root);
                }
                savePoi(cx, cz, col);
                for (var be : col.blockEntities().values()) be.setRemoved();   // 틱 목록에서 빠진다(다시 불러오면 새로 만든다)
                // 예약 틱이 남아 있으면 바뀐 게 없어도 저장한다(내리면 사라지므로)
                if (writeChunkTicks(cx, cz, col, true) || col.dirty()) {
                    world.saveChunkAsync(ChunkSnapshot.forSave(cx, cz, col), col);
                    col.clearDirty();
                }
                world.removeChunk(cx, cz);
                world.levelServices().gameEvents().chunkUnload(world.dimension(), cx, cz);   // 플러그인 ChunkUnloadEvent
            }
            if (level != null) level.raids().park(cx, cz);   // 중심 청크가 내려간 습격은 저장소로
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

    /** 저장이 끝내 실패했다 — 그 청크가 아직 이 region 에 그대로 있으면 다시 dirty 로(다음 저장에 다시 싣는다). */
    void restoreDirtyAfterFailedSave(ChunkPos cp, ChunkColumn col) {
        if (world.loadedChunk(cp.x(), cp.z()) != col) {
            org.slf4j.LoggerFactory.getLogger(Region.class).error("[SAVE] 청크 {} 저장 실패 — 이미 내려가 되돌릴 수 없음(유실)", cp);
            return;
        }
        col.markDirty();
        dirtyChunks.add(cp);
    }

    int dirtyChunkCount() {
        return dirtyChunks.size();
    }

    /** 이 region 스레드에서 플레이어 편입. */
    public void adopt(Player p) {
        world.assertOwns(this);
        players.put(p.entityId(), p);
        observedPlayers = players.size();
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

    /**
     * 수동 시계(헤드리스 시험 서버, Regionizer.manualClock): 이 region 이 마친 시험 틱 수. 잠금 안에서만 읽고 쓴다.
     * manualPass 는 이번 차례가 시험 틱인지(tryTake 가 잠금 안에서 정하고 afterTick 이 센다).
     */
    long manualDone;
    boolean manualPass;

    /** /tick step: 이 region 이 본 step 세대와 남은 걸음(region 스레드 전용). */
    private int stepGeneration;
    private int stepsLeft;

    /** 새 region 이 /tick step 상태를 이어받는다: 생성 시점 세대(남은 걸음 0) 또는 나눈 부모의 세대·남은 걸음. */
    void inheritSteps(int generation, int left) {
        this.stepGeneration = generation;
        this.stepsLeft = left;
    }

    int stepGeneration() { return stepGeneration; }

    int stepsLeft() { return stepsLeft; }

    /** /tick sprint: 이 region 이 본 sprint 세대와 남은 sprint 틱(region 스레드 전용). */
    private int sprintGeneration;
    private int sprintLeft;
    /** 틱 뒤(Regionizer 잠금 안)에 남기는 요약: 본 sprint 세대, 아직 sprint 틱이 남았는지 — 전역이 sprint 끝을 판정할 때 잠금 안에서 읽는다. */
    int sprintSeen;
    boolean sprintPending;
    /** 이 sprint 에 참여하는가(세대를 sprint 중에 처음 봤거나 참여하던 부모에서 나뉨) — 새로 생긴 region 은 참여하지 않는다. */
    private boolean sprintJoined;
    /** 이번 틱을 전역을 기다리느라 진행하지 않았다(Regionizer 가 곧 다시 잡는다). */
    private boolean sprintHeld;
    /** 요약: 참여 여부와 이번 sprint 에서 마친 틱 수(잠금 안). */
    boolean sprintJoinedSummary;
    long sprintDoneSummary;

    /** 새 region 이 sprint 상태를 이어받는다: 생성 시점 세대(남은 틱 0) 또는 나눈 부모의 세대·남은 틱. */
    void inheritSprint(int generation, int left, boolean joined) {
        this.sprintGeneration = generation;
        this.sprintLeft = left;
        this.sprintJoined = joined;
        this.sprintSeen = generation;
        this.sprintPending = left > 0;
        this.sprintJoinedSummary = joined;
        this.sprintDoneSummary = joined ? world.tickState().sprintTicks() - left : 0;
    }

    boolean sprintJoined() { return sprintJoined; }

    /** 세대가 바뀌면 참여 여부(그 순간 sprint 중인가)와 남은 sprint 틱(N 또는 0)을 다시 채운다. */
    private void refreshSprintGeneration(TickState ts) {
        if (sprintGeneration != ts.sprintGeneration()) {
            sprintGeneration = ts.sprintGeneration();
            sprintJoined = ts.sprinting();
            sprintLeft = sprintJoined ? ts.sprintTicks() : 0;
        }
    }

    /** lockstep: 남은 sprint 틱이 있는데 마친 틱 수가 전역이 시작한 틱 수에 닿았으면 참(이번 차례는 기다린다). */
    private boolean sprintHoldsNow() {
        TickState ts = world.tickState();
        refreshSprintGeneration(ts);
        return sprintLeft > 0 && ts.sprintTicks() - sprintLeft >= ts.sprintGlobalDone();
    }

    /** 병합(Regionizer 잠금 안, 둘 다 쉬는 중): 참여하지 않던 이 region 이 참여 region 을 흡수하면 그 sprint 상태를 이어받는다. */
    void adoptSprintFrom(Region o) {
        if (sprintJoined || !o.sprintJoined || o.sprintGeneration != world.tickState().sprintGeneration()) return;
        inheritSprint(o.sprintGeneration, o.sprintLeft, true);
    }

    boolean sprintHeld() { return sprintHeld; }

    int sprintGeneration() { return sprintGeneration; }

    int sprintLeft() { return sprintLeft; }

    public void tick() {
        world.assertOwns(this);
        if (Regionizer.STATS) lapT = System.nanoTime();
        // /tick freeze: 정지 중이면 월드 진행만 건너뛴다(플레이어 처리·변경 방송은 계속). /tick step 은 정지 중에도 N 번 진행한다.
        TickState tickState = world.tickState();
        boolean frozen = tickState.frozen();
        if (stepGeneration != tickState.generation()) {
            stepGeneration = tickState.generation();
            stepsLeft = tickState.requestedSteps();
        }
        if (frozen && stepsLeft > 0) {
            stepsLeft--;
            frozen = false;
        }
        // /tick sprint: sprint 틱이면 정지여도 진행한다. 전역을 앞서는지는 runTick 이 먼저 보고 이 틱 자체를 건너뛴다(lockstep).
        refreshSprintGeneration(tickState);
        if (sprintLeft > 0) {
            sprintLeft--;
            frozen = false;
        }
        if (level != null) {   // 바닐라 ServerLevel.tick: 블록·유체 예약 틱 → 청크 틱(무작위 틱) → 청크 변경 방송 → 블록 이벤트 → 엔티티
            int n = frozen ? 0 : collectTickingChunks();
            level.tickBlocks(tickingChunks, n, frozen ? 0 : spawnChunkCount, this::blockEntityTicking, pl -> players.containsKey(pl.entityId()), frozen);
            lap(0);
            level.runLightUpdates();
            lap(1);
        }
        List<Player> handoff = new ArrayList<>();
        var it = players.values().iterator();
        while (it.hasNext()) {
            Player p = it.next();
            if (p.isDisconnected()) {
                // ServerPlayer.disconnect: 자고 있었으면 깨운다(목록 갱신은 빠진 뒤 — onTrackingEnd)
                if (p.isSleeping() && level != null) org.domi.furnace.server.entity.PlayerSleeping.stopSleepInBed(p, level, true, false);
                org.domi.furnace.server.entity.PlayerVehicleSave.onDisconnect(p, world.playerIo(), world.worldDir());   // PlayerList.remove 의 탈것
                it.remove();
                for (ChunkPos cp : p.loadedChunks()) world.unsubscribeChunk(cp, p);
                for (Integer eid : p.tracked().keySet()) world.removeTracker(eid, p);
                // 저장은 ConnectionHandler.channelInactive가 IO 위치+인벤토리로 수행(여기선 0,0,0 위험).
                world.playerOffline(p.entityId());   // 다른 관찰자는 grace 후 탭에서 뺀다
                world.updateSleepingPlayerList();   // ServerLevel EntityCallbacks.onTrackingEnd
                broadcastRemove(p.entityId());
                if (level != null) level.playerLeft(p);
                continue;
            }
            p.tickDropSpam();
            // 바닐라 ServerGamePacketListenerImpl.tick: 마지막 행동 뒤 player-idle-timeout 분이 지나면 쫓아낸다
            int idleMinutes = world.levelServices().playerIdleTimeoutMinutes();
            if (p.kickReason() == null && org.domi.furnace.server.entity.PlayerIdle.timedOut(System.currentTimeMillis(), p.lastActionTime(), idleMinutes)) {
                p.kick(new org.domi.furnace.server.net.protocol.play.Disconnect(org.domi.furnace.api.text.Text.translatable("multiplayer.disconnect.idling")));
            }
            p.tickCooldowns();
            p.tickAttackStrength();
            p.tickSwing();
            p.tickRiding();
            tickCamera(p);
            // 바닐라 ChunkMap.move — 이동 패킷마다 엔티티 보임을 다시 본다(탄 동안은 회전 패킷이 매 틱 온다)
            double tickX = p.x(), tickZ = p.z();   // 바닐라 xOld·zOld(틱 머리 setOldPosAndRot)
            boolean moved = processMoves(p);
            if ((moved || p.vehicle() != null) && level != null) level.playerMoved(p);
            if (p.vehicle() == null) {   // 바닐라는 연결 틱마다 서버에서 travel→move 를 한 번 돌린다: 움직이지 않아도 불 블록 안이면 불 틱이 오르고, 끝에서 불이 꺼진다
                checkInsideBlocksPlayer(p, 0.0, 0.0, true);
                org.domi.furnace.server.entity.PlayerFire.afterMove(p, level, p.wasTouchingWater);
            }
            if (moved) {
                checkInsideBlocksPlayer(p, p.x() - tickX, p.z() - tickZ, false);
                org.domi.furnace.server.entity.PlayerFire.afterMove(p, level, p.wasTouchingWater);   // Entity.move 의 끝: 불·용암이 없으면 면역 틱, 비·물에서 꺼짐
            }
            p.drainPending();
            org.domi.furnace.server.entity.PlayerMoveValidator.tick(p);   // 틱 머리: 기준 위치·패킷 수·떠 있는 틱(비행 킥)
            p.currentLevel = level;
            p.ownerRegion = this;
            p.drainOwnerTasks();   // 다른 스레드가 맡긴 플레이어 작업(발전 과제·통계·점수) — 주인 스레드에서
            if (level != null) org.domi.furnace.server.entity.PlayerSleeping.tick(p, level);   // Player.tick 머리의 잠 카운터·침대 확인
            tickVitals(p);
            org.domi.furnace.server.entity.PlayerShoulders.tick(p);   // 어깨 몹 울음·내려놓기 조건(aiStep 끝)
            if (level != null && world.probe() != null) org.domi.furnace.server.entity.PlayerPose.update(p, level, world.probe(), blocks);
            org.domi.furnace.server.entity.ElytraFlight.tick(p, level);   // aiStep 의 updateFallFlying·travel(겉날개)
            p.sendPendingKnockback();   // hurtMarked 가 선 틱에만 속도 패킷 한 번
            // Entity.checkInsideBlocks 의 포털 칸 + baseTick 의 handlePortal(차원을 옮기면 이 region 에서 빠진다)
            if (level != null && checkInsidePortals(p)) { it.remove(); continue; }
            if (level != null && handlePlayerPortal(p)) { it.remove(); continue; }
            org.domi.furnace.server.map.MapItems.tick(p, MapTerrain::new, p.dimension(), world.dayTime());   // Inventory.tick 의 지도 + 갱신 패킷
            if (p.takeXpDelay > 0) p.takeXpDelay--;
            org.domi.furnace.server.entity.Consuming.tick(p, this);
            org.domi.furnace.server.entity.Tridents.tickSpin(meleeWorld, p, t -> meleeAttack(p, t));   // 급류 회전 공격
            p.tickArrowCount();    // 박힌 화살이 20 × (30 − 수) 틱마다 하나씩 빠진다
            p.syncLivingFlags();   // 사용 중(먹기·활·방패) 플래그를 다른 사람에게
            org.domi.furnace.server.entity.PlayerLocationEffects.tick(p, level, () -> onSoulSpeedBlock(p));   // 칸 이동·발 장비 변경 시 영혼 가속·서리 걸음
            org.domi.furnace.server.entity.PlayerLocationEffects.tickEffects(p, level, () -> onSoulSpeedBlock(p));   // 영혼 가속 tick 효과(소울 입자·소리)
            p.syncEquipment();     // 든 아이템·방어구가 바뀌면 다른 사람에게(set_equipment)
            pushEntities(p);
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
        saveDeadline = System.nanoTime() + AUTOSAVE_BUDGET_NANOS;
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
            if (y < c.minY() || y > c.maxY()) return ChunkSection.AIR;
            return c.getBlock(x & 15, y, z & 15);
        }
    };

    /** 이번 틱까지 들어온 위치 패킷들을 순서대로 — 점프·이동 소모와 낙하 판정(바닐라 handleMovePlayer 마다). */
    /** 이 region 이 그 플레이어를 가졌는가(region 스레드에서). */
    public boolean hasPlayer(Player p) { return players.containsKey(p.entityId()); }

    private boolean processMoves(Player p) {
        PlayerProbe probe = world.probe();
        Player.MoveSample m;
        boolean any = false;
        p.validatesMoves = true;   // 이 region 이 검증한다 — 연결은 위치를 발행하지 않고 검증 대기열에 넣는다
        while ((m = p.pollMove()) != null) {
            any = true;
            if (m.teleport()) {
                // 서버가 정한 자리: 뒤따르는 검증 패킷의 기준이 되도록 위치를 먼저 맞춘다(회전은 곧 드레인이 맞춘다)
                p.applyMove(m.x(), m.y(), m.z(), p.yaw(), p.pitch(), p.onGround());
                org.domi.furnace.server.entity.PlayerMoveValidator.teleported(p, m.x(), m.y(), m.z());
            } else if (m.check() != null) {
                // 바닐라 handleMovePlayer 검증: 되돌렸으면 이 패킷은 없던 것(위치·소모 계산 없음)
                if (p.moveRejectHold) continue;
                if (p.vehicle() != null) {
                    // 바닐라 handleMovePlayer 의 isPassenger 분기: 탄 동안 받은 위치는 버리고 회전만 받는다.
                    // 타기 직전에 보낸 걸음 패킷이 탄 뒤에 검증되면 탈것 위 자리와 비교해 moved wrongly 로 되돌리던 것을 막는다.
                    var c = m.check();
                    if (c.hasRot()) p.applyRotation(org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(c.yaw()),
                            org.domi.furnace.server.entity.ai.control.Mth.wrapDegrees(c.pitch()), p.onGround());
                    continue;
                }
                if (!org.domi.furnace.server.entity.PlayerMoveValidator.handle(level(), p, m.check(), m.onGround())) continue;
                m = new Player.MoveSample(p.x(), p.y(), p.z(), m.onGround(), false);
            }
            Player.MoveSample prev = p.moveAnchor();
            if (!m.teleport() && prev != null && (m.x() != prev.x() || m.y() != prev.y() || m.z() != prev.z())
                    && !world.levelServices().gameEvents().playerMove(p, prev.x(), prev.y(), prev.z(), p.yaw(), p.pitch(), m.x(), m.y(), m.z(), p.yaw(), p.pitch())) {
                // 플러그인 PlayerMoveEvent 취소: 검증기가 이미 옮긴 서버 위치를 되돌리고 보정 텔레포트(검증 거부와 같은 방식, 이벤트 우회)
                p.applyMove(prev.x(), prev.y(), prev.z(), p.yaw(), p.pitch(), prev.onGround());
                if (p.hasTeleporter()) {
                    p.moveRejectHold = true;
                    p.correctTo(prev.x(), prev.y(), prev.z(), p.yaw(), p.pitch());
                }
                continue;
            }
            p.moveAnchor(m);
            if (m.teleport() || prev == null || probe == null) continue;
            boolean sneaking = p.sneaking();
            double height = p.bbHeight();
            double eye = p.eyeY() - p.y();
            double dx = m.x() - prev.x(), dy = m.y() - prev.y(), dz = m.z() - prev.z();
            int[] landingPos = probe.onPosLegacyPos(blocks, m.x(), m.y(), m.z(), m.onGround());
            int landingState = blocks.stateAt(landingPos[0], landingPos[1], landingPos[2]);
            String landing = landingState < 0 ? null : probe.nameOf(landingState);
            boolean inWater = probe.inWater(blocks, m.x(), m.y(), m.z(), height);
            boolean eyeInWater = probe.eyeInWater(blocks, m.x(), m.y(), m.z(), eye);
            String climbable = probe.climbable(blocks, m.x(), m.y(), m.z());
            boolean waterOnPath = dy < 0 && probe.waterOnPath(blocks, m.x(), prev.y(), m.y(), m.z());
            p.vitals().currentMoveY(m.y());
            p.vitals().landingStalagmite(landingState >= 0 && org.domi.furnace.server.block.B.behavior(landingState)
                    instanceof org.domi.furnace.server.block.types.PointedDripstoneBlock
                    && org.domi.furnace.server.block.types.PointedDripstoneBlock.isStalagmiteTip(landingState));
            p.vitals().onMove(dx, dy, dz, prev.onGround(), m.onGround(), p.sprinting(), sneaking,
                    inWater, eyeInWater, climbable, landing, waterOnPath);
            float landed = p.vitals().landedFallDistance();
            if (landed > 0.0f && level != null && landingState >= 0) {   // ServerPlayer.checkFallDamage: 철퇴 착지 입자가 LivingEntity 입자보다 먼저
                org.domi.furnace.server.entity.LandingParticles.spawnMaceDustPillar(level, p.vitals(), landingPos[0], landingPos[1], landingPos[2], landingState, landed);
            }
            if (landed > 0.0f && level != null && landingState >= 0) {
                // 바닐라 LivingEntity.checkFallDamage 의 착지 블록 입자(ServerPlayer.doCheckFallDamage → super): 발밑 칸 getOnPosLegacy
                org.domi.furnace.server.entity.LandingParticles.spawn(level, m.x(), m.y(), m.z(),
                        new org.domi.furnace.server.block.Pos((int) Math.floor(m.x()), (int) Math.floor(m.y()), (int) Math.floor(m.z())),
                        new org.domi.furnace.server.block.Pos(landingPos[0], landingPos[1], landingPos[2]), landingState, landed,
                        p.attributes().value(org.domi.furnace.server.attribute.Attribute.SAFE_FALL_DISTANCE));
            }
            if (landed > 0.0f && level != null) {
                // 바닐라 doCheckFallDamage → Block.fallOn(getOnPosLegacy 칸). 피해는 위 onMove 가 이미 처리했다
                int[] at = probe.onPos(blocks, 0.2f, m.x(), m.y(), m.z(), m.onGround());
                var onPos = new org.domi.furnace.server.block.Pos(at[0], at[1], at[2]);
                int state = level.getBlockState(onPos);
                if (org.domi.furnace.server.block.types.FarmBlock.isFarmland(state)) {
                    org.domi.furnace.server.block.types.FarmBlock.playerFallOn(level, state, onPos, new org.domi.furnace.server.block.PlayerActor(p),
                            landed, p.bbWidth(), p.bbHeight());
                }
            }
            // Block.fallOn 의 블록 쪽(피해는 위에서): 거북 알은 1/3 로 하나 깨진다
            if (level != null && landingState >= 0 && p.gameMode() != GameMode.SPECTATOR && p.vitals().landedOnLastMove()
                    && org.domi.furnace.server.block.B.behavior(landingState) instanceof org.domi.furnace.server.block.types.TurtleEggBlock egg) {
                egg.fallOnPlayer(level, new org.domi.furnace.server.block.Pos(landingPos[0], landingPos[1], landingPos[2]), landingState);
            }
            if (landed > 0.0f && level != null) {
                // Entity.checkFallDamage: fallOn 뒤 HIT_GROUND 게임 이벤트. 맥락 블록은 지지 블록(getOnPos 1e-5 아래; 지지 블록이 없을 때 바닐라는 getOnPosLegacy 칸 — 여기선 발 밑 칸으로 근사)
                int[] support = probe.onPos(blocks, 1.0E-5f, m.x(), m.y(), m.z(), m.onGround());
                int hitState = blocks.stateAt(support[0], support[1], support[2]);
                level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.HIT_GROUND,
                        new org.domi.furnace.server.entity.Vec3(m.x(), m.y(), m.z()),
                        org.domi.furnace.server.world.gameevent.GameEventContext.of(p, hitState < 0 ? landingState : hitState));
            }
            if (level != null && p.gameMode() != GameMode.SPECTATOR) {
                p.movementEmission.onMove(level, p, m.x(), m.y(), m.z(), dx, dy, dz, m.onGround(), inWater);
            }
        }
        return any;
    }

    /**
     * Player.touch(바닐라 aiStep 끝): 살아 있고 관전자가 아니면 몸통을 (1, 0.5, 1) 넓힌 상자에 걸친 엔티티마다 playerTouch(아이템 줍기).
     */
    /**
     * LivingEntity.pushEntities(서버, ServerPlayer.doTick 의 aiStep): 플레이어 몸통에 걸친 밀 수 있는 엔티티·플레이어를 모아
     * maxEntityCramming 판정(1/4 확률 → cramming 6 피해) 뒤 후보마다 후보.push(player). 다른 플레이어 쪽 속도는 클라이언트 권위라 버린다.
     */
    private void pushEntities(Player p) {
        if (level == null) return;
        if (p.vitals().health() <= 0.0F) return;
        level.pushScratch.pushFor(level, p);
    }

    private void touchEntities(Player p) {
        if (level == null || level.entities().isEmpty()) return;
        if (p.vitals().health() <= 0.0F || p.gameMode() == GameMode.SPECTATOR) return;
        double h = p.bbHeight();
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
            if (e.isPassenger()) continue;   // 승객은 뿌리 탈것을 따라간다
            java.util.List<org.domi.furnace.server.entity.Entity> moving = new java.util.ArrayList<>();
            moving.add(e);
            if (e.isVehicle()) {
                java.util.List<org.domi.furnace.server.entity.Rider> riders = new java.util.ArrayList<>();
                e.collectIndirectPassengers(riders);
                for (var r : riders) if (r instanceof org.domi.furnace.server.entity.Entity pe && level.entities().contains(pe)) moving.add(pe);
            }
            for (var m : moving) level.entities().remove(m);
            world.runAtChunk(new ChunkPos(cx, cz), r -> { for (var m : moving) r.level().adoptEntity(m); });
        }
    }

    // ------------------------------------------------------------------ 청크 틱 대상(ServerChunkCache.tickChunks)

    private long[] tickingChunks = new long[64];

    /**
     * 무작위 틱 받을 청크: 이 region 이 가진 로드된 청크 중, 관전자가 아닌 플레이어가 시뮬레이션 거리 안(체비셰프 ≤ min(거리, 11) − 1,
     * 자연 스폰 범위 8 청크 안)이고 청크 가운데에서 128 블록 안(anyPlayerCloseEnoughForSpawning)인 것. 순서는 레벨이 섞는다.
     */
    private int collectTickingChunks() {
        spawnChunkCount = 0;
        if (players.isEmpty()) return 0;
        org.domi.furnace.server.block.BlockBehaviors.ofBlock(0);   // 무작위 틱 표를 섹션에 건다
        int sim = Math.clamp(world.levelServices().simulationDistance(), 2, 11) - 1;
        int range = Math.min(sim, 8);
        int n = 0;
        if (!players.isEmpty()) {
            for (long key : present) {
                int cx = Regionizer.chunkX(key), cz = Regionizer.chunkZ(key);
                if (!closeToPlayer(cx, cz, range)) continue;
                ChunkColumn col = ownedChunk(cx, cz);
                if (col == null) continue;
                col.inhabitedTime(col.inhabitedTime() + 1);   // LevelChunk.incrementInhabitedTime(1)
                if (n == tickingChunks.length) tickingChunks = java.util.Arrays.copyOf(tickingChunks, n * 2);
                tickingChunks[n++] = key;
            }
        }
        // 바닐라 ServerChunkCache.tickChunks: 무작위 틱·스폰·inhabitedTime 은 anyPlayerCloseEnoughForSpawning 청크만 — 강제 로드 청크도 예외 없다
        spawnChunkCount = n;
        return n;
    }

    /** collectTickingChunks 가 모은 앞쪽 몇 칸이 자연 스폰 대상(플레이어 근처)인가. 뒤는 강제 로드 청크. */
    private int spawnChunkCount;

    /**
     * 그 청크가 블록 엔티티 틱을 받는가 — 바닐라 LevelChunk.isTicking: 엔티티 틱 단계(플레이어 표가 시뮬레이션 거리 - 1 안,
     * 체비셰프)인 청크. 키는 (x << 32 | z).
     */
    private boolean blockEntityTicking(long key) {
        int cx = (int) (key >> 32), cz = (int) key;
        var forced = world.forcedChunksOrNull();
        if (forced != null && forced.size() > 0 && forced.contains(cx, cz)) return true;   // /forceload: 엔티티 틱 레벨 티켓
        if (world.entityTicketed(cx, cz)) return true;   // 포털·드래곤 티켓의 엔티티 틱 범위
        int range = Math.max(world.levelServices().simulationDistance(), 2) - 1;
        for (Player p : players.values()) {
            int pcx = (int) Math.floor(p.x()) >> 4, pcz = (int) Math.floor(p.z()) >> 4;
            if (Math.max(Math.abs(pcx - cx), Math.abs(pcz - cz)) <= range) return true;
        }
        return false;
    }

    /**
     * /execute if loaded 의 바닐라 조건: 청크가 FULL 이상이고 엔티티 틱 단계(ENTITY_TICKING)이며 엔티티가 불러와졌다(areEntitiesLoaded).
     * 이 region 이 가진 청크만 안다. 이 region 스레드에서만.
     */
    public boolean isEntityTickingLoaded(int cx, int cz) {
        world.assertOwns(this);
        ChunkColumn col = ownedChunk(cx, cz);
        return col != null && col.entitiesLoaded() && blockEntityTicking(((long) cx << 32) | (cz & 0xFFFFFFFFL));
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

    /** 플레이어가 살아 있는 엔티티를 때린다(Player.attack — PlayerMelee). */
    public void attack(Player p, org.domi.furnace.server.entity.LivingEntity target) {
        world.assertOwns(this);
        if (!players.containsKey(p.entityId()) || !target.isAlive()) return;
        meleeAttack(p, target);
    }

    /** 플레이어가 다른 플레이어를 때린다(pvp 는 피해 쪽 ServerPlayer.hurt 의 canHarmPlayer 가 본다). */
    public void attackPlayer(Player p, Player target) {
        world.assertOwns(this);
        if (target == p || !players.containsKey(p.entityId()) || !players.containsKey(target.entityId()) || !target.isAlive()) return;
        if (target.gameMode() == GameMode.SPECTATOR) return;
        meleeAttack(p, target);
    }

    /** 근접 공격 본체 + 무기 내구도(postHurtEnemy, 손 사본으로 깎고 연결 인벤토리에 반영). */
    private org.domi.furnace.server.entity.PlayerMelee.Result meleeAttack(Player p, org.domi.furnace.server.entity.Targetable target) {
        var r = org.domi.furnace.server.entity.PlayerMelee.attack(meleeWorld, p, target);
        if (r.weaponDamage() > 0) {
            var actor = breakerActor(p);
            ItemStack w = actor.itemInHand(org.domi.furnace.server.block.BlockActor.MAIN_HAND);
            org.domi.furnace.server.block.BlockBreaking.hurtAndBreak(level(), actor, org.domi.furnace.server.block.BlockActor.MAIN_HAND, w, r.weaponDamage());
            writeBackHand(p, actor);
        }
        return r;
    }

    private final org.domi.furnace.server.entity.PlayerMelee.World meleeWorld = new org.domi.furnace.server.entity.PlayerMelee.World() {
        public org.domi.furnace.server.block.ChunkLevel level() { return Region.this.level(); }
        public Iterable<Player> players() { return java.util.List.copyOf(players.values()); }
    };

    /** 엔더 드래곤 파트를 때린다(Player.attack — PlayerMelee: 쿨다운·힘/나약함·마법 부여는 그대로, 치명타·내구도·통계는 LivingEntity 가 아니라 없다). */
    public void attackDragonPart(Player p, org.domi.furnace.server.entity.EnderDragonPart part) {
        world.assertOwns(this);
        if (!players.containsKey(p.entityId()) || p.gameMode() == GameMode.SPECTATOR) return;
        meleeAttack(p, part);
    }

    /**
     * 생물이 아닌 엔티티(보트·수레·액자·그림·수정 …)를 때린다(Player.attack — PlayerMelee). skipAttackInteraction 이면 그쪽이 처리하고,
     * 튕길 수 있는 투사체는 시선 방향으로 튕기고 끝난다. 그 밖에는 생물과 같은 쿨다운·힘/나약함·마법 부여·소리·밀치기를 탄다.
     */
    public void attackEntity(Player p, org.domi.furnace.server.entity.Entity target) {
        world.assertOwns(this);
        if (!players.containsKey(p.entityId()) || target.isRemoved()) return;
        if (!target.isAttackable() || target.skipAttackInteraction(p)) return;
        // Player.attack: redirectable_projectile 는 시선 방향으로 튕기고(AIM_DEFLECT, 주인은 플레이어) 피해·소모 없이 끝난다
        if (target instanceof org.domi.furnace.server.entity.Projectile fb && fb.isRedirectable() && fb.deflectAim(p, p, true)) {
            p.attackStrengthTicker = 0;   // 바닐라 resetAttackStrengthTicker 는 튕김 분기보다 앞이다(cmx.e 오프셋 100; 튕기지 못하면 melee 가 직접 초기화)
            level().playSound(null, p.x(), p.y(), p.z(), "entity.player.attack.nodamage", "players", 1.0F, 1.0F);
            return;
        }
        meleeAttack(p, new org.domi.furnace.server.entity.PlayerMelee.Raw(target));
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
        if (!world.levelServices().gameEvents().playerInteract(p, hand, null, 0, 0, actor.itemInHand(hand))) {   // 플러그인 PlayerInteractEvent(허공 우클릭) 취소
            org.domi.furnace.server.plugin.ClientResync.hand(p, hand);   // 클라이언트 예측 되돌림
            return;
        }
        if (p.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR && org.domi.furnace.server.entity.Consuming.tryStart(p, hand, actor.itemInHand(hand))) return;
        float y = wrapDegrees(yRot), x = wrapDegrees(xRot);
        if (x != p.pitch() || y != p.yaw()) p.applyRotation(y, Math.clamp(x, -90.0F, 90.0F), p.onGround());   // absRotateTo
        actor = breakerActor(p);
        if (p.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR && !actor.isOnCooldown(actor.itemInHand(hand).itemId())
                && org.domi.furnace.server.entity.RangedWeapons.use(level(), actor, p, hand)) {   // 활·쇠뇌·낚싯대·삼지창
            writeBackHand(p, actor);
            return;
        }
        var result = org.domi.furnace.server.block.ItemUse.useItem(level(), actor, hand);
        if (result.shouldSwing()) p.swing(hand, true);   // handleUseItem: SUCCESS 류면 본인 포함 animate(서버 쪽 결과는 대개 CONSUME)
        writeBackHand(p, actor);
    }

    /** 솔 사용 틱(BrushItem.onUseTick): 계속 쓰면 true, 시선이 블록을 벗어났으면 false. 이 플레이어가 이 region 소속일 때만. */
    public boolean tickBrush(Player p) {
        world.assertOwns(this);
        if (!players.containsKey(p.entityId()) || p.usingHand < 0) return false;
        var actor = breakerActor(p);
        boolean keep = org.domi.furnace.server.entity.Brushing.onUseTick(level(), actor, p, p.usingHand, p.usingTicks);
        writeBackHand(p, actor);
        return keep;
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
        return playerDrop(p, stack, dropAround, includeThrower, true);
    }

    /**
     * callEvent 거짓이면 플러그인 PlayerDropItemEvent 를 부르지 않는다 — CraftBukkit 이 /give 의 가짜 아이템(줍는 연출)에 쓰는 drop(…, false)
     * (SPIGOT-2942). 아이템은 이미 인벤토리에 들어갔으므로 취소할 것이 없다.
     */
    public org.domi.furnace.server.entity.ItemEntity playerDrop(Player p, ItemStack stack, boolean dropAround, boolean includeThrower, boolean callEvent) {
        world.assertOwns(this);
        if (stack.isEmpty()) return null;
        if (callEvent && !world.levelServices().gameEvents().playerDrop(p, stack)) {
            // 플러그인 PlayerDropItemEvent 취소(CraftBukkit): 든 칸으로 돌려놓고 — 안 되면 첫 빈 칸 — 클라이언트가 먼저 비운 칸·커서를 되돌려 보낸다
            stack = org.domi.furnace.server.plugin.ClientResync.returnToSlot(p, p.inventory().heldWindowSlot(), stack);
            if (!stack.isEmpty()) stack = p.inventory().add(stack, false, org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(stack), false);
            org.domi.furnace.server.plugin.ClientResync.inventory(p);
            if (stack.isEmpty()) return null;
        }
        RegionLevel l = level();
        double eye = p.eyeY() - 0.3F;
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
        handleBlockBreakAction(p, pos, action, 1, mayInteract);
    }

    /**
     * 팔 휘두르기(swing 패킷)의 플러그인 LEFT_CLICK_AIR — Spigot handleAnimate 와 같이 눈에서 시선 방향 4.5칸(크리에이티브 5칸)에
     * 블록(OUTLINE, 유체 무시)도 집을 수 있는 엔티티도 없을 때만. 구독자가 없으면 시선 추적도 하지 않는다.
     * 가정: 왼손(hand 1) 휘두르기는 왼쪽 클릭이 아니므로 발화하지 않는다.
     */
    public void swingLeftClick(Player p, int hand) {
        world.assertOwns(this);
        if (hand != 0 || !players.containsKey(p.entityId())) return;
        var ev = world.levelServices().gameEvents();
        if (!ev.wantsInteract()) return;
        double ex = p.x(), ey = p.eyeY(), ez = p.z();
        double[] v = org.domi.furnace.server.block.Clip.viewVector(p.pitch(), p.yaw());
        double reach = p.gameMode() == GameMode.CREATIVE ? 5.0 : 4.5;
        double tx = ex + v[0] * reach, ty = ey + v[1] * reach, tz = ez + v[2] * reach;
        if (!org.domi.furnace.server.block.Clip.clip(level(), ex, ey, ez, tx, ty, tz, org.domi.furnace.server.block.Clip.FluidMode.NONE).miss()) return;
        var sweep = new org.domi.furnace.server.entity.AABB(Math.min(ex, tx), Math.min(ey, ty), Math.min(ez, tz),
                Math.max(ex, tx), Math.max(ey, ty), Math.max(ez, tz)).inflate(1.0);
        for (var e : level().getEntities(null, sweep, e -> e.isPickable() && !e.isSpectator() && !e.isRemoved())) {
            if (rayHits(e.boundingBox(), ex, ey, ez, tx, ty, tz)) return;
        }
        for (Player o : players.values()) {
            if (o != p && !o.isSpectator() && !o.isDisconnected() && rayHits(o.boundingBox(), ex, ey, ez, tx, ty, tz)) return;
        }
        Player.InventoryHost inv = p.inventoryHost();
        ItemStack held = inv == null ? ItemStack.EMPTY : inv.slot(36 + inv.selected());
        ev.playerLeftClick(p, null, 0, -1, held);
    }

    private static boolean rayHits(org.domi.furnace.server.entity.AABB bb, double fx, double fy, double fz, double tx, double ty, double tz) {
        boolean inside = fx >= bb.minX() && fx <= bb.maxX() && fy >= bb.minY() && fy <= bb.maxY() && fz >= bb.minZ() && fz <= bb.maxZ();
        return inside || bb.clip(fx, fy, fz, tx, ty, tz) != null;
    }

    /** face: 누른 면(0 아래 … 5 동) — 플러그인 LEFT_CLICK_BLOCK 에 실린다. */
    public void handleBlockBreakAction(Player p, org.domi.furnace.server.block.Pos pos, int action, int face, boolean mayInteract) {
        world.assertOwns(this);
        if (!players.containsKey(p.entityId())) return;
        var actor = breakerActor(p);
        p.breaking().handleAction(level(), actor, breakHost(p, face, actor), pos, action, level().maxBuildHeight(), mayInteract);
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
            double eye = p.eyeY() - p.y();
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

            @Override
            public void swing(int hand) { p.swing(hand, true); }

            @Override
            public void usedEnderEye(org.domi.furnace.server.block.Pos structure) {
                // UsedEnderEyeTrigger.trigger: 플레이어와 구조물 칸의 수평 거리 제곱
                double dx = p.x() - structure.x(), dz = p.z() - structure.z();
                double distSqr = dx * dx + dz * dz;
                p.runOnOwner(() -> {
                    var adv = p.advancements();
                    if (adv != null) adv.trigger("minecraft:used_ender_eye", distSqr);
                });
            }
        }.withInventory(inv);
    }

    private org.domi.furnace.server.block.BlockBreaking.Host breakHost(Player p) { return breakHost(p, -1, null); }

    private org.domi.furnace.server.block.BlockBreaking.Host breakHost(Player p, int face, org.domi.furnace.server.block.BreakerActor actor) {
        return new org.domi.furnace.server.block.BlockBreaking.Host() {
            public boolean allowStartDestroy(org.domi.furnace.server.block.BlockLevel level, org.domi.furnace.server.block.Pos pos) {
                var ev = world.levelServices().gameEvents();
                if (ev == org.domi.furnace.server.plugin.GameEvents.NONE) return true;
                ItemStack held = actor == null ? ItemStack.EMPTY : actor.itemInHand(org.domi.furnace.server.block.BlockActor.MAIN_HAND);
                return ev.playerLeftClick(p, new int[] {pos.x(), pos.y(), pos.z()}, level.getBlockState(pos), face, held);
            }

            public void sendBlockUpdate(org.domi.furnace.server.block.BlockLevel level, org.domi.furnace.server.block.Pos pos) {
                org.domi.furnace.server.plugin.ClientResync.block(p, level, pos);
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
    final class MapTerrain implements org.domi.furnace.server.map.MapRenderer.Terrain {
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

        @Override public boolean hasCeiling() { return world.dimensionType().hasCeiling(); }

        @Override public int minY() { return world.dimensionType().minY(); }

        @Override public boolean chunkAvailable(int cx, int cz) { return chunk(cx, cz) != null; }

        @Override public int surfaceHeight(int x, int z) {
            ChunkColumn c = chunk(x >> 4, z >> 4);
            return c == null ? world.dimensionType().minY() : c.surfaceBlockY(x & 15, z & 15) + 1;
        }

        @Override public int blockState(int x, int y, int z) {
            ChunkColumn c = chunk(x >> 4, z >> 4);
            return c == null || y < c.minY() || y > c.maxY() ? 0 : c.getBlock(x & 15, y, z & 15);
        }

        @Override public org.domi.furnace.server.map.MapData.Banner bannerAt(int x, int y, int z) {
            ChunkColumn c = chunk(x >> 4, z >> 4);
            if (c == null || y < c.minY() || y > c.maxY()) return null;
            return c.blockEntityAt(x, y, z) instanceof org.domi.furnace.server.block.entity.BannerBlockEntity be
                    ? new org.domi.furnace.server.map.MapData.Banner(x, y, z, be.baseColor(), be.customName) : null;
        }
    }

    /** 매 틱 생존 상태 — 주변 블록을 보고 공허·익사·허기·회복·사망 틱을 돌린다. */
    /** Entity.getBlockPosBelowThatAffectsMyMovement(getOnPos(0.500001F)) 의 블록이 #soul_speed_blocks 인가. */
    private boolean onSoulSpeedBlock(Player p) {
        PlayerProbe probe = world.probe();
        if (probe == null) return false;
        int[] o = probe.onPos(blocks, 0.500001F, p.x(), p.y(), p.z(), p.onGround());
        int st = blocks.stateAt(o[0], o[1], o[2]);
        return st >= 0 && org.domi.furnace.server.block.B.isTag(st, "minecraft:soul_speed_blocks");
    }

    /**
     * Player.doWaterSplashEffect(관전자는 안 함) → Entity.doWaterSplashEffect: 첨벙 소리(본인 제외)와 splash 게임 이벤트.
     * 배를 타고 있으면 바닐라는 물에 닿지 않은 것으로 본다. firstTick 대신 물 상태를 한 번이라도 잰 뒤부터 본다(접속 직후 물속이면 첨벙 없음).
     */
    private void playerWaterSplash(Player p) {
        if (p.isSpectator() || p.vehicle() instanceof org.domi.furnace.server.entity.Boat) return;
        double[] m = p.knownMovement();
        float f = Math.min(1.0F, (float) Math.sqrt(m[0] * m[0] * 0.2F + m[1] * m[1] + m[2] * m[2] * 0.2F) * 0.2F);
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        level.playSound(new org.domi.furnace.server.block.PlayerActor(p), p.x(), p.y(), p.z(),
                f < 0.25F ? "minecraft:entity.player.splash" : "minecraft:entity.player.splash.high_speed", "players", f,
                1.0F + (r.nextFloat() - r.nextFloat()) * 0.4F);
        level.gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.SPLASH,
                new org.domi.furnace.server.entity.Vec3(p.x(), p.y(), p.z()), org.domi.furnace.server.world.gameevent.GameEventContext.of(p));
    }

    private void tickVitals(Player p) {
        PlayerProbe probe = world.probe();
        PlayerVitals.Surroundings s;
        double x = p.x(), y = p.y(), z = p.z();
        if (probe == null || ownedChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4) == null) {
            s = PlayerVitals.Surroundings.NONE;
        } else {
            boolean sneaking = p.sneaking();
            double height = p.bbHeight();
            double eye = p.eyeY() - p.y();
            s = new PlayerVitals.Surroundings(true, probe.inWater(blocks, x, y, z, height),
                    probe.eyeInWater(blocks, x, y, z, eye), probe.eyeInBubbleColumn(blocks, x, y, z, eye),
                    probe.climbable(blocks, x, y, z), probe.stuckInBlock(blocks, x, y, z, height), world.dimensionType().minY());
        }
        if (s.inWater() && !p.wasTouchingWater && p.waterStateKnown && s.chunkLoaded() && level != null) playerWaterSplash(p);
        p.waterStateKnown = s.chunkLoaded();
        p.wasTouchingWater = s.inWater();
        p.onClimbable = s.climbable() != null;
        if (level != null && s.chunkLoaded()) level.raids().tickOmens(p);   // 나쁜 징조·습격 징조(효과 틱 직전)
        if (s.chunkLoaded()) org.domi.furnace.server.entity.PlayerFire.tick(p, level, s.inWater());   // Entity.baseTick 의 불: 물에서 꺼짐·20틱마다 1 피해·용암
        p.vitals().tick(s);
        p.wardenSpawnTracker().tick();   // ServerPlayer.tick
        if (p.stats() != null) {   // 바닐라 Player.tick 의 시간 통계(time_since_rest 는 깨어 있을 때만)
            p.awardStat("minecraft:play_time", 1);
            p.awardStat("minecraft:total_world_time", 1);
            if (!p.vitals().isDeadOrDying()) p.awardStat("minecraft:time_since_death", 1);
            if (p.sneaking()) p.awardStat("minecraft:sneak_time", 1);
            if (!p.isSleeping()) p.awardStat("minecraft:time_since_rest", 1);
        }
        var adv = p.advancements();
        if (adv != null) {
            var v = p.vitals();
            var veh = p.vehicle();
            adv.tickPlayer(new org.domi.furnace.server.advancement.PlayerAdvancements.TickState(new double[] {p.x(), p.y(), p.z()}, v.tickCount(),
                    v.hasEffect(org.domi.furnace.server.effect.MobEffect.LEVITATION), v.fallDistance(), v.health() > 0.0F,
                    veh == null ? 0 : veh.isInLava() ? 1 : -1, v.currentImpulsePos(), v.currentImpulseCause()));
        }
    }

    /**
     * Entity.checkInsideBlocks 의 플레이어 쪽(바닐라 handleMovePlayer 의 player.move 뒤): 몸통(1e-7 안쪽)이 걸친 칸의 켜진 모닥불은
     * 불 피해, 큰 흘림잎은 기울기, 달콤한 열매 덤불은 이번 틱 가로 변위(dx, dz = 위치 - xOld·zOld)로 피해. 나머지 블록의
     * entityInside 는 플레이어에게 아직 연결되지 않았다(끼임·속도는 클라이언트가 계산한다).
     * 같은 칸 순회에서 ServerPlayer.onInsideBlock(state) → enter_block 트리거(공기 포함, 칸마다 entityInside 다음).
     */
    private void checkInsideBlocksPlayer(Player p, double tickDx, double tickDz, boolean travelOnly) {
        if (level == null || p.vitals().health() <= 0.0F || p.gameMode() == GameMode.SPECTATOR) return;
        double h = p.bbHeight();
        int x0 = (int) Math.floor(p.x() - 0.3 + 1.0E-7), x1 = (int) Math.floor(p.x() + 0.3 - 1.0E-7);
        int y0 = (int) Math.floor(p.y() + 1.0E-7), y1 = (int) Math.floor(p.y() + h - 1.0E-7);
        int z0 = (int) Math.floor(p.z() - 0.3 + 1.0E-7), z1 = (int) Math.floor(p.z() + 0.3 - 1.0E-7);
        if (!level.hasChunksAt(x0, z0, x1, z1)) return;
        var adv = p.advancements();
        boolean enterBlock = adv != null && adv.hasListeners("minecraft:enter_block");
        boolean slide = adv != null && adv.hasListeners("minecraft:slide_down_block");
        for (int bx = x0; bx <= x1; bx++) for (int by = y0; by <= y1; by++) for (int bz = z0; bz <= z1; bz++) {
            int s = level.getBlockState(new org.domi.furnace.server.block.Pos(bx, by, bz));
            var beh = org.domi.furnace.server.block.B.behavior(s);
            if (travelOnly) {   // 이동 패킷 없는 틱의 서버 쪽 move: 불 블록만 본다(모닥불·열매 덤불·흘림잎·진행 과제는 패킷 이동에서만 — 가정)
                if (beh instanceof org.domi.furnace.server.block.types.BaseFireBlock fb) org.domi.furnace.server.entity.PlayerFire.fireInside(p, fb.fireDamage());
                continue;
            }
            if (beh instanceof org.domi.furnace.server.block.types.CampfireBlock cf) {
                cf.entityInsidePlayer(s, p.vitals());
            } else if (beh instanceof org.domi.furnace.server.block.types.BaseFireBlock fb) {
                org.domi.furnace.server.entity.PlayerFire.fireInside(p, fb.fireDamage());
            } else if (beh instanceof org.domi.furnace.server.block.types.BigDripleafBlock bd) {
                bd.entityInsideAt(s, level, new org.domi.furnace.server.block.Pos(bx, by, bz), p.onGround(), p.y());
            } else if (beh instanceof org.domi.furnace.server.block.types.SweetBerryBushBlock
                    && org.domi.furnace.server.block.types.SweetBerryBushBlock.hurtsPlayer(s, tickDx, tickDz)) {
                p.vitals().hurt(org.domi.furnace.server.entity.damage.DamageType.byKey("sweet_berry_bush"), 1.0F);
            }
            if (enterBlock) adv.trigger("minecraft:enter_block", org.domi.furnace.server.loot.LootBlockState.of(world.blockRegistry(), s));
            // HoneyBlock.entityInside → maybeDoSlideAchievement: 미끄러지는 중이면 20틱마다 slide_down_block (플레이어 이동은 서버가 아는 변위로)
            if (slide && level.gameTime() % 20L == 0L && org.domi.furnace.server.block.B.behavior(s) instanceof org.domi.furnace.server.block.types.HoneyBlock
                    && org.domi.furnace.server.block.types.HoneyBlock.isSlidingDown(new org.domi.furnace.server.block.Pos(bx, by, bz), p.onGround(),
                            p.x(), p.y(), p.z(), p.serverMovement()[1], 0.6)) {
                adv.trigger("minecraft:slide_down_block", org.domi.furnace.server.loot.LootBlockState.of(world.blockRegistry(), s));
            }
        }
    }

    // ------------------------------------------------------------------ 포털·차원 이동(플레이어)

    /**
     * Entity.checkInsideBlocks 중 포털 칸: 몸통(1e-7 안쪽)이 걸친 지옥문·엔드 포털. 엔드에서 크레딧을 본 적 없이 엔드 포털에 들면
     * 크레딧을 띄우고 이 region 에서 뺀다(참 — 호출자가 목록에서 지운다).
     */
    /** canUsePortal(false) + 관전자 제외: 관전자는 noPhysics 라 Entity.move 가 checkInsideBlocks 를 건너뛰어 포털 칸에 들 수 없다. */
    public static boolean canTriggerPortals(Player p) {
        return p.vehicle() == null && !p.vitals().isDeadOrDying() && p.gameMode() != GameMode.SPECTATOR;
    }

    private boolean checkInsidePortals(Player p) {
        if (!canTriggerPortals(p)) return false;
        double h = p.bbHeight();
        var box = new org.domi.furnace.server.entity.AABB(p.x() - 0.3, p.y(), p.z() - 0.3, p.x() + 0.3, p.y() + h, p.z() + 0.3);
        int x0 = (int) Math.floor(box.minX() + 1.0E-7), x1 = (int) Math.floor(box.maxX() - 1.0E-7);
        int y0 = (int) Math.floor(box.minY() + 1.0E-7), y1 = (int) Math.floor(box.maxY() - 1.0E-7);
        int z0 = (int) Math.floor(box.minZ() + 1.0E-7), z1 = (int) Math.floor(box.maxZ() - 1.0E-7);
        if (!level.hasChunksAt(x0, z0, x1, z1)) return false;
        for (int bx = x0; bx <= x1; bx++) for (int by = y0; by <= y1; by++) for (int bz = z0; bz <= z1; bz++) {
            var pos = new org.domi.furnace.server.block.Pos(bx, by, bz);
            int s = level.getBlockState(pos);
            var b = org.domi.furnace.server.block.B.behavior(s);
            if (b instanceof org.domi.furnace.server.block.types.NetherPortalBlock np) {
                p.setAsInsidePortal(np, pos);
            } else if (b instanceof org.domi.furnace.server.block.types.EndPortalBlock ep
                    && org.domi.furnace.server.block.types.EndPortalBlock.insideShape(box, pos)) {
                if (world.dimensionType() == DimensionType.END && !p.seenCredits) {
                    showEndCredits(p);
                    return true;
                }
                p.setAsInsidePortal(ep, pos);
            } else if (b instanceof org.domi.furnace.server.block.types.EndGatewayBlock gw) {
                gw.entityInsidePlayer(level, pos, p);
            }
        }
        return false;
    }

    /**
     * ServerPlayer.showEndCredits: 레벨에서 빼고(CHANGED_DIMENSION) 크레딧(win_game)을 띄운다. 클라이언트가 client_command 0 을
     * 보내면 연결이 오버월드로 리스폰시킨다(PlayerList.respawn(keepAll = true)).
     */
    private void showEndCredits(Player p) {
        detachForDimensionChange(p);
        if (!p.wonGame) {
            p.wonGame = true;
            p.send(new org.domi.furnace.server.net.protocol.play.GameEvent(org.domi.furnace.server.net.protocol.play.GameEvent.WIN_GAME, 0.0F));
            p.seenCredits = true;
        }
    }

    /** Entity.handlePortal(플레이어): 시간이 차면 원천 쪽 계획을 만들어 목적 차원으로 보낸다. 보냈으면 참(이 region 에서 빠졌다). */
    private boolean handlePlayerPortal(Player p) {
        if (p.portalCooldown > 0) p.portalCooldown--;
        var pp = p.portalProcess();
        if (pp == null) return false;
        boolean canUse = p.vehicle() == null && !p.vitals().isDeadOrDying();
        int timeBefore = pp.getPortalTime();
        if (pp.processPlayerPortalTeleportation(level, p.vitals().invulnerableAbility(), canUse)) {
            p.portalCooldown = Player.DIMENSION_CHANGING_DELAY;
            var events = world.levelServices().gameEvents();
            int cause = pp.portal() instanceof org.domi.furnace.server.block.types.EndPortalBlock ? org.domi.furnace.server.plugin.GameEvents.PORTAL_END
                    : pp.portal().withinLevel() ? org.domi.furnace.server.plugin.GameEvents.PORTAL_GATEWAY : org.domi.furnace.server.plugin.GameEvents.PORTAL_NETHER;
            if (pp.portal().withinLevel()) {
                if (!events.playerPortal(p, world.dimension(), cause)) return false;   // 플러그인 PlayerPortalEvent 취소
                sendWithinLevel(p, pp);
                return true;
            }
            var travel = world.travelService();
            var plan = pp.prepareTravel(level, org.domi.furnace.server.entity.Traveler.of(p));
            if (plan == null || plan.targetDimension().equals(world.dimension()) || !travel.isLevelEnabled(plan.targetDimension())) return false;
            if (!events.playerPortal(p, plan.targetDimension(), cause)) return false;   // 플러그인 PlayerPortalEvent 취소
            if (world.dimensionType() == DimensionType.END && pp.portal() instanceof org.domi.furnace.server.block.types.EndPortalBlock) {
                sendAcrossToRespawn(p);   // findRespawnPositionAndUseSpawnBlock(false, DO_NOTHING)
                return true;
            }
            sendAcross(p, plan);
            return true;
        } else if (pp.getPortalTime() == timeBefore + 1 && timeBefore % PREWARM_INTERVAL_TICKS == 0) {
            prewarmPortal(p, pp);   // 기다림이 막 시작됐다(그 뒤로는 유예가 지나기 전마다) — 목적 청크를 미리 올린다
        } else if (pp.hasExpired()) {
            p.portalProcess(null);
        }
        return false;
    }

    /** 지옥문 대기 중 목적 청크 미리 올리기 간격(청크 퇴출 유예 100틱보다 짧게 — 오래 기다려도 내려가지 않게). */
    private static final int PREWARM_INTERVAL_TICKS = 60;

    /**
     * 플레이어가 차원을 옮기는 포털에서 기다리는 동안(바닐라 playersNetherPortalDefaultDelay 기본 80틱) 지금 자리로 만든 계획의 목적 청크를 올려
     * 둔다 — 넘어갈 때 목적 청크가 이미 있어 바로 도착한다(바닐라는 넘어가는 틱에 서버를 멈추고 청크를 만든다). 같은 레벨 포털·엔드 출구는 하지 않는다.
     */
    private void prewarmPortal(Player p, org.domi.furnace.server.entity.PortalProcessor pp) {
        if (pp.portal().withinLevel() || pp.portal() instanceof org.domi.furnace.server.block.types.EndPortalBlock) return;
        if (pp.getPortalTime() > pp.portal().getPlayerTransitionTime(level, p.vitals().invulnerableAbility())) return;   // 바로 넘어간다
        var travel = world.travelService();
        try {
            var plan = pp.prepareTravel(level, org.domi.furnace.server.entity.Traveler.of(p));
            if (plan != null && !plan.targetDimension().equals(world.dimension()) && travel.isLevelEnabled(plan.targetDimension())) travel.prewarm(plan);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(Region.class).debug("[PORTAL] 미리 올리기 계획 실패", e);
        }
    }

    /**
     * 같은 레벨 안으로 보내는 포털(엔드 관문): 바닐라 ServerPlayer.changeDimension 의 같은 차원 쪽(connection.teleport(위치, 목적 회전)
     * → resetPosition → 도착 칸 포털 티켓). 출구 청크를 비동기로 올리는 동안은 이 region 에서 떼어 두고(다시 들어가지 못하게),
     * 정해지면 떠난 자리 region 에 되돌린 뒤 보정 텔레포트한다. 목적지가 없으면 제자리로만 되돌린다.
     */
    void sendWithinLevel(Player p, org.domi.furnace.server.entity.PortalProcessor pp) {
        var entry = pp.getEntryPosition();
        var traveler = org.domi.furnace.server.entity.Traveler.of(p);
        var src = level;
        detachForDimensionChange(p);
        World w = world;
        pp.portal().travelWithinLevel(src, entry, traveler, (target, t) -> w.runAt(p.x(), p.z(), r -> {
            r.adopt(p);
            if (t != null) {
                p.teleportTo(t.x(), t.y(), t.z(), t.yRot(), t.xRot());
                if (t.ticketPos() != null) w.placePortalTicket(t.ticketPos().x(), t.ticketPos().z());
            }
        }));
    }

    /**
     * 엔드 귀환 포털: 리스폰 위치(ServerPlayer.findRespawnPositionAndUseSpawnBlock(false) — 침대·충전 소모하는 정박기·강제 지점, 없으면
     * 오버월드 월드 스폰)로 보낸다. 목적지를 못 정하면 이 월드로 되돌린다. 목적지가 이 차원이면 떠난 자리로 되돌려 텔레포트.
     */
    void sendAcrossToRespawn(Player p) {
        final int cooldown = p.portalCooldown;
        detachForDimensionChange(p);
        World src = world;
        RespawnFinder.travel(src, p, false, (target, t) -> {
            if (target instanceof RegionLevel rl && t != null && rl.world() == src) {
                src.runAt(p.x(), p.z(), r -> r.adopt(p));
                p.teleportTo(t.x(), t.y(), t.z(), t.yRot(), t.xRot());
            } else if (target == null || t == null || !(target instanceof RegionLevel rl) || !p.changeDimension(rl.world(), t, cooldown)) {
                src.runAt(p.x(), p.z(), r -> r.adopt(p));   // 갈 곳이 없다 — 원래 자리로
            }
        });
    }

    /**
     * 명령 텔레포트로 다른 차원에(바닐라 ServerPlayer.teleportTo(level, …) 의 changeDimension(DimensionTransition(level, pos, ZERO, yaw,
     * pitch, DO_NOTHING))): 이 region 에 있으면 떼어 목적 차원으로 보내고 참, 없으면(다른 region 으로 넘어갔다) 거짓.
     * done 은 목적지가 정해진 뒤(되돌림 포함) 한 번 불린다.
     */
    public boolean teleportAcross(Player p, String dimension, double x, double y, double z, float yaw, float pitch, Runnable done) {
        world.assertOwns(this);
        if (players.remove(p.entityId()) == null) return false;
        var plan = new org.domi.furnace.server.entity.PortalTravel(dimension, org.domi.furnace.server.entity.Entity.floor(x), org.domi.furnace.server.entity.Entity.floor(z), 0, 0,
                lvl -> new org.domi.furnace.server.entity.DimensionTransition(lvl, x, y, z, 0, 0, 0, yaw, pitch));
        final int cooldown = p.portalCooldown;
        detachForDimensionChange(p);
        World src = world;
        src.travelService().travel(plan, (target, t) -> {
            try {
                if (target == null || t == null || !(target instanceof RegionLevel rl) || !p.changeDimension(rl.world(), t, cooldown)) {
                    src.runAt(p.x(), p.z(), r -> r.adopt(p));   // 갈 곳이 없다 — 원래 자리로
                }
            } finally {
                done.run();
            }
        });
        return true;
    }

    // ------------------------------------------------------------------ 관전 시점(바닐라 ServerPlayer.setCamera / tick 의 카메라 동기)

    /**
     * 바닐라 ServerPlayer.setCamera(target): target 이 null 이면 자기 자신. 카메라가 바뀌면 관전자 자신의 회전으로 대상의 자리(x·y·z)에
     * 보정 텔레포트(타고 있으면 내리고 자고 있으면 깨운다)한 뒤 set_camera 패킷을 보낸다. 패킷 순서는 위치 → 카메라. 바뀌지 않았으면 패킷 없음.
     * 대상이 다른 차원이면 바닐라(teleportTo 6인자의 첫 줄 setCamera(this))처럼 먼저 자기 자리로 보정 텔레포트 → set_camera(자기 id)
     * → 차원 이동 → set_camera(자기 id) 이고 카메라는 자기 자신으로 끝난다. 어느 쪽이든 done 을 한 번 부른다.
     * p 는 이 region 의 플레이어여야 한다(region 스레드).
     */
    public void setCamera(Player p, Rider target, String dimension, double x, double y, double z, Runnable done) {
        world.assertOwns(this);
        Rider old = p.cameraOrSelf();
        Rider now = target == null ? p : target;
        float yaw = p.riderYRot(), pitch = p.riderXRot();   // 바닐라: 대상이 아니라 관전자 자신의 회전
        if (old == now) {
            p.cameraField(target);
            done.run();
            return;
        }
        boolean across = target != null && !dimension.equals(p.dimension()) && world.otherWorld(dimension) != null;
        if (across) {
            // 안쪽 setCamera(this): 자기 자리로 텔레포트 + 자기 id, 그다음 차원 이동, 바깥 쪽이 자기 id 를 또 보낸다
            p.cameraField(null);
            p.teleportTo(p.x(), p.y(), p.z(), yaw, pitch);
            p.send(new org.domi.furnace.server.net.protocol.play.SetCamera(p.id()));
            world.otherWorld(dimension).addTicket((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, 1, 5);
            if (p.vehicle() != null) org.domi.furnace.server.entity.Entity.stopRidingForced(p);
            if (p.isSleeping() && level != null) org.domi.furnace.server.entity.PlayerSleeping.stopSleepInBed(p, level, true, true);
            Runnable finish = () -> {
                p.send(new org.domi.furnace.server.net.protocol.play.SetCamera(p.id()));
                done.run();
            };
            if (!teleportAcross(p, dimension, x, y, z, yaw, pitch, finish)) finish.run();
            return;
        }
        p.cameraField(target);
        world.addTicket((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, 1, 5);   // POST_TELEPORT 티켓
        if (p.vehicle() != null) org.domi.furnace.server.entity.Entity.stopRidingForced(p);
        if (p.isSleeping() && level != null) org.domi.furnace.server.entity.PlayerSleeping.stopSleepInBed(p, level, true, true);
        p.teleportTo(x, y, z, yaw, pitch);
        p.send(new org.domi.furnace.server.net.protocol.play.SetCamera(now.id()));
        done.run();
    }

    /** 같은 차원의 대상(이 region 이 가진)을 카메라로: 대상의 지금 자리로. target 이 null 이면 자기 자리에서 자기 시점으로. */
    public void setCamera(Player p, Rider target) {
        Rider at = target == null ? p : target;
        setCamera(p, target, p.dimension(), at.x(), at.y(), at.z(), () -> { });
    }

    /** 이 region 이 r 을 갖고 있는가(소유 스레드에서만 부른다). */
    private boolean ownsRider(Rider r) {
        if (r instanceof Player pl) return players.get(pl.entityId()) == pl;
        return r instanceof org.domi.furnace.server.entity.Entity e && level != null && level.entities().contains(e);
    }

    /** 소유 스레드에서 r 의 자리·회전·생존을 한 번에 뜬 표본(위치 찢어짐 없음). */
    private Player.CameraView viewOf(Rider r) {
        boolean alive = r instanceof Player pl ? pl.isAlive() && !pl.isDisconnected()
                : r instanceof org.domi.furnace.server.entity.Entity e && e.isAlive();
        return new Player.CameraView(r, r.x(), r.y(), r.z(), r.riderYRot(), r.riderXRot(), alive);
    }

    /** 대상이 이 만큼 틱 동안 어느 region 에서도 확인되지 않으면(차원을 옮긴 비플레이어 엔티티 등 — 옛 객체는 사라진다) 되돌아온다. */
    private static final int CAMERA_MISS_LIMIT = 40;

    /**
     * ServerPlayer.tick 의 카메라 동기: 카메라가 자기 자신이 아니면 대상이 살아 있는 동안 대상의 자리·회전으로 서버 쪽 위치를 맞추고(패킷 없이)
     * 청크·가시성 추적을 갱신한다. 웅크리면·대상이 죽거나 사라지면·관전 모드가 아니면 자기 시점으로 돌아간다.
     * 대상이 이 region 에 있으면 직접 읽고, 아니면 대상 주인 region 이 뜬 표본(비동기 갱신)을 쓴다 — 다른 스레드 상태를 직접 읽지 않는다.
     */
    private void tickCamera(Player p) {
        Rider cam = p.cameraTarget();
        if (cam == null) return;
        Player.CameraView view;
        if (ownsRider(cam)) {
            view = viewOf(cam);
            p.cameraView(view);
            p.cameraMisses.set(0);
        } else {
            view = p.cameraView();
            if (view != null && view.target() != cam) view = null;
            if (p.cameraRefreshing.compareAndSet(false, true)) {
                // 라우팅에 쓰는 대상 좌표 읽기만 비소유(EntityOps.runOnOwner 와 같은 가정)
                World w = world.otherWorld(cam.dimensionKey());
                (w != null ? w : world).runAt(cam.x(), cam.z(), region -> {
                    try {
                        if (region.ownsRider(cam)) {
                            p.cameraView(region.viewOf(cam));
                            p.cameraMisses.set(0);
                        } else {
                            p.cameraMisses.incrementAndGet();
                        }
                    } finally {
                        p.cameraRefreshing.set(false);
                    }
                });
            }
        }
        boolean lost = p.cameraMisses.get() > CAMERA_MISS_LIMIT || (view != null && !view.alive());
        if (p.gameMode() == GameMode.SPECTATOR && !lost) {
            if (view != null) {
                p.applyMove(view.x(), view.y(), view.z(), view.yaw(), view.pitch(), p.onGround());
                if (level != null) level.playerMoved(p);
            }
            if (!p.isShiftKeyDown()) return;
        }
        p.cameraView(null);
        setCamera(p, null);
    }

    /** 이 region 에서 떼어 목적 차원으로 보낸다. 목적지를 못 정하면 이 월드로 되돌린다. */
    void sendAcross(Player p, org.domi.furnace.server.entity.PortalTravel plan) {
        final int cooldown = p.portalCooldown;
        detachForDimensionChange(p);
        World src = world;
        src.travelService().travel(plan, (target, t) -> {
            if (target == null || t == null || !(target instanceof RegionLevel rl) || !p.changeDimension(rl.world(), t, cooldown)) {
                src.runAt(p.x(), p.z(), r -> r.adopt(p));   // 갈 곳이 없다 — 원래 자리로
            }
        });
    }

    /**
     * 차원을 옮기려고 떼어 낸다(바닐라 removePlayerImmediately(CHANGED_DIMENSION)): 이 월드의 청크 구독·추적을 지우고 주변 관찰자에게서
     * 엔티티를 지운다. 탭 목록은 그대로(접속자 집합은 서버 전체). 호출자가 players 에서 뺀다.
     */
    void detachForDimensionChange(Player p) {
        world.assertOwns(this);
        for (ChunkPos cp : p.loadedChunks()) world.unsubscribeChunk(cp, p);
        p.loadedChunks().clear();
        p.pendingChunks().clear();
        p.lastCenter(null);
        p.queuedCenter(null);
        p.trackedViewDistance(-1);
        for (Integer eid : p.tracked().keySet()) world.removeTracker(eid, p);
        p.tracked().clear();
        p.missingTicks().clear();
        for (Player other : players.values()) {
            if (stopTracking(other, p.entityId())) other.send(new RemoveEntities(java.util.List.of(p.entityId())));
        }
        if (level != null) level.playerChangedDimension(p);
    }

    /** 이 region 에 있으면 떼어 낸다(다른 차원으로 리스폰할 때 — 연결 스레드가 모든 region 에 묻는다). */
    public void removeForDimensionChange(Player p) {
        if (players.remove(p.entityId()) != null) detachForDimensionChange(p);
    }

    /** 차원 이동 도착(목적 region 스레드): 다음 틱에 체력·경험치를 다시 보내고 편입, 도착 뒤 처리(포털 티켓), 발전 과제(from 이 null 이면 없음 — 죽어서 리스폰). */
    public void adoptAfterDimensionChange(Player p, org.domi.furnace.server.entity.DimensionTransition t, String fromDimension) {
        adoptAfterDimensionChange(p, t, fromDimension, fromDimension);
    }

    /** changedFrom: 플러그인 PlayerChangedWorldEvent 의 떠나온 차원(다른 차원 리스폰은 발전 과제 없이 이것만, null 이면 안 부른다). */
    public void adoptAfterDimensionChange(Player p, org.domi.furnace.server.entity.DimensionTransition t, String fromDimension, String changedFrom) {
        p.vitals().resetSentInfo();
        adopt(p);
        // EntityCallbacks.onTrackingStart/End: 들어온 차원과 떠난 차원의 잠 목록
        world.updateSleepingPlayerList();
        var dims = world.dimensions();
        if (dims != null) {
            for (World other : dims.all()) if (other != world) other.updateSleepingPlayerList();
        }
        if (level != null) level.afterDimensionTransition(t);
        var adv = p.advancements();
        if (adv != null && fromDimension != null) {
            adv.trigger("minecraft:changed_dimension", new String[] {fromDimension, world.dimension()});
            // 바닐라 triggerDimensionChangeTriggers: 네더 → 오버월드면 nether_travel(들어갈 때 오버월드 위치 → 지금), 네더가 아니면 기록을 지운다
            var entered = p.enteredNetherPosition;
            if ("minecraft:the_nether".equals(fromDimension) && "minecraft:overworld".equals(world.dimension()) && entered != null) {
                adv.triggerMoved("minecraft:nether_travel", entered, new double[] {p.x(), p.y(), p.z()}, 0, null);
            }
            if (!"minecraft:the_nether".equals(world.dimension())) p.enteredNetherPosition = null;
        }
        if (changedFrom != null && !changedFrom.equals(world.dimension())) world.levelServices().gameEvents().playerChangedWorld(p, changedFrom);
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
        a.tabEpoch = -1;   // 다음 동기화에서 다시 훑는다(아직 접속 중이면 다시 올린다)
        a.missingTicks().remove(eid);
        if (uuid != null) a.send(new PlayerInfoRemove(java.util.List.of(uuid)));
    }

    /**
     * 중심 이동 또는 시야 거리 변경 시 시야 청크를 pending 큐에 적재(+구독), 시야 밖은 언로드. 전송은 sendQueuedChunks가 틱당 스로틀.
     * 바닐라 ChunkMap.updateChunkTracking: 중심과 거리가 둘 다 그대로일 때만 건너뛴다 — 거리만 바뀌어도 그 틱에 차이를 반영한다.
     * SetCenterChunk 는 중심이 바뀔 때만 보낸다(applyChunkTrackingView).
     */
    private void updateChunks(Player p) {
        ChunkPos center = p.chunkPos();
        int vd = Math.min(world.viewDistance(), p.viewDistance()); // 서버 상한 + 클라 요청
        boolean vdChanged = vd != p.trackedViewDistance();
        if (!vdChanged && center.equals(p.lastCenter()) && center.equals(p.queuedCenter())) return;
        List<ChunkPos> toForget = new ArrayList<>();
        boolean moved = !center.equals(p.lastCenter());
        if (moved || vdChanged) {
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
            if (moved) p.send(new SetCenterChunk(center.x(), center.z()));
            for (ChunkPos cp : toForget) p.send(new UnloadChunk(cp.x(), cp.z()));
            p.lastCenter(center);
        }
        if (!vdChanged && center.equals(p.queuedCenter())) return;
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
        p.trackedViewDistance(vd);
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
        if (world.autoChunkRate()) { sendQueuedChunksAuto(p); return; }
        int adaptive = p.chunkSendRate().nextBudget();   // 바닐라 PlayerChunkSender: 클라이언트가 알린 속도·미확인 배치 한도
        if (adaptive <= 0) return;
        int budget = Math.min(Math.min(world.chunksPerTick(), adaptive), world.maxChunkSendsInFlight() - p.chunkSendsInFlight().get());
        if (budget <= 0) return;
        List<ChunkPos> batch = new ArrayList<>(Math.min(budget, p.pendingChunks().size()));
        var it = p.pendingChunks().iterator();
        while (it.hasNext() && batch.size() < budget) {
            batch.add(it.next());
            it.remove();
        }
        sendBatch(p, batch, false);
    }

    /** auto 전송의 틱당 상한 — 바닐라 PlayerChunkSender 의 클라이언트 속도 상한(64)과 같다. 첫 응답 전에도 이만큼까지. */
    static final int AUTO_MAX_CHUNKS_PER_TICK = (int) ChunkSendRate.MAX_CHUNKS_PER_TICK;
    /** auto 전송이 한 틱에 대기열에서 훑는 최대 수 — 시야 32(4225청크)에서도 틱 비용을 묶는다. 가까운 것부터 훑는다. */
    static final int AUTO_SCAN_LIMIT = 1024;

    /**
     * chunks-per-tick=auto: 바닐라 PlayerChunkSender 처럼 이미 로드된(준비된) 청크만, 클라이언트가 알린 속도(최대 64/틱)만큼 배치로 보낸다.
     * 아직 없는 청크는 가까운 것부터 생성·디스크 로드를 미리 걸어 두고(동시 진행 상한 world.maxChunkSendsInFlight), 다 되면 다음 틱에 보낸다.
     * 예전 방식(숫자)은 준비 안 된 청크도 배치에 넣어 배치 끝(Finished)이 가장 느린 생성을 기다렸고, 동시 진행 상한이 이미 있는 청크까지 막았다.
     */
    private void sendQueuedChunksAuto(Player p) {
        // 바닐라처럼 배치는 겹치지 않는다: 앞 배치의 Finished 가 나가기 전(본문 직렬화가 다음 틱까지 걸린 경우)엔 새 배치를 열지 않는다.
        // 겹치면 클라이언트가 재는 배치 시간(ChunkBatchSizeCalculator)이 어긋난다. 미리 불러오기는 계속한다.
        boolean batchOpen = p.chunkBatchOpen();
        int adaptive = p.chunkSendRate().nextBudget();   // 미확인 배치가 한도면 0 — 그래도 생성 요청은 계속 건다
        int budget = batchOpen ? 0 : Math.min(Math.max(adaptive, 0), AUTO_MAX_CHUNKS_PER_TICK);
        int genSlots = world.maxChunkSendsInFlight() - p.chunkPrefetches().size();
        List<ChunkPos> batch = new ArrayList<>(Math.min(budget, p.pendingChunks().size()));
        int scanned = 0;
        var it = p.pendingChunks().iterator();
        while (it.hasNext() && (batch.size() < budget || genSlots > 0) && scanned++ < AUTO_SCAN_LIMIT) {
            ChunkPos cp = it.next();
            if (world.isChunkLoaded(cp.x(), cp.z())) {
                if (batch.size() < budget) {
                    batch.add(cp);
                    it.remove();
                }
            } else if (genSlots > 0 && p.chunkPrefetches().add(cp)) {
                genSlots--;
                // 구독은 updateChunks 가 해 두었으므로 로드된 뒤 유휴 정리로 내려가지 않는다(evictIdleChunks 는 구독자가 있으면 건너뛴다).
                // 이 region 의 청크면 작업 큐(다음 틱)를 거치지 않고 바로 건다 — 디스크에 있는 청크는 그만큼 일찍 온다.
                CompletableFuture<ChunkColumn> load;
                if (mine(Regionizer.chunkKey(cp.x(), cp.z()))) {
                    try {
                        load = loadChunk(cp);
                    } catch (RuntimeException e) {
                        load = CompletableFuture.failedFuture(e);
                    }
                } else {
                    load = world.submitAtChunk(cp, r -> r.loadChunk(cp)).thenCompose(f -> f);
                }
                load.whenComplete((col, ex) -> {
                    p.chunkPrefetches().remove(cp);
                    if (ex != null) org.slf4j.LoggerFactory.getLogger(Region.class).warn("[CHUNK] 미리 불러오기 실패 {}", cp, ex);
                });
            }
        }
        if (!batch.isEmpty()) {
            p.chunkBatchOpen(true);
            sendBatch(p, batch, true).whenComplete((v, ex) -> p.chunkBatchOpen(false));
        }
    }

    /** 배치 하나: Start → 청크들(본문이 준비되는 대로) → Finished(실제로 보낸 수). */
    private CompletableFuture<Void> sendBatch(Player p, List<ChunkPos> batch, boolean inline) {
        p.chunkSendRate().batchSent(batch.size());
        p.send(new ChunkBatchStart());
        // 실제 전송 수(stale 드롭으로 batch.size와 다를 수 있음) — ChunkBatchFinished에 정확히 보고.
        java.util.concurrent.atomic.AtomicInteger sent = new java.util.concurrent.atomic.AtomicInteger();
        List<CompletableFuture<?>> sends = new ArrayList<>(batch.size());
        for (ChunkPos cp : batch) {
            // 구독은 updateChunks에서 큐에 넣을 때 했다. 이 구독이 청크 전달 전까지 블록 변경을 보류한다.
            World.Subscription sub = world.subscriptionOf(cp, p);
            if (sub == null) continue;   // 그 사이 언로드됨 — 보낼 것 없음
            p.chunkSendsInFlight().incrementAndGet();
            CompletableFuture<?> f = world.serializedChunkBody(cp, inline)
                    .whenComplete((b, ex) -> p.chunkSendsInFlight().decrementAndGet())
                    .thenAccept(body -> {
                        // gen 지연 동안 플레이어가 시야 밖으로 이동했거나(재구독 포함) 언로드됐으면 보내봐야 클라가
                        // "not in view range"로 버림 → 드롭. 위치는 volatile이라 완료 스레드서 안전하게 읽음.
                        if (inView(p, cp) && world.isSubscribed(cp, p, sub)) {
                            p.send(LevelChunkWithLight.ofShared(body));
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
        return CompletableFuture.allOf(sends.toArray(new CompletableFuture[0]))
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
        // 접속자 집합이 그대로면 훑을 것이 없다(예전엔 매 틱 관찰자 × 접속자 O(P²) 조회)
        long epoch = Player.rosterEpoch();
        if (a.tabEpoch == epoch) return;
        List<UUID> gone = new ArrayList<>();
        var it = a.listed().entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Player b = world.onlinePlayer(e.getKey());
            if (b == null || b.isDisconnected() || b != a && !org.domi.furnace.server.entity.view.EntityViewRules.canSee(a, b.uuid())) {   // 퇴장 또는 플러그인이 숨김(Bukkit hidePlayer 처럼 탭에서도 뺀다)
                if (stopTracking(a, e.getKey())) a.send(new RemoveEntities(java.util.List.of(e.getKey())));
                a.missingTicks().remove(e.getKey());
                gone.add(e.getValue());
                it.remove();
            }
        }
        if (!gone.isEmpty()) a.send(new PlayerInfoRemove(gone));
        List<PlayerInfoUpdate.Entry> add = null;
        for (Player b : world.onlinePlayers()) {
            if (b.isDisconnected() || b != a && !org.domi.furnace.server.entity.view.EntityViewRules.canSee(a, b.uuid())) continue;
            if (a.listed().putIfAbsent(b.entityId(), b.uuid()) == null) {
                if (add == null) add = new ArrayList<>();
                var session = b.chat().session();
                add.add(new PlayerInfoUpdate.Entry(b.uuid(), b.name(), b.profileProperties(), session == null ? null : session.asData(),
                        b.gameMode().id(), b.latencyMs()));
            }
        }
        if (add != null) a.send(new PlayerInfoUpdate(add));
        a.tabEpoch = epoch;
    }

    /** 바닐라처럼 600틱마다 접속자 전원의 핑을 모든 플레이어에게 갱신해 보낸다. */
    private void broadcastLatencies() {
        List<PlayerInfoLatency.Entry> all = new ArrayList<>();
        for (Player b : world.onlinePlayers()) {
            if (!b.isDisconnected()) all.add(new PlayerInfoLatency.Entry(b.uuid(), b.latencyMs()));
        }
        if (all.isEmpty()) return;
        PlayerInfoLatency pkt = new PlayerInfoLatency(all);
        if (!org.domi.furnace.server.entity.view.EntityViewRules.active()) {
            for (Player p : players.values()) p.send(pkt);
            return;
        }
        for (Player p : players.values()) {   // 플러그인이 숨긴 플레이어는 그 사람의 탭에 없다 — 모르는 항목을 보내지 않는다
            List<PlayerInfoLatency.Entry> mine = null;
            for (int i = 0; i < all.size(); i++) {
                PlayerInfoLatency.Entry en = all.get(i);
                boolean see = en.uuid().equals(p.uuid()) || org.domi.furnace.server.entity.view.EntityViewRules.canSee(p, en.uuid());
                if (!see && mine == null) mine = new ArrayList<>(all.subList(0, i));
                else if (see && mine != null) mine.add(en);
            }
            if (mine == null) p.send(pkt);
            else if (!mine.isEmpty()) p.send(new PlayerInfoLatency(mine));
        }
    }

    private void updateVisibility(Player a, List<EntityView> candidates) {
        syncTabList(a);
        double range = world.effectiveEntityChunks() * 16.0;
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (EntityView b : candidates) {
            if (b.entityId() == a.entityId()) continue;
            seen.add(b.entityId());
            if (world.onlinePlayer(b.entityId()) == null) continue;   // 이미 퇴장 처리된 플레이어는 (재)스폰하지 않는다
            boolean near = !b.removed() && within(a, b, range) && org.domi.furnace.server.entity.view.EntityViewRules.canSee(a, b.uuid());   // 사망 후 제거된 엔티티·플러그인이 숨긴 플레이어는 보이지 않는다
            boolean tracking = a.tracked().containsKey(b.entityId());
            double[] tr = tracking ? a.tracked().get(b.entityId()) : null;
            if (near && tracking && ((int) tr[5] != b.generation() || (long) tr[7] != org.domi.furnace.server.entity.view.EntityViewRules.epochFor(a, b.uuid()))) {
                // 리스폰(바닐라: 옛 ServerPlayer 제거 + 새 ServerPlayer 추가) 또는 플러그인 변장이 바뀜 — 지웠다 다시 스폰한다.
                a.send(new RemoveEntities(java.util.List.of(b.entityId())));
                stopTracking(a, b.entityId());
                tracking = false;
            }
            if (near && !tracking) {
                if (!a.listed().containsKey(b.entityId())) continue;   // syncTabList가 올리지 않은(퇴장 경합) 대상은 스폰 안 함
                a.send(new SpawnEntity(b.entityId(), b.uuid(), b.x(), b.y(), b.z(), b.yaw(), b.pitch()));
                a.send(new SetHeadRotation(b.entityId(), b.yaw()));
                Player bp = world.onlinePlayer(b.entityId());   // ServerEntity.sendPairingData: 탄 플레이어면 탈것의 승객 목록
                var bd = bp == null ? null : bp.pairingData();   // 웅크림·자세·사용 중(활·방패·먹기) 손 비트
                if (bd != null) a.send(bd);
                var ba = bp == null ? null : bp.pairingAttributes();   // 속성(update_attributes) — 엔티티 데이터 다음, 장비 앞
                if (ba != null) a.send(ba);
                var be = bp == null ? null : bp.pairingEquipment();   // 든 아이템·방어구(set_equipment)
                if (be != null) a.send(be);
                var bv = bp == null ? null : bp.vehicle();
                if (bv != null) a.send(org.domi.furnace.server.net.protocol.play.SetPassengers.of(bv));
                startTracking(a, b.entityId(), new double[]{b.x(), b.y(), b.z(), b.yaw(), b.pitch(), b.generation(), headRotp(b.yaw()),
                        org.domi.furnace.server.entity.view.EntityViewRules.epochFor(a, b.uuid())});
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
        // 바닐라 ServerEntity.sendChanges: 머리 각도(256분할 내림 값)가 바뀌었을 때만 RotateHead 를 보낸다(이동만 했으면 보내지 않음)
        int head = headRotp(b.yaw());
        if (head != (int) last[6]) {
            a.send(new SetHeadRotation(b.entityId(), b.yaw()));
            last[6] = head;
        }
        last[0] = b.x(); last[1] = b.y(); last[2] = b.z(); last[3] = b.yaw(); last[4] = b.pitch();
    }

    /** 바닐라 yHeadRotp: Mth.floor(yHeadRot * 256 / 360). 플레이어는 머리 yaw = 몸통 yaw 로 보낸다. */
    static int headRotp(float yaw) {
        return (int) Math.floor(yaw * 256.0f / 360.0f);
    }
}
