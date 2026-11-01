package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.builtin.FurnaceAdminCommands;
import org.domi.furnace.server.command.builtin.FurnaceAdminCommands.DimView;
import org.domi.furnace.server.command.builtin.FurnaceAdminCommands.GlobalView;
import org.domi.furnace.server.remote.RconConsole;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.sched.TickTimeRing;
import org.domi.furnace.server.world.Regionizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** /furnace threads|regions|profile — 서식은 합성 값으로 결정적으로, 등록·권한·실서버 수집은 TestServers 로. */
class FurnaceAdminCommandsTest {

    @TempDir Path tmp;

    private static final long MS = 1_000_000L;

    private static Regionizer.SchedStats sched() {
        return new Regionizer.SchedStats("minecraft:overworld", 4, 200, 0, 5 * MS, 1 * MS, 3 * MS, 4 * MS, 0, 0, 2, 3, List.of());
    }

    private static List<Regionizer.RegionInfo> regions() {
        return List.of(
                new Regionizer.RegionInfo(1, 2, 30, 0, null, 0, 1 * MS, 2 * MS, -4, -4, 3, 3),
                new Regionizer.RegionInfo(2, 1, 9, 5, "furnace-region-1", 12 * MS, 8 * MS, 40 * MS, 100, 100, 107, 107),
                new Regionizer.RegionInfo(3, 1, 4, 1, null, 0, 3 * MS, 6 * MS, 200, 0, 207, 7));
    }

    private static DimView dim() {
        return new DimView("minecraft:overworld", 3, 1, sched(), new TickTimeRing.Stats(100, 2 * MS, 9 * MS, 40 * MS, 55 * MS), regions(),
                List.of(new String[] {"furnace-region-0", "TIMED_WAITING"}, new String[] {"furnace-region-1", "RUNNABLE"}));
    }

    private static final GlobalView GLOBAL = new GlobalView("furnace-global", 7, new TickTimeRing.Stats(50, 1 * MS, 2 * MS, 3 * MS, 4 * MS));

    @Test
    void threadsReportListsGlobalDimensionAndWorkers() {
        List<String> r = FurnaceAdminCommands.threadsReport(GLOBAL, List.of(dim()));
        assertEquals("== 스레드 현황 ==", r.get(0));
        assertEquals("전역 furnace-global: 대기 작업 7, 틱 p50 1.00 / p95 2.00 / 최근 최대 3.00 / 가동 후 최대 4.00 ms (표본 50)", r.get(1));
        assertEquals("minecraft:overworld: region 3개(대기열 1), 워커 4개 중 2개 틱 중(최대 동시 3)", r.get(2));
        assertEquals("  틱 p50 2.00 / p95 9.00 / 최근 최대 40.00 / 가동 후 최대 55.00 ms (표본 100)", r.get(3));
        assertEquals("  큐 지연 p50 1.00 / p95 3.00 / p99 4.00 / 최대 5.00 ms (틱 200회)", r.get(4));
        assertEquals("  워커 furnace-region-0 [TIMED_WAITING] 대기", r.get(5));
        assertEquals("  워커 furnace-region-1 [RUNNABLE] region #2 (12.00 ms째)", r.get(6));
    }

    @Test
    void emptySamplesAreReadable() {
        assertEquals("틱 표본 없음", FurnaceAdminCommands.tickLine(TickTimeRing.Stats.EMPTY));
    }

    @Test
    void regionsSortedByLastTickDescAndLimited() {
        List<String> r = FurnaceAdminCommands.regionsReport(List.of(dim()), 2);
        assertEquals("minecraft:overworld: region 3개", r.get(1));
        assertTrue(r.get(2).startsWith("  #2 "), r.get(2));   // 직전 8ms 가 가장 길다
        assertTrue(r.get(2).contains("워커 furnace-region-1"));
        assertTrue(r.get(2).contains("범위 청크 (100,100)~(107,107)"));
        assertTrue(r.get(3).startsWith("  #3 "), r.get(3));
        assertEquals("  … 1개 더", r.get(4));
    }

    @Test
    void profileShowsSlowestRegionsAndHints() {
        List<String> r = FurnaceAdminCommands.profileReport(GLOBAL, List.of(dim()), 20f);
        assertTrue(r.get(0).contains("목표 20.0 TPS") && r.get(0).contains("틱 예산 50.00 ms"), r.get(0));
        assertTrue(r.stream().anyMatch(l -> l.startsWith("  느린 region #2: 최대 40.00 ms")), r.toString());
        assertEquals("단계별 분해는 -Dfurnace.tickstats=true, 표본 추적은 /jfr start.", r.get(r.size() - 1));
    }

    @Test
    void tickTimeRingPercentilesAndMax() {
        TickTimeRing ring = new TickTimeRing(8);
        assertEquals(TickTimeRing.Stats.EMPTY, ring.stats());
        for (int i = 1; i <= 10; i++) ring.record(i * MS);   // 8칸이라 3..10 만 남는다
        var s = ring.stats();
        assertEquals(8, s.samples());
        assertEquals(7 * MS, s.p50Nanos());   // 정렬 3..10 의 인덱스 4
        assertEquals(10 * MS, s.p95Nanos());
        assertEquals(10 * MS, s.maxRecentNanos());
        assertEquals(10 * MS, s.maxEverNanos());
        ring.record(1);
        assertEquals(10 * MS, ring.stats().maxEverNanos());
    }

    @Test
    void commandRunsAgainstRealServerAndRespectsPermission() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            FurnaceAdminCommands.register(core.commands().getDispatcher());
            var root = core.commands().getDispatcher().getRoot();
            assertNotNull(root.getChild("furnace"));
            var spawn = core.world().defaultSpawn();
            for (String sub : new String[] {"threads", "regions", "regions 3", "profile"}) {
                var sink = new RconConsole(() -> false, spawn.x(), spawn.y(), spawn.z());
                core.commands().performPrefixedCommandAsync(CommandSource.rcon(core, sink), "furnace " + sub).get(20, java.util.concurrent.TimeUnit.SECONDS);
                String out = sink.takeOutput();
                assertFalse(out.isEmpty(), sub);
                assertTrue(out.contains("overworld") || out.contains("전역"), out);
            }
            // 권한 3 미만은 실행할 수 없다(트리에서 보이지 않아 알 수 없는 명령으로 실패)
            var low = new RconConsole(() -> false, spawn.x(), spawn.y(), spawn.z());
            core.commands().performPrefixedCommandAsync(CommandSource.rcon(core, low).withPermission(2), "furnace threads").get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertFalse(low.takeOutput().contains("스레드 현황"));
        } finally {
            sched.close();
        }
    }
}
