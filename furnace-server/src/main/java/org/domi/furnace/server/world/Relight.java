// 청크 빛 다시 계산(T-197, Paper ThreadedLevelLightEngine#starlight$serverRelightChunks 의 서버 몫). 설계 docs/feature-parity/compat-nms-n2b5-relight-2026-11-16.md.
//
// 받기(아무 스레드) → 8×8 청크 타일로 나눠 대기열 → 타일 준비(기준 청크의 주인 region): 풀린 붙잡힌 검사 먼저 계산, 거울 맞추기, 3×3 검사 붙잡기,
// 구역 얼리기 → 일꾼: 타일 + 둘레 1칸을 빈 빛에서 계산(RelightCompute) → 녹이기 → 청크마다 설치(그 청크의 주인 region): 층 끼우기·원천 다시 세기·
// 저장 표시·빛 패킷, 붙잡기 풀기 → 요청마다 한 줄 실행기에서 콜백(설치 순서), 마지막에 done. 붙잡힌 검사는 붙잡기가 0 이 된 뒤 그 region 의
// 다음 빛 계산에서 풀린다. 다른 요청이 붙잡은 3×3 과 겹치는 타일은 그 설치 뒤로 미룬다(같은 자리를 되풀이해 relight 해도 빛 갱신이 굶지 않게).
// 붙잡기를 건 뒤 준비·설치가 실패하면 얼린 것을 녹이고 붙잡기·핀을 풀고 끝으로 센다(검토 F1). region 마다 한 틱에 준비·설치에 쓰는 시간은
// TICK_BUDGET 까지 — 넘으면 다음 틱으로 미룬다(타일 하나의 설치 64청크가 한 틱에 몰려 MSPT 가 튀지 않게, M3).
package org.domi.furnace.server.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import org.domi.furnace.server.i18n.Lang;
import org.domi.furnace.server.light.ChunkLight;
import org.domi.furnace.server.light.LevelLight;
import org.domi.furnace.server.light.LightEngine;
import org.domi.furnace.server.light.RelightCompute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Relight {

    private static final Logger LOG = LoggerFactory.getLogger(Relight.class);

    /** 타일 한 변(청크). 둘레 1칸까지 많아야 10×10 청크를 한 일꾼이 계산한다. */
    static final int TILE = 8;
    /**
     * region 한 틱에 relight 준비·설치에 쓰는 시간(ns). 첫 일은 늘 한다(넘는 만큼은 그 일 하나 — 준비 하나가 약 0.5 ms). 설계 6장 M3 합격선(relight 없는
     * 같은 장면 대비 틱 p95 +1 ms 이내)에 맞춘 값이다: 1 ms 면 예산 + 준비 하나가 얹혀 +2 ms 를 넘었고, 0.25 ms 에서 +0.7~1.0 ms. 대신 1024청크가
     * 1.7 s 에서 2.8 s 로 늘었다(설치 0.012 ms/청크 → 틱마다 약 20청크).
     */
    static final long TICK_BUDGET = 250_000L;
    /** 도는 빛 작업 묶음 때문에 준비를 미루는 최대 틱 — 넘으면 기다린다(빛 풀이 막혀도 relight 가 끝나게). */
    static final int MAX_BATCH_DEFERS = 20;

    private final World world;

    Relight(World world) { this.world = world; }

    // ------------------------------------------------------------------ 일꾼 풀·콜백 실행기

    private static final class Pool {
        static final int THREADS = Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 4);
        static final Executor EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(THREADS, daemon("furnace-relight-"));
        /** 플러그인 콜백(요청마다 한 줄). 콜백이 막혀도 계산 일꾼은 돈다. */
        static final Executor CALLBACKS = java.util.concurrent.Executors.newCachedThreadPool(daemon("furnace-relight-callback-"));
    }

    /** region 스레드: 이번 틱 relight 예산을 다 썼는가. */
    private boolean overBudget(Region r) {
        long t = r.tickCount();
        if (r.relightTick != t) {
            r.relightTick = t;
            r.relightSpent = 0;
        }
        return r.relightSpent >= tickBudget;
    }

    /** region 스레드: 이번 틱 relight 몫으로 쓴 시간을 더한다(overBudget 이 틱을 맞춘 뒤). */
    private static void spend(Region r, long nanos) {
        if (r.relightTick == r.tickCount()) r.relightSpent += nanos;
    }

    private static java.util.concurrent.ThreadFactory daemon(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** 시험이 일꾼을 잡아 둘 자리(null 이면 공용 풀). */
    private volatile Executor executor;
    /** 시험용 변이: false 면 검사를 붙잡지 않는다(붙잡기가 빠지면 시험이 실패하는지 본다). */
    private volatile boolean holdsEnabled = true;

    /** 시험용: 계산을 이 실행기로 보낸다(null 이면 공용 풀). */
    public void useExecutor(Executor e) { this.executor = e; }

    /** 시험용 변이 스위치. */
    void holdsEnabledForTest(boolean on) { this.holdsEnabled = on; }

    /** 틱 예산(ns, 기본 TICK_BUDGET). 시험이 아주 작게 해 거의 모든 일이 다음 틱으로 미뤄지는 길을 돈다. */
    private volatile long tickBudget = TICK_BUDGET;

    void tickBudgetForTest(long nanos) { this.tickBudget = nanos; }

    /** 시험용: 설치 직전에 부른다(설치 실패를 흉내 낼 자리, null 이면 없음). */
    private volatile Runnable installHook;

    void installHookForTest(Runnable hook) { this.installHook = hook; }

    private Executor executor() {
        Executor e = executor;
        return e != null ? e : Pool.EXECUTOR;
    }

    private int maxInFlight() { return executor != null ? Integer.MAX_VALUE : Pool.THREADS * 2; }

    // ------------------------------------------------------------------ 계측(벤치·시험, 시간 재기 두 번씩 — 타일·청크 단위라 싸다)

    private final java.util.concurrent.atomic.LongAdder prepareNanos = new java.util.concurrent.atomic.LongAdder(),
            computeNanos = new java.util.concurrent.atomic.LongAdder(), installNanos = new java.util.concurrent.atomic.LongAdder(),
            computedColumns = new java.util.concurrent.atomic.LongAdder(), installs = new java.util.concurrent.atomic.LongAdder(),
            prepares = new java.util.concurrent.atomic.LongAdder();

    /** 시험·벤치: {준비 ns, 계산 ns, 설치 ns, 계산한 컬럼 수(둘레 포함), 설치 수, 준비한 타일 수}. */
    long[] stats() {
        return new long[] {prepareNanos.sum(), computeNanos.sum(), installNanos.sum(), computedColumns.sum(), installs.sum(), prepares.sum()};
    }

    /** 시험용: 지금 쓰는 계산 일꾼 수. */
    static int poolThreads() { return Pool.THREADS; }

    // ------------------------------------------------------------------ 핀(내리기 막기)

    /** 받은 뒤 끝날 때까지 내리지 않을 청크(키 → 수). World.ticketed 가 본다. */
    private final ConcurrentHashMap<Long, Integer> pins = new ConcurrentHashMap<>();

    boolean pinned(long key) { return !pins.isEmpty() && pins.containsKey(key); }

    private void pin(long key) { pins.merge(key, 1, Integer::sum); }

    private void unpin(long key) { pins.computeIfPresent(key, (k, v) -> v == 1 ? null : v - 1); }

    // ------------------------------------------------------------------ 붙잡기
    // 계산 중인 타일 청크 C 마다 3×3 에 붙잡기 수 +1. 한 청크는 한 요청만 붙잡는다(겹치는 다른 요청의 타일은 미룬다). 바뀔 때마다 박싱 없는
    // 읽기 사본을 새로 내고 세대를 올린다 — 빛 엔진은 검사마다 사본만 보고, 세대가 그대로면 붙잡힌 통을 다시 훑지 않는다.

    private final Object holdLock = new Object();
    /** 키 → {수}. holdLock 안에서만. */
    private final HashMap<Long, int[]> holdCounts = new HashMap<>();
    /** 키 → 붙잡은 요청. holdLock 안에서만. */
    private final HashMap<Long, Request> holdOwner = new HashMap<>();
    private volatile HoldSet holdSet = HoldSet.EMPTY;
    private volatile long holdGeneration;
    private boolean closed;   // holdLock 안에서

    /** 박싱 없는 청크 키 집합(열린 주소법, 읽기 전용). */
    private static final class HoldSet {
        static final long FREE = Long.MIN_VALUE;   // 청크 키 (−2³¹, 0) — 월드 경계 밖이라 쓰이지 않는다
        static final HoldSet EMPTY = new HoldSet(new long[0]);
        final long[] table;
        final int mask, size;

        HoldSet(long[] keys) {
            int cap = Integer.highestOneBit(Math.max(4, keys.length * 4) - 1) << 1;
            table = new long[cap];
            java.util.Arrays.fill(table, FREE);
            mask = cap - 1;
            for (long k : keys) {
                int i = mix(k) & mask;
                while (table[i] != FREE) i = (i + 1) & mask;
                table[i] = k;
            }
            size = keys.length;
        }

        static int mix(long k) {
            long h = k * 0x9E3779B97F4A7C15L;
            return (int) (h ^ h >>> 32);
        }

        boolean contains(long k) {
            if (size == 0) return false;
            int i = mix(k) & mask;
            for (long t; (t = table[i]) != FREE; i = (i + 1) & mask) if (t == k) return true;
            return false;
        }
    }

    /** holdLock 안에서: 사본을 새로 내고 세대를 올린다. */
    private void publishHolds() {
        long[] keys = new long[holdCounts.size()];
        int i = 0;
        for (long k : holdCounts.keySet()) keys[i++] = k;
        holdSet = keys.length == 0 ? HoldSet.EMPTY : new HoldSet(keys);
        holdGeneration++;
    }

    /** region 레벨이 빛 엔진에 다는 붙잡기 판정. */
    final LevelLight.Hold hold = new LevelLight.Hold() {
        @Override public boolean active() { return holdSet.size > 0; }

        @Override public long generation() { return holdGeneration; }

        @Override public boolean held(long p) { return holdSet.contains(Regionizer.chunkKey(LightEngine.px(p) >> 4, LightEngine.pz(p) >> 4)); }
    };

    private static final int ADDED = 0, DEFERRED = 1, CLOSED = 2;

    /**
     * 3×3 붙잡기를 건다(ADDED). 다른 요청이 붙잡은 칸과 겹치면 걸지 않고 그 타일을 미룬 줄에 넣는다(DEFERRED — 같은 잠금 안이라 풀림을 놓치지 않는다).
     * 닫혔으면 CLOSED.
     */
    private int tryAddHolds(Request req, long[] chunks) {
        synchronized (holdLock) {
            if (closed) return CLOSED;
            for (long k : chunks) {
                int cx = Regionizer.chunkX(k), cz = Regionizer.chunkZ(k);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    Request o = holdOwner.get(Regionizer.chunkKey(cx + dx, cz + dz));
                    if (o != null && o != req) {
                        waiting.add(new Tile(req, chunks, false));
                        return DEFERRED;
                    }
                }
            }
            if (!holdsEnabled) return ADDED;
            for (long k : chunks) {
                int cx = Regionizer.chunkX(k), cz = Regionizer.chunkZ(k);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    long nk = Regionizer.chunkKey(cx + dx, cz + dz);
                    holdCounts.computeIfAbsent(nk, x -> new int[1])[0]++;
                    holdOwner.put(nk, req);
                }
            }
            publishHolds();
            return ADDED;
        }
    }

    private void releaseHolds(int cx, int cz) {
        boolean woke = false;
        synchronized (holdLock) {
            if (closed || !holdsEnabled) return;
            boolean freed = false;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                long nk = Regionizer.chunkKey(cx + dx, cz + dz);
                int[] c = holdCounts.get(nk);
                if (c == null) throw new IllegalStateException("relight hold count missing");
                if (--c[0] == 0) {
                    holdCounts.remove(nk);
                    holdOwner.remove(nk);
                    freed = true;
                }
            }
            publishHolds();
            // 풀린 칸(cx±1, cz±1)에 3×3 이 닿고 이제 막는 붙잡기가 없는 미룬 타일만 깨워 대기열 맨 앞에 둔다(미룬 차례 유지 — 새 요청보다 먼저, 검토 F3)
            if (freed && !waiting.isEmpty()) {
                List<Tile> wake = new ArrayList<>();
                for (var it = waiting.iterator(); it.hasNext(); ) {
                    Tile t = it.next();
                    if (t.touches(cx, cz) && !blocked(t)) {
                        it.remove();
                        wake.add(t);
                    }
                }
                for (int i = wake.size() - 1; i >= 0; i--) queue.addFirst(wake.get(i));
                woke = !wake.isEmpty();
            }
        }
        if (woke) pump();
    }

    /** holdLock 안에서: 타일의 3×3 중 다른 요청이 붙잡은 칸이 있는가. */
    private boolean blocked(Tile t) {
        for (long k : t.chunks) {
            int cx = Regionizer.chunkX(k), cz = Regionizer.chunkZ(k);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                Request o = holdOwner.get(Regionizer.chunkKey(cx + dx, cz + dz));
                if (o != null && o != t.req) return true;
            }
        }
        return false;
    }

    /** 시험용: 지금 붙잡기가 걸린 청크 수. */
    public int holdCount() { return holdSet.size; }

    // ------------------------------------------------------------------ 요청·타일

    /** 요청 하나: 받은 수만큼 청크 콜백을 (한 줄로, 설치 순서대로) 부른 뒤 done(받은 수). */
    private static final class Request {
        final LongConsumer chunkDone;
        final IntConsumer allDone;
        final int accepted;
        final AtomicInteger remaining;
        final ConcurrentLinkedQueue<Long> finished = new ConcurrentLinkedQueue<>();
        final AtomicBoolean draining = new AtomicBoolean();

        Request(LongConsumer chunkDone, IntConsumer allDone, int accepted) {
            this.chunkDone = chunkDone;
            this.allDone = allDone;
            this.accepted = accepted;
            this.remaining = new AtomicInteger(accepted);
        }
    }

    /** 대기 중인 타일. preHeld 인 청크는 붙잡기가 이미 걸려 있다(설치 때 다시 준비로 돌린 청크). */
    private static final class Tile {
        final Request req;
        final long[] chunks;
        final boolean preHeld;
        /** 청크 범위(최소·최대 x·z). */
        final int minX, maxX, minZ, maxZ;
        /** 도는 빛 작업 묶음 때문에 미룬 틱 수(주인 region 스레드만). */
        int batchDefers;

        Tile(Request req, long[] chunks, boolean preHeld) {
            this.req = req;
            this.chunks = chunks;
            this.preHeld = preHeld;
            int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
            for (long k : chunks) {
                int x = Regionizer.chunkX(k), z = Regionizer.chunkZ(k);
                x0 = Math.min(x0, x);
                x1 = Math.max(x1, x);
                z0 = Math.min(z0, z);
                z1 = Math.max(z1, z);
            }
            minX = x0;
            maxX = x1;
            minZ = z0;
            maxZ = z1;
        }

        /** 청크 (cx, cz)의 3×3 과 이 타일 청크들의 3×3 이 겹칠 수 있는가(범위로 거름 — 체비셰프 2). */
        boolean touches(int cx, int cz) { return cx >= minX - 2 && cx <= maxX + 2 && cz >= minZ - 2 && cz <= maxZ + 2; }
    }

    private final ConcurrentLinkedDeque<Tile> queue = new ConcurrentLinkedDeque<>();
    /** 다른 요청의 붙잡기와 겹쳐 미룬 타일(미룬 차례) — 막던 붙잡기가 풀릴 때 대기열 앞으로 돌린다. holdLock 안에서만. */
    private final ArrayList<Tile> waiting = new ArrayList<>();
    private final AtomicInteger inFlight = new AtomicInteger();

    /**
     * 아무 스레드: 로드됐고 빛이 켜진 청크만 받아(순서 유지·중복 제거) 다시 계산을 건다(Paper: 티켓 먼저, 그다음 확인). 돌려주는 값 = 받은 수.
     * 청크마다 끝나면 chunkDone(청크 키), 모두 끝나면 allDone(받은 수) — 요청마다 한 콜백 스레드에서 차례로(Paper 는 빛 일꾼 한 스레드).
     */
    public int relight(long[] chunkKeys, LongConsumer chunkDone, IntConsumer allDone) {
        java.util.LinkedHashSet<Long> accepted = new java.util.LinkedHashSet<>();
        boolean open;
        synchronized (holdLock) {
            open = !closed;
        }
        for (long k : chunkKeys) {
            if (!open || accepted.contains(k)) continue;
            pin(k);
            ChunkColumn col = world.isUnloaded() ? null : world.loadedChunk(Regionizer.chunkX(k), Regionizer.chunkZ(k));
            if (col != null && col.light() != null) accepted.add(k);
            else unpin(k);
        }
        Request req = new Request(chunkDone, allDone, accepted.size());
        if (accepted.isEmpty()) {
            Pool.CALLBACKS.execute(() -> callDone(req));
            return 0;
        }
        LinkedHashMap<Long, List<Long>> tiles = new LinkedHashMap<>();
        for (long k : accepted) {
            long t = Regionizer.chunkKey(Math.floorDiv(Regionizer.chunkX(k), TILE), Math.floorDiv(Regionizer.chunkZ(k), TILE));
            tiles.computeIfAbsent(t, x -> new ArrayList<>()).add(k);
        }
        for (List<Long> l : tiles.values()) queue.add(new Tile(req, l.stream().mapToLong(Long::longValue).toArray(), false));
        pump();
        return accepted.size();
    }

    /** 자리가 있으면 대기 타일을 준비하러 보낸다. */
    private void pump() {
        while (inFlight.get() < maxInFlight()) {
            Tile t = queue.poll();
            if (t == null) return;
            inFlight.incrementAndGet();
            if (world.isUnloaded() || !world.regionizer().enqueueIfOwned(t.chunks[0], r -> prepare(r, t))) {
                inFlight.decrementAndGet();
                for (long k : t.chunks) skip(t.req, k, t.preHeld);
            }
        }
    }

    // ------------------------------------------------------------------ 준비(주인 region)

    /** 계산 하나: 타일 청크, 계산 세계, 멤버(키 → 이번 로드의 컬럼), 얼린 구역. */
    private static final class Job {
        final Request req;
        final long[] chunks;
        final RelightCompute compute;
        final Map<Long, ChunkColumn> members = new HashMap<>();
        /** 타일 청크의 얼릴 때 내용 판 번호 — 설치 때 그대로면 계산한 원천 높이를 쓴다. */
        final Map<Long, Long> stamps = new HashMap<>();
        final List<ChunkColumn.LightBlocks> frozen = new ArrayList<>();
        /** 일꾼 또는 준비 실패 정리 중 먼저 집은 쪽이 이 계산의 끝을 맡는다. */
        final AtomicBoolean claimed = new AtomicBoolean();

        Job(Request req, long[] chunks, RelightCompute compute) {
            this.req = req;
            this.chunks = chunks;
            this.compute = compute;
        }
    }

    private void prepare(Region r, Tile t) {
        if (overBudget(r)) {   // 이번 틱 몫을 다 썼다 — 다음 틱에(자리는 그대로 쥔다)
            r.schedule(t.chunks[0], rr -> prepare(rr, t), 1);
            return;
        }
        boolean dispatched = false, deferred = false;
        long t0 = System.nanoTime(), moved = 0;
        try {
            RegionLevel level = r.level();
            List<Long> mine = new ArrayList<>(), elsewhere = new ArrayList<>();
            for (long k : t.chunks) {
                int cx = Regionizer.chunkX(k), cz = Regionizer.chunkZ(k);
                ChunkColumn col = r.ownedChunk(cx, cz);
                if (col != null) {
                    if (col.light() == null) skip(t.req, k, t.preHeld);   // 빛이 꺼졌다(재생성 중) — 곧 지금 블록으로 켜진다
                    else mine.add(k);
                } else if (world.isChunkLoaded(cx, cz)) {
                    elsewhere.add(k);
                } else {
                    skip(t.req, k, t.preHeld);
                }
            }
            if (!elsewhere.isEmpty()) queue.add(new Tile(t.req, elsewhere.stream().mapToLong(Long::longValue).toArray(), t.preHeld));
            if (mine.isEmpty()) return;
            long[] chunks = mine.stream().mapToLong(Long::longValue).toArray();
            boolean held = t.preHeld;
            Job job = null;
            try {
                if (!held) {
                    // 곧 붙잡을 자리에 밀린 검사(앞 relight 가 풀어 둔 것 포함)가 있으면 먼저 돌린다 — 같은 자리를 곧바로 다시 붙잡아 빛 갱신이
                    // 굶지 않게(검토 R4). 자리 밖 일은 틱 끝 그대로(검토 F2)
                    long[] area = area(chunks);
                    long s0 = System.nanoTime();
                    boolean settled = level.settleChecksBeforeHold(p -> java.util.Arrays.binarySearch(area,
                            Regionizer.chunkKey(LightEngine.px(p) >> 4, LightEngine.pz(p) >> 4)) >= 0, t.batchDefers >= MAX_BATCH_DEFERS);
                    moved = System.nanoTime() - s0;   // 틱 끝 빛 일을 당겨 한 몫 — 그 틱 끝이 그만큼 덜 하므로 예산에 넣지 않는다
                    if (!settled) {   // 빛 작업 묶음이 그 자리 검사를 아직 계산 중 — 기다리지 않고 다음 틱에
                        t.batchDefers++;
                        Tile again = new Tile(t.req, chunks, false);
                        again.batchDefers = t.batchDefers;
                        r.schedule(chunks[0], rr -> prepare(rr, again), 1);
                        deferred = true;
                        return;
                    }
                    int added = tryAddHolds(t.req, chunks);
                    if (added == CLOSED) for (long k : chunks) skip(t.req, k, false);
                    if (added != ADDED) return;
                    held = true;
                }
                RelightCompute compute = new RelightCompute(level.minBuildHeight(), level.maxBuildHeight(), level.lightBedrockState(),
                        level.hasSkyLight(), world.virtualLights());
                job = new Job(t.req, chunks, compute);
                // U = 요청 청크마다 3×3 의 합집합 중 이 region 이 가진·빛 있는 청크. 거울(플러그인이 쓴 섹션)을 먼저 들여온다
                List<int[]> pos = new ArrayList<>();
                for (long k : chunks) {
                    int cx = Regionizer.chunkX(k), cz = Regionizer.chunkZ(k);
                    for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                        long nk = Regionizer.chunkKey(cx + dx, cz + dz);
                        if (job.members.containsKey(nk)) continue;
                        ChunkColumn n = r.ownedChunk(cx + dx, cz + dz);
                        if (n == null || n.light() == null) continue;
                        job.members.put(nk, n);
                        pos.add(new int[] {cx + dx, cz + dz});
                    }
                }
                for (ChunkColumn c : job.members.values()) if (c.mirrorPending()) r.syncMirror(c);
                // 얼리기는 붙잡기 뒤 — 얼린 블록(B0) 이후의 빛 바뀜은 모두 붙잡힌다
                for (long k : chunks) job.stamps.put(k, job.members.get(k).modCount());
                for (int[] p : pos) {
                    ChunkColumn.LightBlocks lb = job.members.get(Regionizer.chunkKey(p[0], p[1])).freezeForLight();
                    job.frozen.add(lb);
                    compute.addColumn(p[0], p[1], lb);
                }
                prepareNanos.add(System.nanoTime() - t0);
                prepares.increment();
                computedColumns.add(pos.size());
                Job j = job;
                executor().execute(() -> runJob(j));
                dispatched = true;
            } catch (RuntimeException | Error e) {
                // 붙잡기·핀·얼림을 남기면 그 둘레 빛 검사가 영영 안 돌고 done 도 오지 않는다 — 정리하고 끝으로 센다(검토 F1).
                // 일꾼이 이미 집었으면(같은 스레드에서 돈 실행기) 정리는 일꾼 몫이다
                if (job != null && !job.claimed.compareAndSet(false, true)) {
                    dispatched = true;
                    throw e;
                }
                if (job != null) thaw(job);
                LOG.error(Lang.t("relight.prepare-failed", world.dimension(), chunks.length), e);
                for (long k : chunks) skip(t.req, k, held);
            }
        } finally {
            spend(r, System.nanoTime() - t0 - moved);
            if (!dispatched && !deferred) {
                inFlight.decrementAndGet();
                pump();
            }
        }
    }

    /** 타일 청크들의 3×3 합집합(정렬한 청크 키). */
    private static long[] area(long[] chunks) {
        java.util.HashSet<Long> s = new java.util.HashSet<>();
        for (long k : chunks) {
            int cx = Regionizer.chunkX(k), cz = Regionizer.chunkZ(k);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) s.add(Regionizer.chunkKey(cx + dx, cz + dz));
        }
        long[] a = s.stream().mapToLong(Long::longValue).toArray();
        java.util.Arrays.sort(a);
        return a;
    }

    private static void thaw(Job job) {
        for (ChunkColumn.LightBlocks b : job.frozen) b.thaw();   // 아무 스레드에서 녹여도 된다(원자 수)
        job.frozen.clear();
    }

    // ------------------------------------------------------------------ 계산(일꾼)

    private void runJob(Job job) {
        if (!job.claimed.compareAndSet(false, true)) return;   // 준비 쪽이 실패로 이미 정리했다
        boolean ok = false;
        long t0 = System.nanoTime();
        try {
            job.compute.run();
            ok = true;
            computeNanos.add(System.nanoTime() - t0);
        } catch (RuntimeException | Error e) {
            LOG.error(Lang.t("relight.compute-failed", world.dimension(), job.chunks.length), e);
        } finally {
            thaw(job);   // 일꾼이 다 읽었다
            inFlight.decrementAndGet();
        }
        boolean computed = ok;
        for (long k : job.chunks) {
            boolean handed = false;
            try {
                ChunkLight fresh = computed ? job.compute.result(Regionizer.chunkX(k), Regionizer.chunkZ(k)) : null;
                handed = !world.isUnloaded() && world.regionizer().enqueueIfOwned(k, r -> install(r, job, k, fresh, 0));
            } catch (RuntimeException | Error e) {
                LOG.error(Lang.t("relight.compute-failed", world.dimension(), 1), e);
            } finally {
                if (!handed) skip(job.req, k, true);   // 넘기지 못한 청크도 붙잡기·핀을 풀고 끝으로 센다(검토 F1)
            }
        }
        pump();
    }

    // ------------------------------------------------------------------ 설치(그 청크의 주인 region)

    private void install(Region r, Job job, long k, ChunkLight fresh, int batchDefers) {
        // 이번 틱 몫을 다 썼거나, 빛 작업 묶음이 아직 도는 중이다(끼우기는 묶음 게시를 기다려야 한다) — 기다리지 않고 다음 틱에
        if (overBudget(r) || batchDefers < MAX_BATCH_DEFERS && r.level().lightBatchRunning()) {
            int d = r.level().lightBatchRunning() ? batchDefers + 1 : batchDefers;
            r.schedule(k, rr -> install(rr, job, k, fresh, d), 1);
            return;
        }
        int cx = Regionizer.chunkX(k), cz = Regionizer.chunkZ(k);
        ChunkColumn col = r.ownedChunk(cx, cz);
        boolean isClosed;
        synchronized (holdLock) {
            isClosed = closed;
        }
        if (col == null || col.light() == null || fresh == null || isClosed) {
            skip(job.req, k, true);
            return;
        }
        // 3×3 안에 계산 때와 다른 컬럼이 보인다(새로 로드·빛 켜짐·합치기, 내렸다 다시 올림·재생성 — 검토 R3) — 그 컬럼의 빛 켜기가 민 빛은
        // 붙잡지 못한 전파라 끼우면 덮이고, 계산에 쓴 옛 블록과도 다르다. 다시 계산한다(붙잡기는 그대로 넘긴다)
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            ChunkColumn n = r.ownedChunk(cx + dx, cz + dz);
            if (n != null && n.light() != null && job.members.get(Regionizer.chunkKey(cx + dx, cz + dz)) != n) {
                queue.addFirst(new Tile(job.req, new long[] {k}, true));   // 붙잡기를 쥐고 있다 — 먼저
                pump();
                return;
            }
        }
        r.level().awaitLightIdle();   // 끝난 빛 작업 묶음 게시 — 이 틱의 pollLight 가 할 일을 당긴 것이라 예산에 넣지 않는다
        long t0 = System.nanoTime();   // 예산은 끼우기·패킷만 센다(위 확인은 싸다)
        try {
            if (installHook != null) installHook.run();
            r.level().installRelight(cx, cz, fresh, job.stamps.get(k));
            installNanos.add(System.nanoTime() - t0);
            installs.increment();
        } catch (RuntimeException | Error e) {
            // 반쯤 끼웠을 수 있다 — 저장 빛을 믿지 않게 표시하고(다음에 올릴 때 다시 계산) 붙잡기·핀은 아래에서 푼다(검토 F1)
            world.lightStale().mark(cx, cz);
            LOG.error(Lang.t("relight.install-failed", world.dimension(), cx, cz), e);
        } finally {
            spend(r, System.nanoTime() - t0);
            releaseHolds(cx, cz);
            unpin(k);
            finish(job.req, k);
        }
    }

    /** 계산 없이 끝냄(내려감·빛 없음·실패·종료). held 면 그 청크의 붙잡기를 푼다. */
    private void skip(Request req, long k, boolean held) {
        if (held) releaseHolds(Regionizer.chunkX(k), Regionizer.chunkZ(k));
        unpin(k);
        finish(req, k);
    }

    // ------------------------------------------------------------------ 콜백(요청마다 한 줄)

    private void finish(Request req, long k) {
        boolean isClosed;
        synchronized (holdLock) {
            isClosed = closed;
        }
        if (isClosed) return;   // 종료 — 플러그인은 이미 꺼지는 중이다
        req.finished.add(k);
        if (req.draining.compareAndSet(false, true)) Pool.CALLBACKS.execute(() -> drain(req));
    }

    private void drain(Request req) {
        while (true) {
            Long k;
            while ((k = req.finished.poll()) != null) {
                if (req.chunkDone != null) {
                    try {
                        req.chunkDone.accept(k);
                    } catch (RuntimeException | LinkageError e) {
                        LOG.warn(Lang.t("relight.callback-failed", world.dimension()), e);
                    }
                }
                if (req.remaining.decrementAndGet() == 0) callDone(req);
            }
            req.draining.set(false);
            if (req.finished.isEmpty() || !req.draining.compareAndSet(false, true)) return;
        }
    }

    private void callDone(Request req) {
        if (req.allDone == null) return;
        try {
            req.allDone.accept(req.accepted);
        } catch (RuntimeException | LinkageError e) {
            LOG.warn(Lang.t("relight.callback-failed", world.dimension()), e);
        }
    }

    // ------------------------------------------------------------------ 종료(검토 R1)

    /**
     * 종료·월드 내리기 전(저장 전에): 새 요청을 받지 않고, 모든 붙잡기를 푼다 — 붙잡혔던 검사는 저장 전 빛 계산(saveDirtyChunks 의 runLightUpdates)이
     * 끝까지 계산한다. 아직 끼우지 못한 청크는 "들여온 블록 + 옛 빛"이라 저장 빛을 믿지 않게 표시한다(다음에 올릴 때 다시 계산, T-012). 콜백은 더
     * 부르지 않는다. 아무 스레드, 여러 번 불러도 된다.
     */
    public void shutdown() {
        synchronized (holdLock) {
            if (closed) return;
            closed = true;
            holdCounts.clear();
            holdOwner.clear();
            publishHolds();
            queue.clear();
            waiting.clear();
        }
        var stale = world.lightStale();
        for (long k : pins.keySet()) stale.mark(Regionizer.chunkX(k), Regionizer.chunkZ(k));
    }
}
