// PR #201 독립 검토에서 나온 회귀 시험(R1 박동 몰아 돌기, R2 껐다 켜면 옛 작업 부활, R3 onEnable 안 setEnabled(false)). 기대값은 Paper 와 같은 동작.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.sched.GlobalThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A14PluginLifecycleReviewTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private Path dir;

    private static final PluginSettings DEV = new PluginSettings(false, null, Map.of(), true, false);

    @BeforeEach
    void setUp() {
        TestPlugins.LOG.clear();
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        dir = tmp.resolve("plugins");
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

    // ------------------------------------------------------------------ R1: 전역 스레드가 멈췄다 풀리면 박동이 밀린 틱을 한꺼번에 낸다

    /**
     * 전역 스레드가 3초 막혔다 풀리면 scheduleAtFixedRate 박동이 밀린 60번을 몰아 돌린다 — 1틱 반복 작업(Bukkit 박동 포함)이 수십 번 연달아 돈다.
     * Paper 는 스케줄러 박동이 tickServer 마다 한 번이라 서버 틱 루프가 "Can't keep up" 으로 건너뛴 틱은 스케줄러도 세지 않는다.
     */
    @Test
    void r1_stallMakesBeatBurst() throws Exception {
        GlobalThread g = new GlobalThread("review-r1");
        try {
            List<Long> at = new CopyOnWriteArrayList<>();
            var t = g.runAtFixedRate(() -> at.add(System.nanoTime()), 1, 1);
            Thread.sleep(500);
            long tick0 = g.schedulerTick();
            g.execute(() -> {
                try {
                    Thread.sleep(3_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).get(10, TimeUnit.SECONDS);
            long released = System.nanoTime();
            Thread.sleep(300);
            t.cancel();
            long burst = at.stream().filter(x -> x >= released && x < released + 100_000_000L).count();
            System.out.println("[R1] 3초 막힌 뒤 100ms 안에 돈 1틱 반복 " + burst + "번, 스케줄러 틱 " + tick0 + " -> " + g.schedulerTick());
            assertTrue(burst <= 5, "막힘이 풀린 뒤 100ms 안에 1틱 반복이 " + burst + "번 돌았다(초당 20 이면 2번 안팎)");
        } finally {
            g.close();
        }
    }

    // ------------------------------------------------------------------ R2: 껐다 다시 켜면 꺼지기 전에 건 작업이 되살아난다

    @Test
    void r2_rawSchedulerTaskResurrectsOnReEnable() throws Exception {
        jar("""
                int id = ++N;
                server().scheduler().global().runAtFixedRate(() -> org.domi.furnace.server.plugin.TestPlugins.LOG.add("tick:" + id), 1, 1);
                """);
        start();
        awaitLog("tick:1");
        FurnacePlugin p = host.plugins().plugin("Hot");
        host.plugins().disable(p);
        assertFalse(p.isEnabled());
        Thread.sleep(20);   // 다음 차례가 오기 전에 다시 켠다(/plugin 명령·다른 플러그인의 enable)
        host.plugins().enable(p);
        awaitLog("tick:2");
        TestPlugins.LOG.clear();
        Thread.sleep(400);
        assertFalse(TestPlugins.LOG.contains("tick:1"),
                "꺼지기 전에 server().scheduler() 로 건 반복 작업은 다시 켠 뒤에도 돌면 안 된다(Paper 는 disablePlugin 때 취소). 기록 일부: "
                        + TestPlugins.LOG.subList(0, Math.min(8, TestPlugins.LOG.size())));
    }

    @Test
    void r2b_rawDelayedOneShotRunsAfterReEnable() throws Exception {
        jar("""
                int id = ++N;
                if (id == 1) server().scheduler().global().runLater(() -> org.domi.furnace.server.plugin.TestPlugins.LOG.add("late:" + id), 10);
                """);
        start();
        FurnacePlugin p = host.plugins().plugin("Hot");
        host.plugins().disable(p);
        host.plugins().enable(p);
        Thread.sleep(1_000);
        assertFalse(TestPlugins.LOG.contains("late:1"), "꺼지기 전에 건 한 번짜리 지연 작업이 다시 켠 뒤 돌았다");
    }

    // ------------------------------------------------------------------ R3: onEnable 안의 setEnabled(false) 는 관리자 정리를 건너뛴다

    @Test
    void r3_selfDisableInsideOnEnableSkipsManagerCleanup() throws Exception {
        assumeTrue(core.world() != null);
        jar("""
                server().worlds().get(0).spawnPolicy().addFilter(this, c -> true);
                setEnabled(false);   // 의존 플러그인이 없을 때 흔히 쓰는 모양
                """);
        start(false);
        assertFalse(host.plugins().isEnabled("Hot"));
        assertEquals(0, core.world().spawnPolicy().filterCount(),
                "onEnable 안에서 setEnabled(false) 로 꺼졌어도 관리자 정리(스폰 필터 등)가 돌아야 한다 — LIFECYCLE 이 스레드 단위라 로더 호출로 오인한다");
    }

    /** 대조군: 같은 자리에서 관리자 disable(this) 를 부르면 정리된다. */
    @Test
    void r3_control_managerDisableInsideOnEnableCleansUp() throws Exception {
        assumeTrue(core.world() != null);
        jar("""
                server().worlds().get(0).spawnPolicy().addFilter(this, c -> true);
                server().plugins().disable(this);
                """);
        start(false);
        assertFalse(host.plugins().isEnabled("Hot"));
        assertEquals(0, core.world().spawnPolicy().filterCount());
    }

    // ------------------------------------------------------------------ 도우미

    private void jar(String body) throws IOException {
        Map<String, String> src = new LinkedHashMap<>();
        src.put("hot/a/Main.java", """
                package hot.a;
                public final class Main extends org.domi.furnace.api.plugin.FurnacePlugin {
                    static int N;
                    @Override public void onEnable() {
                        %s
                    }
                }
                """.formatted(body));
        pluginJar("hot.jar", "name: Hot\nversion: '1'\nmain: hot.a.Main\n", src);
    }

    private void start() { start(true); }

    private void start(boolean expectEnabled) {
        host = new PluginHost(core, dir);
        host.pluginsImpl().settings(DEV);
        host.pluginsImpl().retireDelayTicks = 0;
        host.pluginsImpl().leakCheckMillis = -1;
        host.pluginsImpl().asyncWaitMillis = 50;
        host.pluginsImpl().loadAll(PluginBootstrapper.bootstrap(dir, DEV));
        host.pluginsImpl().enableAll();
        if (expectEnabled) assertTrue(host.plugins().isEnabled("Hot"), "Hot 이 켜져야 한다");
    }

    private static void awaitLog(String entry) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!TestPlugins.LOG.contains(entry)) {
            if (System.nanoTime() > end) fail("기록에 " + entry + " 이 오지 않았다");
            Thread.sleep(10);
        }
    }

    private static final java.util.concurrent.atomic.AtomicLong MTIME = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    private void pluginJar(String file, String descriptor, Map<String, String> sources) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assumeTrue(javac != null, "JDK 컴파일러가 없다");
        Path src = Files.createTempDirectory(tmp, "src");
        Path out = Files.createTempDirectory(tmp, "classes");
        List<String> args = new ArrayList<>(List.of("-d", out.toString(), "-cp", classpath(), "-encoding", "UTF-8", "-proc:none"));
        for (var e : sources.entrySet()) {
            Path f = src.resolve(e.getKey());
            Files.createDirectories(f.getParent());
            Files.writeString(f, e.getValue());
            args.add(f.toString());
        }
        assertEquals(0, javac.run(null, null, null, args.toArray(String[]::new)), "컴파일 실패");
        Path jar = dir.resolve(file);
        Files.createDirectories(dir);
        try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar)); Stream<Path> walk = Files.walk(out)) {
            jo.putNextEntry(new JarEntry("furnace-plugin.yml"));
            jo.write(descriptor.getBytes(StandardCharsets.UTF_8));
            jo.closeEntry();
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                jo.putNextEntry(new JarEntry(out.relativize(p).toString().replace('\\', '/')));
                jo.write(Files.readAllBytes(p));
                jo.closeEntry();
            }
        }
        Files.setLastModifiedTime(jar, FileTime.fromMillis(MTIME.addAndGet(5_000)));
    }

    private static String classpath() {
        List<String> parts = new ArrayList<>();
        for (Class<?> c : new Class<?>[]{FurnacePlugin.class, TestPlugins.class}) {
            try {
                parts.add(Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }
        return String.join(java.io.File.pathSeparator, parts);
    }
}
