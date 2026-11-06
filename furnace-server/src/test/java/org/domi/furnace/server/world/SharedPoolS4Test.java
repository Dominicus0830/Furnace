// S4: 여러 차원이 작업 스레드 풀 하나를 공유할 때 — 세 차원 동시 틱, 바쁜 차원이 다른 차원을 굶기지 않음(perDimCap·starved),
// 한 차원 분리 뒤 다른 차원 계속·풀 종료, 세 차원 sprint lockstep.
// 설계: docs/feature-parity/shared-scheduler-design-2026-10-28.md §4·§7·§9 S4
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.domi.furnace.api.concurrent.Owned;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SharedPoolS4Test {

    private final List<AutoCloseable> closers = new ArrayList<>();
    private final List<ExecutorService> pools = new ArrayList<>();
    private final AtomicBoolean stop = new AtomicBoolean();

    @AfterEach
    void tearDown() throws Exception {
        stop.set(true);
        for (int i = closers.size() - 1; i >= 0; i--) closers.get(i).close();
        for (ExecutorService e : pools) e.shutdownNow();
    }

    private SharedScheduler sharedPool(int threads) {
        SharedScheduler p = SharedScheduler.startShared(threads, "furnace-region-s4-");
        closers.add(p::close);
        return p;
    }

    private World world(DimensionType type, long tickNanos, SharedScheduler pool) {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(tickNanos, 1);
        closers.add(sched::close);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ExecutorService io = Executors.newSingleThreadExecutor(), gen = Executors.newSingleThreadExecutor();
        pools.add(io);
        pools.add(gen);
        World w = new World(type, new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, pool, 4, 1, 2, 2,
                reg.directBits(), 7, io, gen);
        w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
        return w;
    }

    private List<World> threeDims(long tickNanos, SharedScheduler pool) {
        return List.of(world(DimensionType.OVERWORLD, tickNanos, pool), world(DimensionType.NETHER, tickNanos, pool),
                world(DimensionType.END, tickNanos, pool));
    }

    /** region(청크 cp 를 가진) 에서 매 틱 body 를 부른다. stop 이 서거나 n 번 돌면 끝. */
    private CompletableFuture<Void> everyTick(World w, ChunkPos cp, int n, Consumer<Region> body) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        long key = Regionizer.chunkKey(cp.x(), cp.z());
        AtomicInteger c = new AtomicInteger();
        w.runAtChunk(cp, r -> {
            r.chunkAtBlocking(cp);
            r.schedule(key, new Consumer<Region>() {
                @Override public void accept(Region r2) {
                    body.accept(r2);
                    if (c.incrementAndGet() >= n || stop.get()) f.complete(null);
                    else r2.schedule(key, this, 1);
                }
            }, 1);
        });
        return f;
    }

    private static ThreadOwner owner(Region r) {
        return new ThreadOwner() {
            @Override public boolean isOwnerThread() { return r.isOwnerThread(); }
            @Override public String ownerName() { return "region#" + r.id(); }
        };
    }

    /** 세 차원 × region 2, 워커 4 하나: 모두 돌고, 한 region 은 겹쳐 돌지 않으며, 틱 안에서만 주인. 틱 수는 고르게. */
    @Test
    void threeDimensionsTickTogetherOnOnePool() throws Exception {
        SharedScheduler pool = sharedPool(4);
        List<World> ws = threeDims(5_000_000L, pool);
        assertEquals(3, pool.dimensionCount());
        Set<String> names = ConcurrentHashMap.newKeySet();
        AtomicInteger guardFail = new AtomicInteger(), overlap = new AtomicInteger();
        Set<Region> inFlight = ConcurrentHashMap.newKeySet();
        List<CompletableFuture<Void>> fs = new ArrayList<>();
        for (World w : ws) {
            for (int k = 0; k < 2; k++) {
                fs.add(everyTick(w, new ChunkPos(k * 4096, 0), 200, r -> {
                    if (!inFlight.add(r)) overlap.incrementAndGet();
                    names.add(Thread.currentThread().getName());
                    try {
                        new Owned<>("x", owner(r)).get();
                        if (!Regionizer.isRegionWorkerThread()) guardFail.incrementAndGet();
                    } catch (AssertionError e) {
                        guardFail.incrementAndGet();
                    }
                    inFlight.remove(r);
                }));
            }
        }
        CompletableFuture.allOf(fs.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);
        assertEquals(0, overlap.get(), "한 region 이 두 워커에서 겹쳐 돌았다");
        assertEquals(0, guardFail.get(), "틱 안에서는 주인이어야 한다");
        assertTrue(names.stream().allMatch(n -> n.startsWith("furnace-region-s4-")), "워커 이름: " + names);
        assertTrue(names.size() <= 4, "공유 풀 밖의 스레드가 돌았다: " + names);
        for (World w : ws) assertEquals(4, w.regionizer().threadCount());
        // 같은 시점의 region 별 틱 수(차원 사이 공정성): 서로 크게 벌어지지 않는다
        List<Long> ticks = new ArrayList<>();
        for (World w : ws) for (var s : w.regionizer().schedStats().regions()) if (s.ticks() > 50) ticks.add(s.ticks());
        assertTrue(ticks.size() >= 6, "region 수: " + ticks);
        long min = ticks.stream().mapToLong(Long::longValue).min().orElseThrow(), max = ticks.stream().mapToLong(Long::longValue).max().orElseThrow();
        assertTrue(max - min <= 20, "차원 사이 틱 수 차: " + ticks);
    }

    /**
     * R3: 차원 A 의 region 8개가 틱마다 100ms 바쁘게 돌고(수요 16 워커 > 용량 4 — A 머리는 늘 밀려 있다) 차원 B region 1개는 가볍다.
     * 순수 EDF 면 B 는 밀린 A 머리 뒤에서 굶는다. 상한(perDimCap = 3)·굶은 차원 우선 덕에 B 간격이 유계로 남는다.
     */
    @Test
    void busyDimensionDoesNotStarveAnother() throws Exception {
        SharedScheduler pool = sharedPool(4);
        if (System.getProperty("furnace.sched.dimCap") == null) assertEquals(3, pool.perDimCap());
        World a = world(DimensionType.OVERWORLD, 50_000_000L, pool), b = world(DimensionType.NETHER, 50_000_000L, pool);
        AtomicInteger aMax = new AtomicInteger(), aNow = new AtomicInteger();
        for (int k = 0; k < 8; k++) {
            everyTick(a, new ChunkPos(k * 4096, 0), Integer.MAX_VALUE, r -> {
                aMax.accumulateAndGet(aNow.incrementAndGet(), Math::max);
                long end = System.nanoTime() + 100_000_000L;
                while (System.nanoTime() - end < 0) Thread.onSpinWait();
                aNow.decrementAndGet();
            });
        }
        Thread.sleep(300);   // A 가 워커를 차지하게
        ConcurrentLinkedQueue<Long> starts = new ConcurrentLinkedQueue<>();
        everyTick(b, new ChunkPos(0, 0), 60, r -> starts.add(System.nanoTime())).get(30, TimeUnit.SECONDS);
        stop.set(true);
        Long[] st = starts.toArray(Long[]::new);
        long[] gaps = new long[st.length - 1];
        for (int i = 1; i < st.length; i++) gaps[i - 1] = st[i] - st[i - 1];
        Arrays.sort(gaps);
        long p95 = gaps[(int) Math.ceil(gaps.length * 0.95) - 1], max = gaps[gaps.length - 1];
        assertTrue(p95 <= 150_000_000L, "B 틱 간격 p95 ≤ 3·tick: " + p95 / 1_000_000 + "ms " + Arrays.toString(gaps));
        assertTrue(max < 300_000_000L, "B 틱 간격 최대(굶음 없음): " + max / 1_000_000 + "ms");
        assertTrue(aMax.get() >= 3, "A 도 상한까지는 병렬로 돈다: " + aMax.get());
    }

    /** 한 차원을 떼어도(Regionizer.close — 공유 경로는 풀을 멈추지 않는다) 다른 차원은 계속 돌고 flush 도 끝난다. 풀 종료는 1초 안. */
    @Test
    void closingOneDimensionKeepsOthersRunning() throws Exception {
        SharedScheduler pool = new SharedScheduler(4, "furnace-region-s4c-");
        pool.start();
        List<World> ws = threeDims(10_000_000L, pool);
        for (World w : ws) w.submitAtChunk(new ChunkPos(0, 0), r -> r.chunkAtBlocking(new ChunkPos(0, 0))).get(10, TimeUnit.SECONDS);
        ws.get(0).regionizer().close();
        assertEquals(2, pool.dimensionCount());
        everyTick(ws.get(1), new ChunkPos(0, 0), 20, r -> {}).get(10, TimeUnit.SECONDS);
        everyTick(ws.get(2), new ChunkPos(0, 0), 20, r -> {}).get(10, TimeUnit.SECONDS);
        for (var f : ws.get(2).flushAll()) f.get(10, TimeUnit.SECONDS);
        long t0 = System.nanoTime();
        pool.close();
        assertTrue(System.nanoTime() - t0 < 1_000_000_000L, "풀 종료가 1초를 넘었다");
        Thread.sleep(50);
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            assertFalse(t.getName().startsWith("furnace-region-s4c-") && t.isAlive(), "워커가 남았다: " + t.getName());
        }
    }

    /** 세 차원 × region 2 를 한 풀에서 /tick sprint 500 — lockstep 이 끝나고 참여 region 은 모두 500 틱. */
    @Test
    void sprintLockstepCompletesAcrossThreeDimensions() throws Exception {
        SharedScheduler pool = sharedPool(4);
        List<World> ws = threeDims(50_000_000L, pool);
        TickState ts = ws.get(0).tickState();
        for (World w : ws) {
            w.tickState(ts);
            for (int k = 0; k < 2; k++) {
                ChunkPos cp = new ChunkPos(k * 4096, 0);
                w.submitAtChunk(cp, r -> r.chunkAtBlocking(cp)).get(10, TimeUnit.SECONDS);
            }
            w.regionizer().gameTicks(true);
        }
        Thread.sleep(150);
        assertFalse(ts.startSprint(500, System.nanoTime()));
        int gen = ts.sprintGeneration();
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (true) {   // ServerCore.tickSprint 의 lockstep(전역 틱 본문 없이)
            assertTrue(System.nanoTime() < deadline, "sprint 가 60초 안에 끝나지 않았다");
            long min = Long.MAX_VALUE;
            for (World w : ws) min = Math.min(min, w.regionizer().sprintMinDone(gen));
            if (ts.sprintGlobalLeft() > 0) {
                if (ts.sprintGlobalDone() > min) {
                    java.util.concurrent.locks.LockSupport.parkNanos(50_000L);
                    continue;
                }
                ts.takeSprintGlobalTick();
                continue;
            }
            boolean done = true;
            for (World w : ws) done &= w.regionizer().sprintDone(gen);
            if (done) break;
            java.util.concurrent.locks.LockSupport.parkNanos(50_000L);
        }
        for (World w : ws) {
            for (Region r : w.regionizer().regionsSnapshot()) {
                if (r.sprintJoinedSummary) assertEquals(500, r.sprintDoneSummary, "region#" + r.id());
            }
            assertEquals(500, w.regionizer().sprintMinDone(gen));
        }
        var res = ts.endSprint(System.nanoTime());
        assertNotNull(res);
        assertEquals(500, res.ticks());
    }
}
