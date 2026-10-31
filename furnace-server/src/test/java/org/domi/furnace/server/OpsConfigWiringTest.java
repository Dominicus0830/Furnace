package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** server.properties 의 max-world-size / spawn-npcs / max-tick-time 프로브가 실제 서버에 연결됐는지. */
class OpsConfigWiringTest {

    @TempDir Path tmp;

    @Test
    void maxWorldSizeReachesWorldBorder() throws Exception {
        Files.writeString(tmp.resolve("server.properties"), "max-world-size=1000\n");
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            assertEquals(1000, core.border().getAbsoluteMaxSize());
            assertEquals(1000.0, core.border().getMaxX());   // 기본 보더(약 6천만)는 절대 한계로 잘린다
        } finally {
            sched.close();
        }
    }

    @Test
    void defaultMaxWorldSizeIsVanillaValue() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            assertEquals(29999984, TestServers.core(tmp, sched).border().getAbsoluteMaxSize());
        } finally {
            sched.close();
        }
    }

    @Test
    void watchdogProbesCoverGlobalAndRegionTicks() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            List<ServerWatchdog.Probe> probes = core.watchdogProbes();
            assertEquals("global tick", probes.get(0).name());
            assertTrue(probes.size() >= 2);
            assertTrue(probes.get(1).name().startsWith("region tick "));
            core.scheduledTick();
            assertTrue(probes.get(0).stalledNanos(System.nanoTime()) < 5_000_000_000L);
            assertEquals(Thread.currentThread(), probes.get(0).thread());
            Thread.sleep(120);
            assertTrue(probes.get(0).stalledNanos(System.nanoTime()) >= 100_000_000L, "박동이 없으면 멈춘 시간이 자란다");
            core.scheduledTick();
            assertTrue(probes.get(0).stalledNanos(System.nanoTime()) < 100_000_000L, "박동하면 다시 0 근처");
            // 쉬는 차원에서는 region 프로브가 임계(수 초)를 넘지 않는다
            assertTrue(probes.get(1).stalledNanos(System.nanoTime()) < 5_000_000_000L);
        } finally {
            sched.close();
        }
    }
}
