// 수동 시계(헤드리스 시험 서버 furnace-test 가 쓰는 결정적 틱)와 region 지연 작업의 기준 틱.
// - 수동 시계: 낸 틱 수만큼만 region 게임 틱·지연 작업이 돌고, 그 사이에는 작업 큐만 비운다.
// - 플러그인 region 스케줄러: region 스레드 안에서 다시 거는 반복 작업(runAtFixedRate)이 주기 1 이면 매 틱 돈다(예전에는 큐를 거쳐 두 틱마다).
// - 전역 스레드 수동 지연 작업: 0·1 은 다음 틱, n 은 n 틱 뒤, 같은 틱은 넣은 차례.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import org.domi.furnace.server.IoPoolDrainExtension;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.sched.GlobalThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(IoPoolDrainExtension.class)
class ManualClockTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;

    @AfterEach
    void tearDown() {
        if (core != null) core.plugins().close();
        if (sched != null) sched.close();
    }

    private World manualWorld() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        World w = core.world();
        w.useManualClock();
        w.startTicking();
        ChunkPos c = new ChunkPos(0, 0);
        w.submitAtChunk(c, r -> r.chunkAtBlocking(c)).get(30, TimeUnit.SECONDS);
        return w;
    }

    private static void step(World w) throws Exception {
        // 시험 서버(FurnaceTestServer.tick)처럼: 틱 전에 모든 region 이 앞서 받은 작업을 비우게 한다(밖에서 건 지연 작업이 어느 틱에 붙을지 정해지게)
        for (var f : w.forEachRegion(r -> { })) f.get(30, TimeUnit.SECONDS);
        long target = w.advanceManualTick();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!w.manualTickDone(target)) {
            assertTrue(System.nanoTime() < deadline, "틱 " + target + " 이 끝나지 않았다");
            LockSupport.parkNanos(20_000L);
        }
    }

    @Test
    void regionsOnlyTickWhenAskedButStillDrainTasks() throws Exception {
        World w = manualWorld();
        AtomicInteger runs = new AtomicInteger();
        w.chunkScheduler(0, 0).runAtFixedRate(runs::incrementAndGet, 1, 1);
        Region r = w.regionizer().ownerOf(0, 0);
        long t0 = r.tickCount();
        Thread.sleep(250);   // 벽시계 5틱
        assertEquals(0, runs.get(), "낸 틱이 없으면 지연 작업도 돌지 않는다");
        assertEquals(t0, r.tickCount(), "틱 번호도 그대로");
        assertEquals("ok", w.submitAtChunk(new ChunkPos(0, 0), x -> "ok").get(5, TimeUnit.SECONDS), "작업 큐는 틱 없이 비운다");
        for (int i = 0; i < 7; i++) step(w);
        assertEquals(7, runs.get());
        assertEquals(t0 + 7, r.tickCount());
    }

    @Test
    void newRegionStartsFromCurrentTarget() throws Exception {
        World w = manualWorld();
        for (int i = 0; i < 3; i++) step(w);
        ChunkPos far = new ChunkPos(300, 300);
        w.submitAtChunk(far, r -> r.chunkAtBlocking(far)).get(30, TimeUnit.SECONDS);
        assertNotSame(w.regionizer().ownerOf(0, 0), w.regionizer().ownerOf(300, 300));
        assertTrue(w.manualTickDone(3), "나중에 생긴 region 은 이미 지난 틱을 따라잡지 않는다");
        AtomicInteger runs = new AtomicInteger();
        w.chunkScheduler(300, 300).runAtFixedRate(runs::incrementAndGet, 1, 1);
        for (int i = 0; i < 4; i++) step(w);
        assertEquals(4, runs.get());
    }

    /** 수동 시계 없이(운영): 주기 1 반복 작업은 region 틱마다 한 번. */
    @Test
    void periodOneRepeatRunsEveryTickOnRealClock() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        World w = core.world();
        w.startTicking();
        ChunkPos c = new ChunkPos(0, 0);
        w.submitAtChunk(c, r -> r.chunkAtBlocking(c)).get(30, TimeUnit.SECONDS);
        List<Long> at = new CopyOnWriteArrayList<>();
        w.chunkScheduler(0, 0).runAtFixedRate(() -> at.add(w.regionizer().ownerOf(0, 0).tickCount()), 1, 1);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (at.size() < 6) {
            assertTrue(System.nanoTime() < deadline);
            Thread.sleep(20);
        }
        for (int i = 1; i < 6; i++) assertEquals(at.get(i - 1) + 1, at.get(i), "연속한 틱: " + at);
    }

    @Test
    void globalDelayedTasksCountManualTicks() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        GlobalThread g = (GlobalThread) sched.global();
        g.useManualClock();
        List<String> log = new CopyOnWriteArrayList<>();
        AtomicLong tick = new AtomicLong();
        g.executeDelayed(() -> log.add("d3@" + tick.get()), 3);
        g.executeDelayed(() -> log.add("d0@" + tick.get()), 0);
        g.executeDelayed(() -> log.add("d1@" + tick.get()), 1);
        Thread.sleep(200);
        assertTrue(log.isEmpty(), "벽시계로는 돌지 않는다");
        for (int i = 0; i < 3; i++) g.submit(() -> { tick.incrementAndGet(); g.advanceManualTick(); return null; }).get(5, TimeUnit.SECONDS);
        assertEquals(List.of("d0@1", "d1@1", "d3@3"), log, "0·1 은 다음 틱(넣은 차례), 3 은 셋째 틱");
        assertThrows(IllegalStateException.class, g::advanceManualTick, "전역 스레드 밖에서는 거부");
    }
}
