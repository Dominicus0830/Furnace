// 운영 서버 모드(driveByServerTicks): 전역 지연 작업은 ServerCore.scheduledTick 이 낸 서버 틱으로 센다(A14-04) — rate·freeze 를 따르고 대체 박동은 세지 않는다.
package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerDrivenSchedulerTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    GlobalThread g;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        g = (GlobalThread) sched.global();
        g.driveByServerTicks();
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private void beat() throws Exception { g.submit(() -> { core.scheduledTick(); return null; }).get(5, TimeUnit.SECONDS); }

    @Test
    void delayedTasksCountServerTicksNotWallClock() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        g.executeDelayed(() -> log.add("d2"), 2);
        Thread.sleep(300);
        assertTrue(log.isEmpty(), "서버 박동 없이는 벽시계로 돌지 않는다");
        assertEquals(0, g.schedulerTick());
        beat();
        assertTrue(log.isEmpty());
        beat();
        assertEquals(List.of("d2"), log, "두 번째 서버 틱에");
        assertEquals(2, g.schedulerTick());
    }

    @Test
    void followsTickRateAndKeepsCountingWhileFrozen() throws Exception {
        core.tickState().setRate(10f);   // 박동 둘에 서버 틱 하나
        for (int i = 0; i < 4; i++) beat();
        assertEquals(2, g.schedulerTick(), "rate 10: 박동 4번에 스케줄러 틱 2");
        core.tickState().setRate(20f);
        core.tickState().setFrozen(true);
        long before = g.schedulerTick();
        for (int i = 0; i < 3; i++) beat();
        assertEquals(before + 3, g.schedulerTick(), "/tick freeze 중에도 스케줄러는 센다(Paper heartbeat)");
    }
}
