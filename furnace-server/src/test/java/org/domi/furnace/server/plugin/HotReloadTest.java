// B8: 개발 모드 핫 리로드. 플러그인 클래스는 시험 중에 컴파일해(서버 클래스패스에 없게) jar 로 만든다 —
// 그래야 다시 읽은 뒤 정말 새 클래스가 돌고 옛 클래스로더가 풀리는지 볼 수 있다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.domi.furnace.api.event.server.ServerCommandEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.builtin.FurnaceCommands;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HotReloadTest {

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

    // ------------------------------------------------------------------ 플러그인 만들기

    /** Hot 플러그인: Api.version() 이 ver 를 돌려주고, 켜질 때 명령 hotver·명령 이벤트 구독을 단다. */
    private static Map<String, String> hotSources(String ver) {
        return Map.of(
                "hot/a/Api.java", "package hot.a; public final class Api { public static String version() { return \"" + ver + "\"; } }",
                "hot/a/Main.java", """
                        package hot.a;
                        import org.domi.furnace.server.plugin.TestPlugins;
                        public final class Main extends org.domi.furnace.api.plugin.FurnacePlugin {
                            @Override public void onEnable() {
                                TestPlugins.LOG.add("hot:enable:" + Api.version());
                                server().commands().register(this, "hotver", (s, a) -> TestPlugins.LOG.add("hot:cmd:" + Api.version()));
                                server().events().subscribe(this, org.domi.furnace.api.event.server.ServerCommandEvent.class,
                                        e -> TestPlugins.LOG.add("hot:event:" + Api.version()));
                            }
                            @Override public void onDisable() { TestPlugins.LOG.add("hot:disable:" + Api.version()); }
                        }
                        """);
    }

    /** Hot 의 Api 클래스를 쓰는 플러그인(name). depend 를 적을지 고른다. */
    private static Map<String, String> userSources(String pkg) {
        return Map.of(pkg + "/Main.java", """
                package %s;
                import org.domi.furnace.server.plugin.TestPlugins;
                public final class Main extends org.domi.furnace.api.plugin.FurnacePlugin {
                    @Override public void onEnable() { TestPlugins.LOG.add("%s:sees:" + hot.a.Api.version()); }
                    @Override public void onDisable() { TestPlugins.LOG.add("%s:disable"); }
                }
                """.formatted(pkg, pkg, pkg));
    }

    private Path hotJar(String ver) throws IOException {
        return pluginJar("hot.jar", "name: Hot\nversion: '" + ver + "'\nmain: hot.a.Main\n", hotSources(ver), List.of());
    }

    /** 소스를 컴파일해 설명 파일과 함께 jar 로 묶는다. extraClasspath 는 컴파일에만 쓴다(다른 플러그인의 클래스). */
    private Path pluginJar(String file, String descriptor, Map<String, String> sources, List<Path> extraClasspath) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assumeTrue(javac != null, "JDK 컴파일러가 없다");
        Path src = Files.createTempDirectory(tmp, "src");
        Path out = Files.createTempDirectory(tmp, "classes");
        List<String> args = new ArrayList<>(List.of("-d", out.toString(), "-cp", classpath(extraClasspath), "-encoding", "UTF-8", "-proc:none"));
        for (var e : sources.entrySet()) {
            Path f = src.resolve(e.getKey());
            Files.createDirectories(f.getParent());
            Files.writeString(f, e.getValue());
            args.add(f.toString());
        }
        int rc = javac.run(null, null, null, args.toArray(String[]::new));
        assertEquals(0, rc, "컴파일 실패");
        Path jar = dir.resolve(file);
        Files.createDirectories(dir);
        Path staging = tmp.resolve(file + ".tmp");
        try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(staging)); Stream<Path> walk = Files.walk(out)) {
            jo.putNextEntry(new JarEntry("furnace-plugin.yml"));
            jo.write(descriptor.getBytes(StandardCharsets.UTF_8));
            jo.closeEntry();
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                jo.putNextEntry(new JarEntry(out.relativize(p).toString().replace('\\', '/')));
                jo.write(Files.readAllBytes(p));
                jo.closeEntry();
            }
        }
        // 제자리 덮어쓰기(개발자가 빌드 결과를 plugins/ 에 복사하는 것과 같다)
        Files.write(jar, Files.readAllBytes(staging));
        bumpMtime(jar);
        return jar;
    }

    private static final java.util.concurrent.atomic.AtomicLong MTIME = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    /** 쓸 때마다 다른 수정 시각(파일 시스템 시각 해상도와 무관하게 감시가 변화를 보게). */
    private static void bumpMtime(Path jar) throws IOException {
        Files.setLastModifiedTime(jar, FileTime.fromMillis(MTIME.addAndGet(5_000)));
    }

    private static String classpath(List<Path> extra) {
        List<String> parts = new ArrayList<>();
        for (Class<?> c : new Class<?>[]{FurnacePlugin.class, TestPlugins.class}) {
            try {
                parts.add(Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }
        for (Path p : extra) parts.add(p.toString());
        return String.join(java.io.File.pathSeparator, parts);
    }

    private PluginHost start(PluginSettings s) {
        host = new PluginHost(core, dir);
        host.pluginsImpl().settings(s);
        host.pluginsImpl().retireDelayTicks = 0;
        host.pluginsImpl().leakCheckMillis = -1;
        host.pluginsImpl().loadAll(PluginBootstrapper.bootstrap(dir, s));
        host.pluginsImpl().enableAll();
        return host;
    }

    private List<String> reload(String name) throws Exception {
        return host.plugins().reload(name).get(10, TimeUnit.SECONDS);
    }

    private static void awaitLog(String entry) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!TestPlugins.LOG.contains(entry)) {
            if (System.nanoTime() > end) fail("기록에 " + entry + " 이 오지 않았다: " + TestPlugins.LOG);
            Thread.sleep(10);
        }
    }

    private List<String> eventsFired() {
        List<String> out = new ArrayList<>();
        for (String s : TestPlugins.LOG) if (s.startsWith("hot:event:")) out.add(s);
        return out;
    }

    // ------------------------------------------------------------------ 시험

    @Test
    void offByDefault() throws Exception {
        hotJar("v1");
        start(PluginSettings.DEFAULT);
        assertFalse(host.plugins().devMode());
        var f = host.plugins().reload("Hot");
        ExecutionException e = assertThrows(ExecutionException.class, () -> f.get(5, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertFalse(Files.exists(dir.resolve(HotReload.SHADOW_DIR)), "개발 모드가 아니면 jar 사본을 만들지 않는다");
        // 명령: 개발 모드가 아니면 /plugins reload 가 콘솔에도 보이지 않는다
        FurnaceCommands.register(core.commands().getDispatcher(), host);
        var reloadNode = core.commands().getDispatcher().getRoot().getChild("plugins").getChild("reload");
        assertFalse(reloadNode.canUse(CommandSource.console(core)));
    }

    @Test
    void reloadSwapsClassesAndCleansUp() throws Exception {
        hotJar("v1");
        start(DEV);
        FurnacePlugin before = host.plugins().plugin("Hot");
        assertTrue(before.isEnabled());
        assertTrue(TestPlugins.LOG.contains("hot:enable:v1"));
        ClassLoader oldLoader = before.getClass().getClassLoader();
        assertNotEquals(PluginHost.class.getClassLoader(), oldLoader, "플러그인 클래스는 jar 에서 읽혀야 한다");
        Path oldShadow = ((PluginManagerImpl.PluginClassLoader) oldLoader).shadow();
        assertTrue(Files.exists(oldShadow), "개발 모드는 jar 사본을 읽는다");

        hotJar("v2");   // 켜져 있는 동안 원본 jar 를 덮어쓴다
        assertEquals(List.of("Hot"), reload("Hot"));

        FurnacePlugin after = host.plugins().plugin("Hot");
        assertNotSame(before, after);
        assertTrue(after.isEnabled());
        assertFalse(before.isEnabled());
        assertNotSame(oldLoader, after.getClass().getClassLoader());
        assertEquals("v2", after.description().version());
        int dis = TestPlugins.LOG.indexOf("hot:disable:v1"), en = TestPlugins.LOG.indexOf("hot:enable:v2");
        assertTrue(dis >= 0 && en > dis, "옛 것을 끈 뒤 새 것을 켠다: " + TestPlugins.LOG);
        assertEquals(1, host.plugins().plugins().size());
        // 옛 구독은 풀리고 새 구독 하나만 남는다
        host.eventsImpl().call(new ServerCommandEvent(host.console(), "x"));
        assertEquals(List.of("hot:event:v2"), eventsFired());
        // 명령은 새 코드로 다시 등록된다
        assertNotNull(core.commands().getDispatcher().getRoot().getChild("hotver"));
        host.dispatchCommand(host.console(), "hotver");
        awaitLog("hot:cmd:v2");
        assertFalse(TestPlugins.LOG.contains("hot:cmd:v1"));
        // 옛 클래스로더는 닫히고 사본은 지워진다
        assertFalse(Files.exists(oldShadow));
        assertNull(((PluginManagerImpl.PluginClassLoader) oldLoader).findResource("hot/a/Api.class"), "닫힌 로더");
    }

    @Test
    void oldClassLoaderIsCollectable() throws Exception {
        hotJar("v1");
        start(DEV);
        WeakReference<ClassLoader> ref = new WeakReference<>(host.plugins().plugin("Hot").getClass().getClassLoader());
        hotJar("v2");
        reload("Hot");
        host.eventsImpl().call(new ServerCommandEvent(host.console(), "x"));
        TestPlugins.LOG.clear();
        for (int i = 0; i < 20 && ref.get() != null; i++) {
            System.gc();
            Thread.sleep(50);
        }
        assertNull(ref.get(), "서버 쪽에 옛 플러그인 클래스를 붙잡는 참조가 남으면 안 된다");
    }

    @Test
    void dependentsAreReloadedTooButOthersAreNot() throws Exception {
        Path hot = hotJar("v1");
        // Dep 은 depend 로 적었고, Zed 는 적지 않고 클래스만 가져다 쓴다(이름 순으로 Hot 뒤에 켜진다), Gamma 는 관계없다
        pluginJar("dep.jar", "name: Dep\nversion: 1\nmain: dep.Main\ndepend: [Hot]\n", userSources("dep"), List.of(hot));
        pluginJar("zed.jar", "name: Zed\nversion: 1\nmain: zed.Main\n", userSources("zed"), List.of(hot));
        PluginSystemTest.jar(dir.resolve("gamma.jar"), "furnace-plugin.yml",
                "name: Gamma\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Gamma\n", TestPlugins.Gamma.class);
        start(DEV);
        assertTrue(TestPlugins.LOG.containsAll(List.of("dep:sees:v1", "zed:sees:v1")), TestPlugins.LOG.toString());
        FurnacePlugin gamma = host.plugins().plugin("Gamma");

        hotJar("v2");
        List<String> back = reload("hot");
        assertEquals(List.of("Dep", "Hot", "Zed").stream().sorted().toList(), back.stream().sorted().toList());
        assertTrue(TestPlugins.LOG.containsAll(List.of("dep:sees:v2", "zed:sees:v2")), "기대는 플러그인도 새 클래스를 본다: " + TestPlugins.LOG);
        assertTrue(TestPlugins.LOG.indexOf("dep:disable") < TestPlugins.LOG.indexOf("hot:disable:v1"), "기대는 쪽을 먼저 끈다");
        assertTrue(TestPlugins.LOG.indexOf("hot:enable:v2") < TestPlugins.LOG.indexOf("dep:sees:v2"), "켜기는 의존 순서");
        assertSame(gamma, host.plugins().plugin("Gamma"), "관계없는 플러그인은 그대로");
        assertTrue(gamma.isEnabled());
    }

    @Test
    void newJarLoadsAndRemovedJarUnloads() throws Exception {
        start(DEV);
        assertNull(host.plugins().plugin("Hot"));
        Path jar = hotJar("v1");
        assertEquals(List.of("Hot"), reload("Hot"), "불러오지 않은 이름이라도 jar 가 있으면 새로 불러온다");
        assertTrue(host.plugins().isEnabled("Hot"));
        Files.delete(jar);
        assertEquals(List.of(), reload("Hot"));
        assertNull(host.plugins().plugin("Hot"), "jar 가 없어졌으면 내리기만 한다");
        assertTrue(TestPlugins.LOG.contains("hot:disable:v1"));
        var e = assertThrows(ExecutionException.class, () -> reload("Nope"));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
    }

    @Test
    void brokenJarKeepsTheRunningPlugin() throws Exception {
        Path jar = hotJar("v1");
        start(DEV);
        FurnacePlugin before = host.plugins().plugin("Hot");
        Files.write(jar, new byte[]{1, 2, 3});   // 복사 도중 같은 깨진 jar
        var e = assertThrows(ExecutionException.class, () -> reload("Hot"));
        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertSame(before, host.plugins().plugin("Hot"));
        assertTrue(before.isEnabled());
        assertFalse(TestPlugins.LOG.contains("hot:disable:v1"));
    }

    @Test
    void consoleCommandReloadsInDevMode() throws Exception {
        hotJar("v1");
        start(DEV);
        FurnaceCommands.register(core.commands().getDispatcher(), host);
        List<String> console = new CopyOnWriteArrayList<>();
        core.addConsoleListener(t -> console.add(t.getString()));
        hotJar("v2");
        core.commands().performPrefixedCommand(CommandSource.console(core), "plugins reload Hot");
        awaitLog("hot:enable:v2");
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (console.stream().noneMatch(s -> s.contains("다시 읽음: Hot")) && System.nanoTime() < end) Thread.sleep(10);
        assertTrue(console.stream().anyMatch(s -> s.contains("다시 읽음: Hot")), console.toString());
    }

    @Test
    void watcherReloadsChangedJarOnceWritesSettle() throws Exception {
        hotJar("v1");
        start(new PluginSettings(false, null, Map.of(), true, true));
        host.pluginsImpl().startWatcher(3_600_000);   // 시험은 poll() 을 직접 부른다
        HotReload.Watcher w = host.pluginsImpl().watcher();
        assertNotNull(w);
        w.poll();
        assertFalse(TestPlugins.LOG.contains("hot:disable:v1"), "바뀐 것이 없으면 아무것도 안 한다");
        hotJar("v2");
        w.poll();   // 처음 본 변화 — 쓰기가 멈췄는지 한 번 더 본다
        Thread.sleep(200);
        assertFalse(TestPlugins.LOG.contains("hot:enable:v2"));
        w.poll();
        awaitLog("hot:enable:v2");
        w.poll();
        Thread.sleep(200);
        assertEquals(1, TestPlugins.LOG.stream().filter("hot:enable:v2"::equals).count(), "한 번만 다시 읽는다");
    }

    @Test
    void settingsParse() {
        Properties pr = new Properties();
        assertFalse(PluginSettings.from(pr, org.slf4j.LoggerFactory.getLogger("Furnace")).devMode());
        pr.setProperty("plugins.dev-mode.auto-reload", "true");
        PluginSettings s = PluginSettings.from(pr, org.slf4j.LoggerFactory.getLogger("Furnace"));
        assertFalse(s.autoReload(), "개발 모드 없이는 자동 다시 읽기도 없다");
        pr.setProperty("plugins.dev-mode", "true");
        s = PluginSettings.from(pr, org.slf4j.LoggerFactory.getLogger("Furnace"));
        assertTrue(s.devMode());
        assertTrue(s.autoReload());
    }
}
