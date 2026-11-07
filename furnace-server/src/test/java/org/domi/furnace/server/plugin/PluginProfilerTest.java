// 플러그인별 시간 프로파일러(/timings): 꺼짐 상태의 무기록, 겹친 구간의 자기 시간, 여러 스레드 동시 기록, 리스너·명령·작업 귀속,
// 클래스로더로 찾는 귀속(플러그인 jar 에만 있는 클래스 — 시험 안에서 컴파일한다)과 스레드 종류(region·전역·그 밖), 보고 서식.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.profiler.PluginProfiler;
import org.domi.furnace.api.profiler.PluginProfiler.Kind;
import org.domi.furnace.api.profiler.PluginProfiler.PluginStats;
import org.domi.furnace.api.profiler.PluginProfiler.Report;
import org.domi.furnace.api.profiler.PluginProfiler.SectionStats;
import org.domi.furnace.api.profiler.Timing;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.builtin.TimingsCommands;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginProfilerTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;

    @BeforeEach
    void setUp() {
        TestPlugins.LOG.clear();
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
    }

    @AfterEach
    void tearDown() {
        if (host != null) {
            host.pluginsImpl().disableAll();
            host.pluginsImpl().closeAll();
            host.close();
        }
        sched.close();
    }

    static class Ping extends Event {}

    private static void spin(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) Thread.onSpinWait();
    }

    /** TestPlugins 의 Gamma(아무것도 안 하는 플러그인)와 Delta 를 켠 호스트. */
    private PluginHost hostWith(String... names) throws IOException {
        Path dir = tmp.resolve("plugins");
        for (String n : names) {
            PluginSystemTest.jar(dir.resolve(n + ".jar"), "furnace-plugin.yml", "name: " + n + "\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Gamma\n",
                    TestPlugins.Gamma.class);
        }
        host = new PluginHost(core, dir);
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        return host;
    }

    private static PluginStats plugin(Report r, String name) {
        return r.plugins().stream().filter(p -> p.plugin().equals(name)).findFirst().orElse(null);
    }

    private static SectionStats section(PluginStats p, Kind kind, String nameStart) {
        if (p == null) return null;
        return p.sections().stream().filter(s -> s.kind() == kind && s.name().startsWith(nameStart)).findFirst().orElse(null);
    }

    @Test
    void offRecordsNothingAndOnRecords() throws Exception {
        hostWith("Gamma");
        FurnacePlugin g = host.plugins().plugin("Gamma");
        PluginProfilerImpl prof = host.profiler();
        assertFalse(prof.isRunning(), "기본은 꺼짐");
        Timing t = prof.timing(g, Kind.CUSTOM, "x");
        assertSame(t, prof.timing(g, Kind.CUSTOM, "x"), "같은 열쇠면 같은 구간");
        long tok = t.start();
        assertTrue(tok < 0, "꺼져 있으면 음수 토큰");
        t.stop(tok);
        assertTrue(prof.report().plugins().isEmpty(), "꺼져 있을 때는 아무것도 모으지 않는다");

        prof.start();
        tok = t.start();
        assertTrue(tok >= 0);
        spin(1_000_000);
        t.stop(tok);
        prof.stop();
        assertFalse(prof.isRunning());
        tok = t.start();
        t.stop(tok);
        Report r = prof.report();
        assertFalse(r.running());
        assertTrue(r.measuredNanos() > 0);
        SectionStats s = section(plugin(r, "Gamma"), Kind.CUSTOM, "x");
        assertNotNull(s);
        assertEquals(1, s.calls(), "끈 뒤의 호출은 세지 않는다");
        assertTrue(s.selfNanos() >= 1_000_000 && s.maxNanos() >= 1_000_000);

        prof.reset();
        assertTrue(prof.report().plugins().isEmpty(), "reset 은 값을 지운다");
        assertSame(t, prof.timing(g, Kind.CUSTOM, "x"), "켜진 플러그인의 구간 손잡이는 reset 뒤에도 그대로 쓰인다");
    }

    @Test
    void nestedSectionsSplitSelfTimeWithoutDoubleCounting() throws Exception {
        hostWith("Gamma", "Delta");
        FurnacePlugin a = host.plugins().plugin("Gamma");
        FurnacePlugin b = host.plugins().plugin("Delta");
        PluginProfilerImpl prof = host.profiler();
        prof.start();
        // Gamma 의 리스너가 이벤트를 발행하고 그 이벤트를 Delta 가 받는다
        var bus = host.eventsImpl();
        bus.subscribe(b, Ping.class, EventPriority.NORMAL, false, e -> spin(3_000_000));
        Timing outer = prof.timing(a, Kind.CUSTOM, "outer");
        long tok = outer.start();
        spin(2_000_000);
        bus.call(new Ping());
        outer.stop(tok);

        Report r = prof.report();
        SectionStats o = section(plugin(r, "Gamma"), Kind.CUSTOM, "outer");
        SectionStats l = section(plugin(r, "Delta"), Kind.LISTENER, "Ping ");
        assertNotNull(o);
        assertNotNull(l, "리스너는 '이벤트 이름 + 리스너' 구간으로 Delta 몫");
        assertEquals(o.totalNanos(), o.selfNanos() + l.totalNanos(), "바깥 자기 시간 = 바깥 전체 - 안쪽 전체");
        assertTrue(o.selfNanos() >= 2_000_000 && o.selfNanos() < o.totalNanos());
        assertTrue(l.selfNanos() >= 3_000_000);
        long sum = r.plugins().stream().mapToLong(PluginStats::selfNanos).sum();
        assertEquals(o.totalNanos(), sum, "플러그인 합계를 더하면 바깥 전체 시간과 같다(두 번 세지 않음)");
    }

    @Test
    void concurrentRecordingFromManyThreadsIsExact() throws Exception {
        hostWith("Gamma");
        FurnacePlugin g = host.plugins().plugin("Gamma");
        PluginProfilerImpl prof = host.profiler();
        prof.start();
        Timing t = prof.timing(g, Kind.TASK, "shared");
        int threads = 8, per = 20_000;
        CountDownLatch go = new CountDownLatch(1);
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int k = 0; k < per; k++) {
                    long tok = t.start();
                    t.stop(tok);
                }
            });
            ts[i].start();
        }
        go.countDown();
        for (Thread x : ts) x.join();
        PluginStats p = plugin(prof.report(), "Gamma");
        assertEquals((long) threads * per, section(p, Kind.TASK, "shared").calls(), "LongAdder 라 잃는 호출이 없다");
        assertEquals(p.selfNanos(), p.otherNanos(), "일반 스레드는 '그 밖'");
    }

    @Test
    void attributesTasksCommandsAndThreadKindsThroughPluginApi() throws Exception {
        hostWith("Gamma");
        FurnacePlugin g = host.plugins().plugin("Gamma");
        PluginProfilerImpl prof = host.profiler();
        prof.start();
        // PluginTasks: 전역·region·비동기
        CountDownLatch done = new CountDownLatch(3);
        g.tasks().runGlobal(() -> { spin(200_000); done.countDown(); }, 0);
        g.tasks().runRegion(new RegionPos(0, 0), () -> { spin(200_000); done.countDown(); }, 0);
        g.tasks().runAsync(() -> { spin(200_000); done.countDown(); });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        // 명령
        host.commands().register(g, "slowcmd", (sender, args) -> spin(500_000));
        core.runConsoleCommand("slowcmd a b");
        core.scheduler().global().submit(() -> null).join();
        // 작업 실행 뒤 stop 이 기록되기까지 기다린다(카운트다운은 작업 안에서 일어난다)
        PluginStats p = null;
        for (int i = 0; i < 200; i++) {
            p = plugin(prof.report(), "Gamma");
            if (p != null && p.regionNanos() > 0 && p.globalNanos() > 0 && p.otherNanos() > 0 && section(p, Kind.COMMAND, "/slowcmd") != null) break;
            Thread.sleep(20);
        }
        assertNotNull(p);
        assertTrue(p.regionNanos() >= 200_000, "region 작업은 region 몫: " + p);
        assertTrue(p.globalNanos() >= 200_000 + 500_000, "전역 작업·명령은 전역 몫: " + p);
        assertTrue(p.otherNanos() >= 200_000, "비동기는 그 밖: " + p);
        assertNotNull(section(p, Kind.ASYNC, ""), "runAsync 는 ASYNC 구간");
        assertEquals(1, section(p, Kind.COMMAND, "/slowcmd").calls(), "명령 한 번(이름 공간 붙은 이름과 같은 줄)");
        assertTrue(p.sections().stream().filter(s -> s.kind() == Kind.TASK).mapToLong(SectionStats::calls).sum() >= 2);
    }

    @Test
    void classLoaderAttributionCoversDirectSchedulerAndExecutorUse() throws Exception {
        Path dir = tmp.resolve("plugins");
        Files.createDirectories(dir);
        compileProbePlugin(dir.resolve("probe.jar"));
        host = new PluginHost(core, dir);
        host.profiler().start();   // 수명 함수부터 잰다
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        FurnacePlugin probe = host.plugins().plugin("Probe");
        assertNotNull(probe, "시험 안에서 컴파일한 플러그인이 켜졌다");
        assertSame(probe, PluginManagerImpl.pluginOf(probe.getClass()), "플러그인 jar 클래스는 그 플러그인 것");
        assertNull(PluginManagerImpl.pluginOf(PluginProfilerTest.class), "서버·시험 클래스는 아무 플러그인 것도 아니다");

        PluginStats p = null;
        for (int i = 0; i < 250; i++) {
            p = plugin(host.profiler().report(), "Probe");
            if (p != null && p.globalNanos() > 0 && p.regionNanos() > 0 && p.otherNanos() > 0
                    && p.sections().stream().filter(s -> s.kind() == Kind.TASK).count() >= 2
                    && section(p, Kind.ASYNC, "ProbePlugin") != null) break;   // 비동기 람다 기록까지 기다린다(부하 때 경합)
            Thread.sleep(20);
        }
        assertNotNull(p);
        assertNotNull(section(p, Kind.LIFECYCLE, "onEnable"));
        assertNotNull(section(p, Kind.LIFECYCLE, "onLoad"));
        assertTrue(p.sections().stream().anyMatch(s -> s.kind() == Kind.TASK && s.name().startsWith("ProbePlugin")),
                "server().scheduler() 로 바로 예약한 람다도 Probe 몫: " + p.sections());
        assertNotNull(section(p, Kind.ASYNC, "ProbePlugin"), "asyncExecutor() 에 바로 넣은 람다도 Probe 몫: " + p.sections());
        assertTrue(p.globalNanos() > 0 && p.regionNanos() > 0 && p.otherNanos() > 0, p.toString());
    }

    /** server().scheduler() 와 asyncExecutor() 를 PluginTasks 없이 바로 쓰는 플러그인을 컴파일해 jar 로 만든다. */
    private void compileProbePlugin(Path jar) throws IOException {
        Path src = tmp.resolve("probe-src/probe/ProbePlugin.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, """
                package probe;
                import org.domi.furnace.api.plugin.FurnacePlugin;
                import org.domi.furnace.api.sched.RegionPos;
                public class ProbePlugin extends FurnacePlugin {
                    static void spin() { long e = System.nanoTime() + 300_000; while (System.nanoTime() < e) Thread.onSpinWait(); }
                    @Override public void onEnable() {
                        server().scheduler().global().runAtFixedRate(() -> spin(), 0, 1);
                        server().scheduler().region(new RegionPos(0, 0)).runAtFixedRate(() -> spin(), 0, 1);
                        server().asyncExecutor().execute(() -> spin());
                    }
                }
                """, StandardCharsets.UTF_8);
        Path out = tmp.resolve("probe-classes");
        Files.createDirectories(out);
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "JDK 컴파일러가 필요하다");
        int rc = javac.run(null, null, null, "-classpath", System.getProperty("java.class.path"), "-d", out.toString(), src.toString());
        assertEquals(0, rc, "probe 컴파일");
        try (JarOutputStream o = new JarOutputStream(Files.newOutputStream(jar)); Stream<Path> files = Files.walk(out)) {
            o.putNextEntry(new JarEntry("furnace-plugin.yml"));
            o.write("name: Probe\nversion: 1\nmain: probe.ProbePlugin\n".getBytes(StandardCharsets.UTF_8));
            o.closeEntry();
            for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                o.putNextEntry(new JarEntry(out.relativize(f).toString().replace('\\', '/')));
                o.write(Files.readAllBytes(f));
                o.closeEntry();
            }
        }
    }

    @Test
    void reportFormatting() {
        var sec = new SectionStats(Kind.LISTENER, "PlayerMoveEvent a.B.on", 4, 120_000_000L, 100_000_000L, 60_000_000L, 1);
        var p = new PluginStats("Alpha", 100_000_000L, 4, 90_000_000L, 10_000_000L, 0, List.of(sec));
        var r = new Report(10_000_000_000L, true, 50_000_000L, List.of(p));
        List<String> lines = TimingsCommands.summary(r).stream().map(t -> t.getString()).toList();
        assertTrue(lines.get(0).contains("측정 10.0초") && lines.get(0).contains("측정 중"), lines.get(0));
        // 10초 = 틱 200개 → 100ms / 200 = 0.5ms
        assertEquals("1. Alpha: 100.00 ms (틱당 0.500 ms), 호출 4 — region 90.00 / 전역 10.00 / 그 밖 0.00", lines.get(1));
        assertEquals("   리스너 PlayerMoveEvent a.B.on: 호출 4, 자기 100.00 ms (안쪽 포함 120.00), 평균 25000.0 µs, 최대 60.00 ms, 50ms 넘음 1번",
                lines.get(2));
        assertEquals(lines.subList(1, 3), TimingsCommands.detail(r, "alpha").stream().map(t -> t.getString()).toList().subList(1, 3), "이름은 대소문자 무시");
        assertTrue(TimingsCommands.detail(r, "none").get(1).getString().contains("기록이 없다"));
        assertTrue(TimingsCommands.summary(new Report(0, false, 50_000_000L, List.of())).get(1).getString().contains("/timings on"));
        assertEquals(3, TimingsCommands.fullText(r).size() - 1, "머리 + 빈 줄 + 플러그인 + 구간");
    }

    @Test
    void timingsCommandTogglesProfiler() throws Exception {
        hostWith("Gamma");
        PluginProfiler prof = core.plugins().profiler();   // 명령은 core 의 호스트 프로파일러를 쓴다
        TimingsCommands.register(core.commands().getDispatcher());
        core.runConsoleCommand("timings on");
        core.scheduler().global().submit(() -> null).join();
        assertTrue(prof.isRunning());
        core.runConsoleCommand("timings off");
        core.scheduler().global().submit(() -> null).join();
        assertFalse(prof.isRunning());
    }
}
