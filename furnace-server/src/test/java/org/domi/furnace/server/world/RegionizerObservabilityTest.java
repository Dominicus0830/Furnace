package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** /furnace 와 워치독이 읽는 Regionizer 관측 값(틱 시간 분포·region 사본·워커 상태·멈춤 시간). */
class RegionizerObservabilityTest {

    @TempDir Path tmp;

    private static void until(BooleanSupplier c, long ms) throws InterruptedException {
        long end = System.nanoTime() + ms * 1_000_000L;
        while (!c.getAsBoolean() && System.nanoTime() < end) Thread.sleep(10);
    }

    @Test
    void liveRegionReportsTicksAndStaysFarFromWatchdogThreshold() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            World world = core.world();
            world.setChunkForced(0, 0, true);
            until(() -> world.loadedChunk(0, 0) != null, 10_000);
            assertNotNull(world.loadedChunk(0, 0));
            world.startTicking();
            Regionizer rz = world.regionizer();
            until(() -> rz.tickTimes().samples() >= 5, 10_000);
            var t = rz.tickTimes();
            assertTrue(t.samples() >= 5, "틱 표본이 쌓인다: " + t);
            assertTrue(t.p50Nanos() <= t.p95Nanos() && t.p95Nanos() <= t.maxRecentNanos() && t.maxRecentNanos() <= t.maxEverNanos());

            List<Regionizer.RegionInfo> infos = rz.regionInfos();
            assertFalse(infos.isEmpty());
            var r = infos.get(0);
            assertTrue(r.chunks() >= 1 && r.sections() >= 1);
            assertTrue(r.minChunkX() <= 0 && r.maxChunkX() >= 0 && r.minChunkZ() <= 0 && r.maxChunkZ() >= 0, "구역 범위가 (0,0) 청크를 덮는다: " + r);
            until(() -> rz.regionInfos().stream().anyMatch(i -> i.lastTickNanos() > 0), 5_000);
            assertTrue(rz.regionInfos().stream().anyMatch(i -> i.lastTickNanos() > 0 && i.maxTickNanos() >= i.lastTickNanos() - 1));

            assertFalse(rz.workerStates().isEmpty());
            assertTrue(rz.workerStates().get(0)[0].startsWith("furnace-region-"));
            assertTrue(rz.queuedRegions() >= 0);
            // 정상 가동 중에는 멈춤 시간이 몇 초를 넘지 않는다(워치독 기본 60초에서 한참 멀다)
            assertTrue(rz.stalledNanos(System.nanoTime()) < 5_000_000_000L);
            assertEquals("minecraft:overworld", rz.dimension());
        } finally {
            sched.close();
        }
    }

    @Test
    void stalledNanosGrowsWhileATickBlocksAndTheThreadIsReported() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            World world = core.world();
            world.setChunkForced(0, 0, true);
            until(() -> world.loadedChunk(0, 0) != null, 10_000);
            world.startTicking();
            Regionizer rz = world.regionizer();
            java.util.concurrent.CountDownLatch inTask = new java.util.concurrent.CountDownLatch(1), release = new java.util.concurrent.CountDownLatch(1);
            Thread[] worker = new Thread[1];
            world.runAt(0.5, 0.5, region -> {
                worker[0] = Thread.currentThread();
                inTask.countDown();
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(inTask.await(10, java.util.concurrent.TimeUnit.SECONDS));
            Thread.sleep(250);
            assertTrue(rz.stalledNanos(System.nanoTime()) >= 200_000_000L, "작업 하나가 틱을 붙잡고 있으면 멈춤 시간이 자란다");
            assertEquals(worker[0], rz.longestRunningThread(System.nanoTime()));
            var running = rz.regionInfos().stream().filter(i -> i.thread() != null).findFirst();
            assertTrue(running.isPresent());
            assertEquals(worker[0].getName(), running.get().thread());
            assertTrue(running.get().runningNanos() >= 200_000_000L);
            release.countDown();
            until(() -> rz.stalledNanos(System.nanoTime()) < 150_000_000L, 5_000);
            assertTrue(rz.stalledNanos(System.nanoTime()) < 150_000_000L, "풀면 멈춤 시간이 줄어든다");
        } finally {
            sched.close();
        }
    }
}
