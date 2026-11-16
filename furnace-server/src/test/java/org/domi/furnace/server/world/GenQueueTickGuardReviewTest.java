// T-172 생성 조절기 검토 시험(PR #377 검토 R1~R4): 생성 조절기 경계·닫기·바쁜 대기·시야 밖 취소 지연·스트레스.
package org.domi.furnace.server.world;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GenQueueTickGuardReviewTest {

    private static final int THREADS = 4, VD = 4;
    private GenQueue q;
    private FurnaceSchedulerImpl sched;
    private ExecutorService io;
    private World w;
    private final CountDownLatch gate = new CountDownLatch(1);
    private final AtomicInteger running = new AtomicInteger(), finished = new AtomicInteger();

    @AfterEach
    void close() throws Exception {
        gate.countDown();
        if (q != null) { q.shutdown(); assertTrue(q.awaitTermination(10, TimeUnit.SECONDS)); }
        if (io != null) io.shutdown();
        if (sched != null) sched.close();
    }

    private static GenQueue.Settings settings() { return GenQueue.Settings.fromSystem(THREADS, 16); }

    private void submitGen(int n) {
        for (int i = 0; i < n; i++) {
            q.tagged(null, i, 0, null).execute(() -> {
                running.incrementAndGet();
                try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                running.decrementAndGet();
                finished.incrementAndGet();
            });
        }
    }

    private void awaitRunning(int n) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (running.get() < n && System.nanoTime() < end) Thread.sleep(5);
        assertEquals(n, running.get());
    }

    /** 잠금 안에서 genRunning 을 읽는다(검토용 반사). */
    private static int gr(GenQueue g) {
        try {
            var lf = GenQueue.class.getDeclaredField("lock");
            lf.setAccessible(true);
            var l = (java.util.concurrent.locks.ReentrantLock) lf.get(g);
            var f = GenQueue.class.getDeclaredField("genRunning");
            f.setAccessible(true);
            l.lock();
            try { return f.getInt(g); } finally { l.unlock(); }
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    private int genRunning() throws Exception {
        return gr(q);
    }

    /** 문턱 경계: 19.0 은 줄이지 않고(< 19), 19.8 은 늘린다(≥ 19.8). */
    @Test
    void thresholds_boundaries() {
        q = new GenQueue(THREADS, settings(), true, false);
        assertEquals(THREADS, q.adjustLimit(GenQueue.TICK_GUARD_LOW), "정확히 19.0 이면 그대로");
        assertEquals(THREADS - 1, q.adjustLimit(Math.nextDown(GenQueue.TICK_GUARD_LOW)));
        assertEquals(THREADS - 1, q.adjustLimit(Math.nextDown(GenQueue.TICK_GUARD_HIGH)), "19.8 바로 아래면 그대로");
        assertEquals(THREADS, q.adjustLimit(GenQueue.TICK_GUARD_HIGH), "정확히 19.8 이면 늘린다");
    }

    /** 조절기는 모든 차원 중 가장 느린 region 을 본다(잴 region 이 없는 차원은 건너뛰고, 아무것도 없으면 20). */
    @Test
    void worstOf_takesMinimumAcrossDimensions() {
        assertEquals(20.0, GenQueue.worstOf(List.of()));
        assertEquals(20.0, GenQueue.worstOf(java.util.Arrays.asList(null, null)));
        assertEquals(12.5, GenQueue.worstOf(java.util.Arrays.asList(new Regionizer.RegionRate(null, 19.9), null,
                new Regionizer.RegionRate(null, 12.5), new Regionizer.RegionRate(null, 18.0))));
        assertEquals(20.0, GenQueue.worstOf(List.of(new Regionizer.RegionRate(null, 25.0))), "20 위는 20 으로 본다");
    }

    /** 닫는 중에는 상한을 보지 않는다: 문을 열기 전에 남은 고정 요청이 스레드 수만큼 동시에 돈다. */
    @Test
    void shutdownWhileLimited_ignoresLimitImmediately() throws Exception {
        q = new GenQueue(THREADS, settings(), true, false);
        for (int i = 0; i < THREADS; i++) q.adjustLimit(0.0);
        submitGen(6);
        awaitRunning(1);
        q.shutdown();
        awaitRunning(THREADS);
        gate.countDown();
        assertTrue(q.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(6, finished.get());
        assertEquals(0, genRunning());
    }

    /** 상한에 막혀 기다리는 일꾼은 잠들어 있어야 한다(바쁜 대기 없음). */
    @Test
    void blockedWorkers_doNotSpin() throws Exception {
        q = new GenQueue(THREADS, settings(), true, false);
        for (int i = 0; i < THREADS; i++) q.adjustLimit(0.0);
        submitGen(THREADS * 3);
        awaitRunning(1);
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        List<Long> ids = new ArrayList<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) if (t.getName().equals("furnace-gen-wait")) ids.add(t.threadId());
        long before = 0, after = 0;
        for (long id : ids) before += Math.max(0, mx.getThreadCpuTime(id));
        Thread.sleep(500);
        for (long id : ids) after += Math.max(0, mx.getThreadCpuTime(id));
        long ms = TimeUnit.NANOSECONDS.toMillis(after - before);
        assertTrue(ms < 100, "기다리는 일꾼 CPU " + ms + "ms / 0.5초");
    }

    private void world() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        io = Executors.newSingleThreadExecutor();
        q = new GenQueue(THREADS, new GenQueue.Settings(true, 16, 0, 16, 0, 0, TimeUnit.SECONDS.toNanos(3), false), true, false);
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, VD, VD,
                reg.directBits(), 7, io, q);
        w.configureStreaming(0, Config.CHUNKS_PER_TICK_AUTO);
        Player p = new Player(1, UUID.randomUUID(), "P1", new ConcurrentEmbeddedChannel());
        p.viewDistance(VD);
        p.applyMove(8, -60, 8, 0, 0, true);
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
    }

    /**
     * 상한 1 에 막혀 모든 일꾼이 기다리는 동안 시야 밖 미리 불러오기 취소가 얼마나 늦나.
     * 도는 생성(문에 막힘)이 끝나야 다시 잰다 — 생성 하나 시간만큼 플레이어 자리(chunkPrefetches)가 돌아오지 않는다.
     */
    @Test
    void outOfViewCancel_whileAllWorkersBlockedByLimit() throws Exception {
        world();
        for (int i = 0; i < THREADS; i++) q.adjustLimit(0.0);
        CountDownLatch started = new CountDownLatch(1);
        q.tagged(w, 0, 0, null).execute(() -> {   // 시야 안 고정 생성 하나가 상한 1 을 차지
            started.countDown();
            try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        GenQueue.Ticket far = new GenQueue.Ticket(true);
        CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> {
            if (!far.tryStart()) throw new java.util.concurrent.CancellationException();
            return "far";
        }, q.tagged(w, 40, 0, far));
        long t0 = System.nanoTime();
        while (!far.cancelled() && System.nanoTime() - t0 < TimeUnit.MILLISECONDS.toNanos(1000)) Thread.sleep(10);
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue(far.cancelled(), "1초 안에 시야 밖 요청이 취소돼야 한다(REPRIO 50ms)");
        assertThrows(java.util.concurrent.ExecutionException.class, () -> f.get(5, TimeUnit.SECONDS));
    }

    /**
     * 스트레스: 여러 스레드가 생성 요청·이어 하기를 넣고, 다른 스레드가 상한을 마구 바꾸고, 생성 요청 시작 순간의 동시 수를 잰다.
     * 상한이 1 로 고정된 구간에선 동시 1, 끝나면 모두 끝나고 genRunning 0.
     */
    @Test
    void stress_randomLimits_allComplete_counterReturnsToZero() throws Exception {
        for (int round = 0; round < 5; round++) {
            running.set(0); finished.set(0);
            q = new GenQueue(THREADS, settings(), true, false);
            final GenQueue qq = q;
            AtomicInteger gen = new AtomicInteger(), plainDone = new AtomicInteger(), violations = new AtomicInteger();
            AtomicInteger frozenAtOne = new AtomicInteger();   // 1 이면 지금 상한이 1 로 고정된 구간
            int perProducer = 1500, producers = 3, plains = 2000;
            CountDownLatch all = new CountDownLatch(perProducer * producers + plains);
            Thread[] ps = new Thread[producers + 2];
            for (int k = 0; k < producers; k++) {
                int kk = k;
                ps[k] = new Thread(() -> {
                    for (int i = 0; i < perProducer; i++) {
                        qq.tagged(null, kk * 100000 + i, 0, null).execute(() -> {
                            int c = gen.incrementAndGet();
                            if (frozenAtOne.get() == 1 && c > 1) violations.incrementAndGet();
                            if (ThreadLocalRandom.current().nextInt(8) == 0) Thread.onSpinWait();
                            gen.decrementAndGet();
                            all.countDown();
                        });
                    }
                });
            }
            ps[producers] = new Thread(() -> {
                for (int i = 0; i < plains; i++) qq.execute(() -> { plainDone.incrementAndGet(); all.countDown(); });
            });
            ps[producers + 1] = new Thread(() -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                for (int i = 0; i < 4000 && all.getCount() > 0; i++) {
                    if (i % 500 == 250) {   // 상한을 1 로 내리고 이미 돌던 것이 빠질 때까지 기다린 뒤 잠깐 고정
                        for (int j = 0; j < THREADS; j++) qq.adjustLimit(0.0);
                        long end = System.nanoTime() + 50_000_000L;
                        while (gr(qq) > 1 && System.nanoTime() < end) Thread.onSpinWait();
                        if (gr(qq) <= 1) {
                            frozenAtOne.set(1);
                            try { Thread.sleep(2); } catch (InterruptedException e) { return; }
                            frozenAtOne.set(0);
                        }
                    }
                    qq.adjustLimit(r.nextBoolean() ? 5.0 : 20.0);
                    if (r.nextInt(4) == 0) Thread.yield();
                }
                for (int j = 0; j < THREADS; j++) qq.adjustLimit(20.0);
            });
            for (Thread t : ps) t.start();
            for (Thread t : ps) t.join();
            assertTrue(all.await(30, TimeUnit.SECONDS), "남은 작업 " + all.getCount());
            assertEquals(0, violations.get(), "상한 1 구간에 생성이 둘 이상 돌았다");
            assertEquals(plains, plainDone.get());
            // 작업 안의 countDown 뒤에 일꾼의 finally 가 genRunning 을 돌려주므로 잠깐 기다린다
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (genRunning() != 0 && System.nanoTime() < until) Thread.sleep(1);
            assertEquals(0, genRunning(), "genRunning 이 0 으로 돌아와야 한다");
            q.shutdown();
            assertTrue(q.awaitTermination(5, TimeUnit.SECONDS));
            q = null;
        }
    }
}
