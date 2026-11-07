package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.builtin.TpsCommands;
import org.domi.furnace.server.remote.RconConsole;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.sched.TickWindows;
import org.domi.furnace.server.world.Regionizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** /tps·/mspt — 서식은 합성 값으로 결정적으로, 등록·권한·실서버 수집은 TestServers 로. */
class TpsCommandsTest {

    @TempDir Path tmp;

    private static final long MS = 1_000_000L;

    private static List<String> plain(List<Text> lines) {
        return lines.stream().map(Text::plainText).toList();
    }

    @Test
    void tpsValueCapsStarsAndColours() {
        assertEquals("20.00", TpsCommands.tpsValue(20.4, 20).plainText());     // 목표 위 5% 안: 목표로 자른다
        assertEquals("*20.00", TpsCommands.tpsValue(35.0, 20).plainText());    // sprint 처럼 훨씬 빠름
        assertEquals("17.25", TpsCommands.tpsValue(17.25, 20).plainText());
        assertEquals("-", TpsCommands.tpsValue(Double.NaN, 20).plainText());
        assertEquals(NamedTextColor.GREEN, TpsCommands.tpsValue(18.0, 20).color());
        assertEquals(NamedTextColor.YELLOW, TpsCommands.tpsValue(17.0, 20).color());
        assertEquals(NamedTextColor.RED, TpsCommands.tpsValue(12.0, 20).color());
        assertEquals(NamedTextColor.GREEN, TpsCommands.tpsValue(9.5, 10).color());   // /tick rate 10 이면 10 이 목표
        assertEquals(NamedTextColor.GRAY, TpsCommands.tpsValue(Double.NaN, 20).color());
    }

    @Test
    void tpsReportShowsGlobalAndSlowestRegionPerDimension() {
        var info = new Regionizer.RegionInfo(7, 2, 40, 0, null, 0, 3 * MS, 9 * MS, -16, -16, 15, 15, 3);
        List<String> r = plain(TpsCommands.tpsReport(new double[] {19.97, 20.0, Double.NaN, 20.0}, 20f, List.of(
                new TpsCommands.DimRate("minecraft:overworld", 3, new Regionizer.RegionRate(info, 14.5)),
                new TpsCommands.DimRate("minecraft:the_nether", 0, null))));
        assertEquals("전역 TPS (최근 5초, 1분, 5분, 15분): 19.97, 20.00, -, 20.00 (목표 20.0)", r.get(0));
        assertEquals("minecraft:overworld 가장 느린 region (최근 5초): 14.50 — #7, 청크 40개, 범위 청크 (-16,-16)~(15,15), region 3개 (목표 20.0)", r.get(1));
        assertEquals("minecraft:the_nether 가장 느린 region (최근 5초): 잴 수 있는 region 없음 (region 0개)", r.get(2));
        assertEquals(3, r.size());
    }

    @Test
    void tpsReportAddsActiveRegionAverageLineAfterGlobal() {
        var t = new org.domi.furnace.api.world.ServerTps(18.5, 19.0, 19.5, 20.0, 2, 5, 12.0, 11.0, 20, 20, 20, 20);
        List<String> r = plain(TpsCommands.tpsReport(new double[] {20, 20, 20, 20}, t, 20f, List.of()));
        assertEquals("활성 region 평균 TPS (최근 5초, 1분, 5분, 15분): 18.50, 19.00, 19.50, 20.00 (목표 20.0 — 플레이어 있는 region 2개, 플레이어 5명, 그중 가장 느린 region 12.00)", r.get(1));
        // 접속자가 없으면 전역 박동 값이라고 밝힌다
        var none = new org.domi.furnace.api.world.ServerTps(20, 20, 20, 20, 0, 0, Double.NaN, 19.0, 20, 20, 20, 20);
        assertTrue(plain(TpsCommands.tpsReport(new double[] {20, 20, 20, 20}, none, 20f, List.of())).get(1).contains("플레이어가 있는 region 이 없어 전역 박동 값"));
    }

    @Test
    void regionsReportListsSlowestFirstAndCaps() {
        var a = new Regionizer.RegionInfo(1, 1, 4, 0, null, 0, 1 * MS, 2 * MS, 0, 0, 7, 7, 0);
        var b = new Regionizer.RegionInfo(2, 1, 9, 0, null, 0, 8 * MS, 40 * MS, 100, 100, 107, 107, 2);
        var c = new Regionizer.RegionInfo(3, 1, 1, 0, null, 0, 1 * MS, 1 * MS, 200, 0, 207, 7, 0);
        List<String> r = plain(TpsCommands.regionsReport(List.of(
                new TpsCommands.RegionLine("minecraft:overworld", a, 20.0),
                new TpsCommands.RegionLine("minecraft:overworld", b, 13.5),
                new TpsCommands.RegionLine("minecraft:the_nether", c, Double.NaN)), 20f, 2));
        assertEquals("== region TPS (최근 5초, 느린 순 2/3개) ==", r.get(0));
        assertEquals("minecraft:overworld region #2: 13.50 — 플레이어 2명, 청크 9개, 범위 청크 (100,100)~(107,107), 직전 틱 8.00 ms, 최장 40.00 ms (목표 20.0)", r.get(1));
        assertTrue(r.get(2).startsWith("minecraft:overworld region #1: 20.00"), r.get(2));   // 잴 수 없는(NaN) 쪽은 뒤로
        assertEquals("… 외 1개 (/tps regions <개수>)", r.get(3));
    }

