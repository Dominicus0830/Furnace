// 스핀 대기(거친 타이머 플랫폼 경로)와 시간 비교 규칙: 도는 워커는 풀 전체에서 하나, MAX(후보 없음)·nanoTime 경계에서 비교가 맞다.
// 설계: docs/feature-parity/shared-scheduler-design-2026-10-28.md §3.1(N4), §5(N6)
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class SharedSchedulerSpinTest {

    @Test
    void dueComparisonsUseDifferencesAndSkipMax() {
        for (long now : new long[] {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE - 1, Long.MAX_VALUE}) {
            assertFalse(SharedScheduler.isDue(Long.MAX_VALUE, now), "MAX 는 언제나 후보 아님: now=" + now);
        }
        long beforeWrap = Long.MAX_VALUE - 10, afterWrap = Long.MIN_VALUE + 10;   // afterWrap 이 21ns 뒤
        assertTrue(SharedScheduler.isDue(beforeWrap, afterWrap), "경계를 넘은 now 에서도 지난 마감은 때가 됐다");
        assertFalse(SharedScheduler.isDue(afterWrap, beforeWrap), "경계 너머 마감은 아직");
        assertTrue(SharedScheduler.earlier(beforeWrap, afterWrap));
        assertFalse(SharedScheduler.earlier(afterWrap, beforeWrap));
        assertTrue(SharedScheduler.earlier(afterWrap, Long.MAX_VALUE));
        assertFalse(SharedScheduler.earlier(Long.MAX_VALUE, afterWrap));
        assertFalse(SharedScheduler.earlier(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    /** spin=17ms 강제, 워커 4개, region 하나(50ms 틱): 도는 워커는 하나뿐이라 워커 CPU 합이 한 워커가 도는 몫 정도다. 틱은 제때 돈다. */
    @Test
    void onlyOneWorkerSpinsWhenTheTimerIsCoarse() throws Exception {
        SharedScheduler pool = new SharedScheduler(4, "furnace-region-spintest-", 17_000_000L);
        pool.start();
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ExecutorService io = Executors.newSingleThreadExecutor(), gen = Executors.newSingleThreadExecutor();
        try {
            World w = new World(DimensionType.OVERWORLD, new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, pool, 4, 1, 2, 2,
                    reg.directBits(), 7, io, gen);
            w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
            ChunkPos cp = new ChunkPos(0, 0);
            AtomicInteger ticks = new AtomicInteger();
            long key = Regionizer.chunkKey(0, 0);
            w.submitAtChunk(cp, r -> {
                r.schedule(key, new java.util.function.Consumer<Region>() {
                    @Override public void accept(Region r2) {
                        ticks.incrementAndGet();
                        r2.schedule(key, this, 1);
                    }
                }, 1);
                return null;
            }).get(10, TimeUnit.SECONDS);
            Thread.sleep(300);
            var mx = ManagementFactory.getThreadMXBean();
            long cpu0 = workerCpu(mx), t0 = ticks.get(), wall0 = System.nanoTime();
            Thread.sleep(2000);
            long cpu = workerCpu(mx) - cpu0, n = ticks.get() - t0, wall = System.nanoTime() - wall0;
            // 한 워커가 틱(50ms)마다 ~17ms 돌면 벽시계의 ~34%, 둘이면 ~68%, 넷이면 ~136%
            assertTrue(cpu < wall * 55 / 100, "도는 워커가 하나보다 많다: 워커 CPU " + cpu / 1_000_000 + "ms / " + wall / 1_000_000 + "ms");
            assertTrue(n >= 30, "틱은 계속 돈다: " + n);
        } finally {
            sched.close();
            pool.close();
            io.shutdownNow();
            gen.shutdownNow();
        }
    }

    private static long workerCpu(java.lang.management.ThreadMXBean mx) {
        long sum = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith("furnace-region-spintest-")) sum += Math.max(0L, mx.getThreadCpuTime(t.threadId()));
        }
        return sum;
    }
}
