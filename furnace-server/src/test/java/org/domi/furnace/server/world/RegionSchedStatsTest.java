// 스케줄 계측(차원·region 별 큐 지연, 기다린 시간, 바쁜 워커 수)이 값으로 보인다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegionSchedStatsTest {

    private FurnaceSchedulerImpl sched;
    private World w;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(10_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 3, 4, 1, 2, 2,
                reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor(),
                java.util.concurrent.Executors.newSingleThreadExecutor());
        w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @AfterEach
    void tearDown() {
        sched.close();
    }

    @Test
    void statsAreExposedPerDimensionAndRegion() throws Exception {
        ChunkPos a = new ChunkPos(0, 0), b = new ChunkPos(4096, 0);   // 멀리 — region 둘
        w.submitAtChunk(a, r -> r.chunkAtBlocking(a)).get(10, TimeUnit.SECONDS);
        w.submitAtChunk(b, r -> r.chunkAtBlocking(b)).get(10, TimeUnit.SECONDS);
        Regionizer rz = w.regionizer();
        long deadline = System.nanoTime() + 5_000_000_000L;
        Regionizer.SchedStats s;
        do {
            Thread.sleep(20);
            s = rz.schedStats();
        } while (s.regions().stream().filter(x -> x.ticks() >= 20).count() < 2 && System.nanoTime() < deadline);
        assertEquals(w.dimension(), s.dimension());
        assertEquals(3, s.threads());
        assertTrue(s.regions().size() >= 2, "region 별 항목: " + s.regions());
        long perRegion = s.regions().stream().mapToLong(Regionizer.RegionSchedStats::ticks).sum();
        assertTrue(perRegion >= 40 && perRegion <= s.ticks(), "region 합 ≤ 차원 합: " + perRegion + " / " + s.ticks());
        assertTrue(s.queueDelayMaxNanos() >= 0 && s.queueDelaySumNanos() >= s.queueDelayMaxNanos());
        assertTrue(s.queueDelayP50Nanos() <= s.queueDelayP95Nanos() && s.queueDelayP95Nanos() <= s.queueDelayP99Nanos());
        for (var r : s.regions()) assertTrue(r.queueDelayMaxNanos() <= s.queueDelayMaxNanos());
        assertTrue(s.waitNanos() > 0 && s.wakeups() > 0, "유휴 워커는 기다린다: " + s);
        assertTrue(s.peakActiveWorkers() >= 1 && s.peakActiveWorkers() <= 3, "바쁜 워커 최대: " + s.peakActiveWorkers());
        assertTrue(s.activeWorkers() >= 0 && s.activeWorkers() <= 3);
        assertTrue(s.queueDelayAvgNanos() < 50_000_000L, "유휴 서버 큐 지연 평균은 틱보다 훨씬 작다: " + s.queueDelayAvgNanos());
    }
}
