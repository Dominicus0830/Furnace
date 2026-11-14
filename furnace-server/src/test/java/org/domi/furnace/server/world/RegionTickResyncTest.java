package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

// T-171: 틱이 주기(50ms)를 넘기면 한 주기를 쉬지 않고 바로 다음 틱을 돈다(따라잡기 몰아치기 없음, Folia 방식).
// 틱 순서·내용은 그대로이고 마감만 바뀐다. 같은 워커 풀의 다른 region 은 마감 순(가장 오래 기다린 것 먼저)으로 공정하게 돈다.
class RegionTickResyncTest {

    private static final long MS = 1_000_000L, TICK = 50 * MS;

    private final List<AutoCloseable> closers = new ArrayList<>();
    private final List<ExecutorService> pools = new ArrayList<>();
    private final AtomicBoolean stop = new AtomicBoolean();

    @AfterEach
    void tearDown() throws Exception {
        stop.set(true);
        for (int i = closers.size() - 1; i >= 0; i--) closers.get(i).close();
        for (ExecutorService e : pools) e.shutdownNow();
    }

    @Test
    void nextDueKeepsTheScheduleWhenOnTimeAndTakesNowWhenLate() {
        long due = 1_000_000_000L;
        // 제때 끝남(틱 20ms, 마감 직후 시작): 마감을 한 주기 앞으로
        assertEquals(due + TICK, Regionizer.nextDueAfterTick(due, TICK, due + 20 * MS));
        // 마감과 같은 순간에 끝나도 한 주기 앞(지난 것이 아님)
        assertEquals(due + TICK, Regionizer.nextDueAfterTick(due, TICK, due + TICK));
        // 53ms 틱: 한 칸 쉬지 않고 바로 다음(now) — 예전에는 now + 50ms
        assertEquals(due + 53 * MS, Regionizer.nextDueAfterTick(due, TICK, due + 53 * MS));
        // 한참 밀렸어도 따라잡기 없이 now 한 번(마감을 여러 주기 뒤로 몰아 앞당기지 않는다)
        assertEquals(due + 500 * MS, Regionizer.nextDueAfterTick(due, TICK, due + 500 * MS));
        // nanoTime 오버플로 경계에서도 차이로 비교
        long nearMax = Long.MAX_VALUE - 10 * MS;
        long wrappedLate = nearMax + 60 * MS;   // 넘쳐 음수가 된 now(마감 + 60ms)
        assertTrue(wrappedLate < 0);
        assertEquals(wrappedLate, Regionizer.nextDueAfterTick(nearMax, TICK, wrappedLate));
        assertEquals(nearMax + TICK, Regionizer.nextDueAfterTick(nearMax, TICK, nearMax + 5 * MS));
        assertEquals(nearMax + TICK, Regionizer.nextDueAfterTick(nearMax, TICK, nearMax + 20 * MS));   // 마감 + 20ms 는 넘쳐도 제때
    }

    private SharedScheduler sharedPool(int threads) {
        SharedScheduler p = SharedScheduler.startShared(threads, "furnace-region-t171-");
        closers.add(p::close);
        return p;
    }

    private World world(SharedScheduler pool) {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(TICK, 1);
        closers.add(sched::close);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ExecutorService io = Executors.newSingleThreadExecutor(), gen = Executors.newSingleThreadExecutor();
        pools.add(io);
        pools.add(gen);
        World w = new World(DimensionType.OVERWORLD, new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, pool, 4, 1, 2, 2,
                reg.directBits(), 7, io, gen);
        w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
        return w;
    }

    private void everyTick(World w, ChunkPos cp, Consumer<Region> body) {
        long key = Regionizer.chunkKey(cp.x(), cp.z());
        w.runAtChunk(cp, r -> {
            r.chunkAtBlocking(cp);
            r.schedule(key, new Consumer<Region>() {
                @Override public void accept(Region r2) {
                    body.accept(r2);
                    if (!stop.get()) r2.schedule(key, this, 1);
                }
            }, 1);
        });
    }