    @Test
    void msptReportShowsAvgMinMaxPerWindow() {
        var w = new TickWindows.Window(100, 5_000 * MS, 250 * MS, 1 * MS, 60 * MS);   // 평균 2.5ms, 최대 60ms
        List<String> r = plain(TpsCommands.msptReport(List.of(w, w, TickWindows.Window.EMPTY), 20f,
                List.of(new TpsCommands.DimTimes("minecraft:overworld", List.of(w, w, w)))));
        assertEquals("틱 소요 시간 ms, 평균/최소/최대 (최근 5초, 10초, 1분 — 틱 예산 50.00 ms):", r.get(0));
        assertEquals("전역: 2.50/1.00/60.00, 2.50/1.00/60.00, -", r.get(1));
        assertEquals("minecraft:overworld region: 2.50/1.00/60.00, 2.50/1.00/60.00, 2.50/1.00/60.00", r.get(2));
        // 예산 넘은 최대값은 빨강
        Text v = TpsCommands.msptValue(w, 50 * MS);
        assertEquals(NamedTextColor.RED, v.children().get(v.children().size() - 1).color());
        assertEquals(NamedTextColor.GREEN, v.children().get(0).color());
    }

    @Test
    void commandsRunAgainstRealServerAndRespectPermission() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            TpsCommands.register(core.commands().getDispatcher());
            var root = core.commands().getDispatcher().getRoot();
            assertNotNull(root.getChild("tps"));
            assertNotNull(root.getChild("mspt"));
            // 전역 박동 몇 번(실서버 반복 대신 직접) — 1초 넘게 기록이 쌓여야 TPS 를 잰다
            long until = System.nanoTime() + 1_300 * MS;
            while (System.nanoTime() < until) {
                sched.global().submit(() -> { core.scheduledTick(); return null; }).get(5, TimeUnit.SECONDS);
                Thread.sleep(50);
            }
            var spawn = core.world().defaultSpawn();
            var sink = new RconConsole(() -> false, spawn.x(), spawn.y(), spawn.z());
            core.commands().performPrefixedCommandAsync(CommandSource.rcon(core, sink), "tps").get(20, TimeUnit.SECONDS);
            String out = sink.takeOutput();
            assertTrue(out.startsWith("전역 TPS (최근 5초, 1분, 5분, 15분): "), out);
            assertFalse(out.startsWith("전역 TPS (최근 5초, 1분, 5분, 15분): -"), "1.3초 기록이면 5초 창 값이 있다: " + out);
            assertTrue(out.contains("minecraft:overworld 가장 느린 region"), out);
            assertTrue(out.contains("활성 region 평균 TPS"), out);
            var st = core.serverTps();
            assertFalse(Double.isNaN(st.global5s()), "1.3초 기록이면 전역 5초 값이 있다");
            assertEquals(0, st.activePlayers());
            assertEquals(st.global5s(), st.active5s(), 1e-9, "접속자가 없으면 활성 평균은 전역 박동 값이다");
            // 특정 region: 목록·없는 번호·없는 위치
            sink = new RconConsole(() -> false, spawn.x(), spawn.y(), spawn.z());
            core.commands().performPrefixedCommandAsync(CommandSource.rcon(core, sink), "tps regions 5").get(20, TimeUnit.SECONDS);
            out = sink.takeOutput();
            assertTrue(out.startsWith("== region TPS (최근 5초, 느린 순"), out);
            sink = new RconConsole(() -> false, spawn.x(), spawn.y(), spawn.z());
            core.commands().performPrefixedCommandAsync(CommandSource.rcon(core, sink), "tps region minecraft:overworld 99999").get(20, TimeUnit.SECONDS);
            assertTrue(sink.takeOutput().contains("region #99999 가 없다"));
            sink = new RconConsole(() -> false, spawn.x(), spawn.y(), spawn.z());
            core.commands().performPrefixedCommandAsync(CommandSource.rcon(core, sink), "mspt").get(20, TimeUnit.SECONDS);
            out = sink.takeOutput();
            assertTrue(out.contains("전역: ") && out.contains("minecraft:overworld region: "), out);
            assertFalse(out.contains("전역: -"), "다 찬 칸에 전역 틱이 있다: " + out);
            // 권한 3 미만은 실행할 수 없다
            var low = new RconConsole(() -> false, spawn.x(), spawn.y(), spawn.z());
            core.commands().performPrefixedCommandAsync(CommandSource.rcon(core, low).withPermission(2), "tps").get(20, TimeUnit.SECONDS);
            assertFalse(low.takeOutput().contains("전역 TPS"));
        } finally {
            sched.close();
        }
    }
}
