// 작업 스레드 풀(SharedScheduler)을 Regionizer 에서 떼어 낸 뒤의 동작: 한 region 은 동시에 한 워커에서만, 틱 안에서만 주인,
// 자는 워커 깨우기(다른 Regionizer 머리도 놓치지 않음), 유휴 때 바통이 돌지 않음, 한 풀에 Regionizer 둘을 건 /tick sprint lockstep.
// 설계: docs/feature-parity/shared-scheduler-design-2026-10-28.md §3.3, §9 S2 시험 (a)~(e)
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.domi.furnace.api.concurrent.Owned;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SharedSchedulerTest {

    private final List<AutoCloseable> closers = new ArrayList<>();
    private final List<ExecutorService> pools = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (int i = closers.size() - 1; i >= 0; i--) closers.get(i).close();
        for (ExecutorService e : pools) e.shutdownNow();
    }

    private World world(long tickNanos, int threads, SharedScheduler pool) {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(tickNanos, 1);
        closers.add(sched::close);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ExecutorService io = Executors.newSingleThreadExecutor(), gen = Executors.newSingleThreadExecutor();
        pools.add(io);
        pools.add(gen);
        World w = pool == null
                ? new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, threads, 4, 1, 2, 2, reg.directBits(), 7, io, gen)
                : new World(DimensionType.OVERWORLD, new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, pool, 4, 1, 2, 2,
                        reg.directBits(), 7, io, gen);
        w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
        return w;
    }

    private SharedScheduler sharedPool(int threads) {
        SharedScheduler p = new SharedScheduler(threads, "furnace-region-test-");
        p.start();
        closers.add(p::close);
        return p;
    }

    /** region 하나에 매 틱 다시 거는 작업을 둔다. 돌 때마다 body 를 부르고 n 번 뒤 완료. */
    private CompletableFuture<Void> everyTick(World w, ChunkPos cp, int n, java.util.function.Consumer<Region> body) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        long key = Regionizer.chunkKey(cp.x(), cp.z());
        AtomicInteger c = new AtomicInteger();
        w.runAtChunk(cp, r -> {
            r.chunkAtBlocking(cp);
            r.schedule(key, new java.util.function.Consumer<Region>() {
                @Override public void accept(Region r2) {
                    body.accept(r2);
                    if (c.incrementAndGet() >= n) f.complete(null);
                    else r2.schedule(key, this, 1);
                }
            }, 1);
        });
        return f;
    }

    /** (a)+(b) 워커 4개, region 1개, 1000틱: 동시에 한 워커만, 틱 안에서는 주인(Owned 통과), 밖에서는 아님. */
    @Test
    void oneRegionRunsOnOneWorkerAtATime_andOwnsOnlyWhileTicking() throws Exception {
        World w = world(1_000_000L, 4, null);
        AtomicInteger inFlight = new AtomicInteger(), maxInFlight = new AtomicInteger(), guardFail = new AtomicInteger();
        Set<String> threads = ConcurrentHashMap.newKeySet();
        AtomicReference<Region> seen = new AtomicReference<>();
        everyTick(w, new ChunkPos(0, 0), 1000, r -> {
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            threads.add(Thread.currentThread().getName());
            seen.set(r);
            Owned<String> o = new Owned<>("x", owner(r));
            try {
                if (!r.isOwnerThread() || !Regionizer.isRegionWorkerThread()) guardFail.incrementAndGet();
                o.get();
            } catch (AssertionError e) {
                guardFail.incrementAndGet();
            }
            inFlight.decrementAndGet();
        }).get(60, TimeUnit.SECONDS);
        assertEquals(1, maxInFlight.get(), "한 region 이 두 워커에서 겹쳐 돌았다");
        assertEquals(0, guardFail.get(), "틱 안에서는 주인이어야 한다");
        assertTrue(threads.stream().allMatch(n -> n.startsWith("furnace-region-")), "워커 이름: " + threads);
        Region r = seen.get();
        assertFalse(r.isOwnerThread(), "테스트 스레드는 주인이 아니다");
        assertFalse(Regionizer.isRegionWorkerThread());
        assertThrows(AssertionError.class, () -> new Owned<>("x", owner(r)).get(), "밖에서는 Owned 가드가 막는다");
        assertEquals(4, w.regionizer().threadCount());
    }

    private static ThreadOwner owner(Region r) {
        return new ThreadOwner() {
            @Override public boolean isOwnerThread() { return r.isOwnerThread(); }
            @Override public String ownerName() { return "region#" + r.id(); }
        };
    }

    /**
     * (c) 깨우기: 한 풀(워커 4)에 Regionizer 둘. 워커가 다 자는 동안(틱 200ms — 머리가 멀다) 두 차원에 1ms 간격으로 일을 넣으면
     * 둘 다 곧바로(5ms 상한, 공유 머신 여유로 중앙값을 본다) 돈다. N2: 늦게 등록한 머리도 놓치지 않는다.
     * 새 region 은 마감 = 만든 시각으로 큐에 들어가므로 첫 틱의 큐 지연(계측)이 곧 깨우기 지연이다. (작업 완료 시각으로 재면
     * "region 이 먼저 돌고 작업은 그 뒤에 들어가 다음 틱(200ms)에 돈다"는 기존 경합이 섞인다.)
     */
    @Test
    void sleepingWorkersWakeForEitherRegionizerHead() throws Exception {
        SharedScheduler pool = sharedPool(4);
        World a = world(200_000_000L, 4, pool), b = world(200_000_000L, 4, pool);
        assertEquals(4, a.regionizer().threadCount());
        assertSame(a.regionizer().pool(), b.regionizer().pool());
        long[] lat = new long[15];
        for (int i = 0; i < lat.length; i++) {
            Thread.sleep(30);   // 워커가 잠들게
            Region ra = a.regionizer().ownerForTask(i * 4096, 0);
            Thread.sleep(1);
            Region rb = b.regionizer().ownerForTask(i * 4096, 4096);
            long deadline = System.nanoTime() + 5_000_000_000L;
            long da, db;
            while ((da = firstTickDelay(a, ra)) < 0 | (db = firstTickDelay(b, rb)) < 0) {
                assertTrue(System.nanoTime() < deadline, "새 region 이 돌지 않는다");
                Thread.sleep(1);
            }
            lat[i] = Math.max(da, db);
        }
        java.util.Arrays.sort(lat);
        assertTrue(lat[lat.length / 2] < 5_000_000L, "두 차원 모두 5ms 안에 시작(중앙값): " + java.util.Arrays.toString(lat));
        assertLatency(lat);
    }

    /** 깨우기를 놓치면 PARK_MAX(50ms) 뒤에야 도므로, p90·최댓값이 그보다 확실히 작아야 한다. */
    private static void assertLatency(long[] sorted) {
        java.util.Arrays.sort(sorted);
        long p90 = sorted[(int) Math.ceil(sorted.length * 0.9) - 1], max = sorted[sorted.length - 1];
        assertTrue(p90 < 10_000_000L, "p90 < 10ms: " + java.util.Arrays.toString(sorted));
        assertTrue(max < SharedScheduler.PARK_MAX * 7 / 10, "max < PARK_MAX*0.7(깨우기 유실이면 ~50ms): " + java.util.Arrays.toString(sorted));
    }

    /** 기본 경로(차원 하나·자기 풀 하나, 워커 2): 자는 워커에게 일을 넣은 뒤 첫 틱까지의 지연 30회. */
    @Test
    void defaultPathScheduleToWakeLatency() throws Exception {
        World w = world(200_000_000L, 2, null);
        long[] lat = new long[30];
        for (int i = 0; i < lat.length; i++) {
            Thread.sleep(25);   // 워커가 잠들게
            Region r = w.regionizer().ownerForTask(i * 4096, 0);
            long deadline = System.nanoTime() + 5_000_000_000L;
            long d;
            while ((d = firstTickDelay(w, r)) < 0) {
                assertTrue(System.nanoTime() < deadline, "새 region 이 돌지 않는다");
                Thread.sleep(1);
            }
            lat[i] = d;
        }
        assertLatency(lat);
    }

    private static void awaitParked(SharedScheduler pool, int n) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (pool.parkedCount() < n) {
            assertTrue(System.nanoTime() < deadline, "워커가 잠들지 않았다");
            Thread.sleep(1);
        }
        Thread.sleep(5);
    }

    /** 누가 뽑아 깨운 스레드들(park 복귀 때 이미 자는 목록에서 빠져 있음 — 타임아웃 복귀는 아님). 부하로 타이밍이 흔들려도 결정적이다. */
    private static java.util.Set<Thread> recordEarlyWakers(SharedScheduler pool, java.util.function.Consumer<SharedScheduler.Parked> then) {
        java.util.Set<Thread> early = ConcurrentHashMap.newKeySet();
        pool.afterParkHook = me -> {
            if (!pool.isParkedForTest(me)) early.add(Thread.currentThread());
            if (then != null) then.accept(me);
        };
        return early;
    }

    private static void awaitEarlyWakers(java.util.Set<Thread> early, int n, long withinMs) throws Exception {
        long deadline = System.nanoTime() + withinMs * 1_000_000L;
        while (early.size() < n && System.nanoTime() - deadline < 0) Thread.sleep(1);
    }

    private static void awaitWakeups(SharedScheduler pool, long target, long withinMs) throws Exception {
        long deadline = System.nanoTime() + withinMs * 1_000_000L;
        while (pool.wakeups() < target && System.nanoTime() - deadline < 0) Thread.sleep(1);
    }

    /**
     * N1-② 결정적 재현: 워커 X 가 자기를 등록한 직후 깨우는 쪽이 X 를 뽑고, 머리는 이미 때가 된 상태.
     * X 는 안 자고 나가므로 뽑힌 깨우기를 다른 자는 워커 Y 에게 넘겨야 한다(passBaton). 넘기지 않으면 Y 는 PARK_MAX 뒤에야 깬다.
     * 깨어남 수: X(처음 깨움) 1 + Y(바통) 1 = 2 가 PARK_MAX 한참 전에 되어야 한다.
     */
    @Test
    void pickedBeforeSleepPassesBaton() throws Exception {
        SharedScheduler pool = sharedPool(2);
        World w = world(200_000_000L, 2, pool);
        Regionizer rz = w.regionizer();
        awaitParked(pool, 2);
        java.util.concurrent.atomic.AtomicReference<Thread> x = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.Set<Thread> early = recordEarlyWakers(pool, null);
        pool.afterRegisterHook = me -> {
            if (!x.compareAndSet(null, Thread.currentThread())) return;
            rz.setHeadDueForTest(System.nanoTime() - 1_000_000L);   // 등록 직후 머리가 이미 때가 됨(N1-②)
            pool.pickForTest(me);                                  // 그 사이 깨우는 쪽이 나를 뽑음
        };
        pool.wake(System.nanoTime() + 1_000_000L);   // 워커 하나(X)를 깨워 sleep 을 다시 타게 한다
        try {
            awaitEarlyWakers(early, 2, PARK_MAX_MS / 2);
            assertNotNull(x.get(), "훅이 불리지 않았다");
            assertTrue(early.size() >= 2 && early.stream().anyMatch(t -> t != x.get()),
                    "뽑힌 깨우기를 다른 자는 워커에게 넘기지 않았다(그 워커는 PARK_MAX 까지 잔다)");
        } finally {
            rz.setHeadDueForTest(Long.MAX_VALUE);
            pool.afterRegisterHook = null;
            pool.afterParkHook = null;
        }
    }

    /**
     * N1-③ 결정적 재현: X 가 타임아웃으로 깬 순간 깨우는 쪽이 X 를 뽑음(뽑힘과 타임아웃 겹침) → X 는 바통을 Y 에게 넘겨야 한다.
     * Y 가 타임아웃이 아니라 바통으로 깨어야 한다.
     */
    @Test
    void pickedAtTimeoutPassesBaton() throws Exception {
        SharedScheduler pool = sharedPool(2);
        World w = world(200_000_000L, 2, pool);
        Regionizer rz = w.regionizer();
        awaitParked(pool, 2);
        long t = System.nanoTime() + 8_000_000L;
        java.util.concurrent.atomic.AtomicReference<Thread> x = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.Set<Thread> early = recordEarlyWakers(pool, me -> {
            if (System.nanoTime() - t < 0 || !x.compareAndSet(null, Thread.currentThread())) return;   // 타임아웃으로 깬 첫 번째만
            rz.setHeadDueForTest(Long.MAX_VALUE);
            pool.pickForTest(me);
        });
        rz.setHeadDueForTest(t);
        pool.wake(t);
        try {
            awaitEarlyWakers(early, 2, PARK_MAX_MS / 2);
            assertNotNull(x.get(), "훅이 불리지 않았다");
            assertTrue(early.stream().anyMatch(th -> th != x.get()), "타임아웃과 겹친 뽑힘을 넘기지 않았다(다른 워커가 PARK_MAX 까지 잔다)");
        } finally {
            rz.setHeadDueForTest(Long.MAX_VALUE);
            pool.afterParkHook = null;
        }
    }

    private static final long PARK_MAX_MS = SharedScheduler.PARK_MAX / 1_000_000L;

    /** 그 region 의 첫 틱 큐 지연(ns). 아직 안 돌았으면 -1. */
    private static long firstTickDelay(World w, Region r) {
        for (var s : w.regionizer().schedStats().regions()) {
            if (s.id() == r.id() && s.ticks() >= 1) return s.queueDelayMaxNanos();
        }
        return -1;
    }

    /** (d) 바통 라이브락 없음: 유휴 워커 4개가 2초 동안 깨는 횟수 ≤ 4×20×2×1.5. */
    @Test
    void idleWorkersDoNotPassTheBatonForever() throws Exception {
        World w = world(50_000_000L, 4, null);
        w.submitAtChunk(new ChunkPos(0, 0), r -> null).get(5, TimeUnit.SECONDS);   // region 하나(유휴 틱만)
        Thread.sleep(200);
        long before = w.regionizer().pool().wakeups();
        Thread.sleep(2000);
        long woke = w.regionizer().pool().wakeups() - before;
        assertTrue(woke <= 4 * 20 * 2 * 3 / 2, "유휴 깨어남이 너무 많다(바통 순환?): " + woke);
    }

    /** (e) 한 풀에 Regionizer 둘 + 공용 TickState 로 sprint 200: 전역 lockstep 이 끝나고 참여 region 은 모두 200 틱을 마친다. */
    @Test
    void sprintLockstepCompletesOnOnePoolWithTwoRegionizers() throws Exception {
        SharedScheduler pool = sharedPool(4);
        World a = world(50_000_000L, 4, pool), b = world(50_000_000L, 4, pool);
        TickState ts = a.tickState();
        b.tickState(ts);
        for (World w : List.of(a, b)) {
            for (int k = 0; k < 2; k++) {
                ChunkPos cp = new ChunkPos(k * 4096, 0);
                w.submitAtChunk(cp, r -> r.chunkAtBlocking(cp)).get(10, TimeUnit.SECONDS);
            }
            w.regionizer().gameTicks(true);
        }
        Thread.sleep(150);
        assertFalse(ts.startSprint(200, System.nanoTime()), "앞선 sprint 없음");
        int gen = ts.sprintGeneration();
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (true) {   // ServerCore.tickSprint 의 lockstep(전역 틱 본문 없이)
            assertTrue(System.nanoTime() < deadline, "sprint 가 60초 안에 끝나지 않았다");
            long min = Math.min(a.regionizer().sprintMinDone(gen), b.regionizer().sprintMinDone(gen));
            if (ts.sprintGlobalLeft() > 0) {
                if (ts.sprintGlobalDone() > min) {
                    java.util.concurrent.locks.LockSupport.parkNanos(50_000L);
                    continue;
                }
                ts.takeSprintGlobalTick();
                continue;
            }
            if (a.regionizer().sprintDone(gen) && b.regionizer().sprintDone(gen)) break;
            java.util.concurrent.locks.LockSupport.parkNanos(50_000L);
        }
        for (World w : List.of(a, b)) {
            for (Region r : w.regionizer().regionsSnapshot()) {
                if (r.sprintJoinedSummary) assertEquals(200, r.sprintDoneSummary, "region#" + r.id() + " 마친 sprint 틱");
            }
            assertEquals(200, w.regionizer().sprintMinDone(gen));
        }
        var res = ts.endSprint(System.nanoTime());
        assertNotNull(res);
        assertEquals(200, res.ticks());
    }
}