    private static void spin(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() - end < 0) Thread.onSpinWait();
    }

    /** 장면 결과: 가벼운 region 의 TPS·틱 간격, 무거운 region 들의 TPS. */
    private record Scene(double lightTps, long p50, long p99, long max, double[] heavyTps, String msg) {
        double hMin() { return Arrays.stream(heavyTps).min().orElse(0); }
        double hMax() { return Arrays.stream(heavyTps).max().orElse(0); }
    }

    /** 워커 4개 한 풀·한 차원에 무거운 region heavyN 개(틱 55ms — 주기를 조금 넘긴다)와 가벼운 region 1개(2ms)를 10초 돌린다. */
    private Scene run(int heavyN) throws Exception {
        Assumptions.assumeTrue(Runtime.getRuntime().availableProcessors() >= 4, "워커 4개를 실제로 병렬로 돌릴 코어가 필요하다");
        stop.set(false);
        SharedScheduler pool = sharedPool(4);
        World w = world(pool);
        List<ConcurrentLinkedQueue<Long>> heavy = new ArrayList<>();
        for (int k = 0; k < heavyN; k++) {
            ConcurrentLinkedQueue<Long> q = new ConcurrentLinkedQueue<>();
            heavy.add(q);
            everyTick(w, new ChunkPos((k + 1) * 4096, 0), r -> {
                q.add(System.nanoTime());
                spin(55 * MS);
            });
        }
        ConcurrentLinkedQueue<Long> light = new ConcurrentLinkedQueue<>();
        everyTick(w, new ChunkPos(0, 0), r -> {
            light.add(System.nanoTime());
            spin(2 * MS);
        });
        Thread.sleep(3_000);   // 자리 잡기
        light.clear();
        heavy.forEach(ConcurrentLinkedQueue::clear);
        long t0 = System.nanoTime();
        Thread.sleep(10_000);
        double secs = (System.nanoTime() - t0) / 1e9;
        stop.set(true);

        Long[] ls = light.toArray(Long[]::new);
        long[] gaps = new long[ls.length - 1];
        for (int i = 1; i < ls.length; i++) gaps[i - 1] = ls[i] - ls[i - 1];
        Arrays.sort(gaps);
        double[] heavyTps = new double[heavyN];
        for (int k = 0; k < heavyN; k++) heavyTps[k] = heavy.get(k).size() / secs;
        double lightTps = ls.length / secs;
        String msg = String.format("T-171 무거운 %d개: 가벼운 region TPS %.2f, 틱 간격 p50=%.1fms p99=%.1fms max=%.1fms | 무거운 region TPS 최소 %.2f 최대 %.2f (평균 %.2f)",
                heavyN, lightTps, gaps[gaps.length / 2] / 1e6, gaps[gaps.length * 99 / 100] / 1e6, gaps[gaps.length - 1] / 1e6,
                Arrays.stream(heavyTps).min().orElse(0), Arrays.stream(heavyTps).max().orElse(0), Arrays.stream(heavyTps).average().orElse(0));
        System.out.println(msg);
        for (int i = closers.size() - 1; i >= 0; i--) closers.get(i).close();
        closers.clear();
        return new Scene(lightTps, gaps[gaps.length / 2], gaps[gaps.length * 99 / 100], gaps[gaps.length - 1], heavyTps, msg);
    }

    /**
     * 수요(3×55ms/50ms ≈ 3.3 워커)가 워커 4개 안이면: 무거운 region 은 한 칸씩 쉬지 않아 TPS 가 18 가까이 오르고(예전 약 9.5),
     * 가벼운 region 은 20 TPS 를 지키며, 무거운 셋은 고르게 돈다.
     */
    @Test
    void overloadedRegionsWithinCapacityRunBackToBackAndTheLightOneStaysOnTime() throws Exception {
        Scene s = run(3);
        assertTrue(s.lightTps() >= 19.5, s.msg());
        assertTrue(s.p99() <= 120 * MS, s.msg());
        assertTrue(s.hMin() >= 16.0, "무거운 region 이 한 주기씩 쉬면 약 9.5 로 내려간다: " + s.msg());
        assertTrue(s.hMax() <= s.hMin() * 1.2, "무거운 region 끼리 고르게 돌아야 한다: " + s.msg());
    }

    /**
     * 수요가 워커를 넘으면(5×55ms/50ms = 5.5 > 4): 무거운 다섯은 용량을 똑같이 나눠 갖고(마감 순 — 가장 오래 기다린 것 먼저) 예전(9.5)보다
     * 빨라진다. 가벼운 region 은 같은 줄에 서므로 워커가 비는 틈이 없어져 20 에서 조금(약 7%) 내려간다 — 틱이 겹쳐 끝나는 무리(코호트)마다
     * 한 번씩 돈다. 이 대가는 알려진 한계이며(PR 본문), 아래 단언은 그 안에서 굶지 않는다는 것만 지킨다.
     */
    @Test
    void saturatedPoolSharesCapacityFairlyAndTheLightOneDoesNotStarve() throws Exception {
        Scene s = run(5);
        assertTrue(s.lightTps() >= 17.0, s.msg());
        assertTrue(s.p99() <= 120 * MS, s.msg());
        assertTrue(s.hMin() >= 12.0, "무거운 region 이 한 주기씩 쉬면 약 9.5 로 내려간다: " + s.msg());
        assertTrue(s.hMax() <= s.hMin() * 1.3, "무거운 region 끼리 고르게 돌아야 한다: " + s.msg());
    }
}
