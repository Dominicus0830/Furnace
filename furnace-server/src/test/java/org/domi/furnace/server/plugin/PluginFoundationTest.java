// B1: 이벤트 비동기 표시·발화 계약, LambdaMetafactory 디스패치, MONITOR 경고, 자원 자동 정리, fail-fast, 폴더 분리, apiVersion, 로거 수준, 조기 콘솔.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import org.domi.furnace.api.ApiVersion;
import org.domi.furnace.api.DeprecatedApi;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.Listener;
import org.domi.furnace.api.event.Subscribe;
import org.domi.furnace.server.ConsoleInputTestSupport;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginFoundationTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private ListAppender<ILoggingEvent> logs;
    private ch.qos.logback.classic.Logger furnaceLog;

    static class Ping extends Event implements Cancellable {
        private boolean cancelled;
        Ping() { super(); }
        public boolean isCancelled() { return cancelled; }
        public void setCancelled(boolean c) { cancelled = c; }
    }

    static final class AsyncPing extends Event {
        AsyncPing() { super(true); }
    }

    @DeprecatedApi(since = "1.0", removeIn = "1.2", replacement = "Ping")
    static final class OldPing extends Event {}

    @BeforeEach
    void setUp() {
        TestPlugins.LOG.clear();
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        furnaceLog = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs = new ListAppender<>();
        logs.start();
        furnaceLog.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        furnaceLog.detachAppender(logs);
        if (host != null) {
            host.pluginsImpl().disableAll();
            host.pluginsImpl().closeAll();
            host.close();
        }
        sched.close();
    }

    private List<String> messages() {
        List<String> out = new ArrayList<>();
        for (ILoggingEvent e : logs.list) out.add(e.getFormattedMessage());
        return out;
    }

    private PluginHost host(Path dir, PluginSettings s) {
        host = new PluginHost(core, dir);
        host.pluginsImpl().settings(s);
        return host;
    }

    private void gamma(Path dir, String extra) throws Exception {
        PluginSystemTest.jar(dir.resolve("g.jar"), "furnace-plugin.yml",
                "name: Gamma\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Gamma\n" + extra, TestPlugins.Gamma.class);
    }

    // ---- 이벤트 ----

    @Test
    void isAsyncFlag() {
        assertFalse(new Ping().isAsync());
        assertTrue(new AsyncPing().isAsync());
    }

    /** 리스너 클래스 — 비공개 메서드·상위 클래스 메서드도 LambdaMetafactory 경로로 불린다. */
    static class Base implements Listener {
        final List<String> seen = new ArrayList<>();
        @Subscribe private void priv(Ping e) { seen.add("priv"); }
        @Subscribe public void checked(Ping e) throws Exception { seen.add("checked"); if (e.isCancelled()) throw new Exception("x"); }
    }

    static final class Child extends Base {
        @Subscribe(priority = EventPriority.LOW) void pkg(Ping e) { seen.add("pkg"); }
    }

    @Test
    void dispatchHandlesPrivateInheritedAndCheckedThrowingMethods() throws Exception {
        Path dir = tmp.resolve("plugins");
        gamma(dir, "");
        host(dir, PluginSettings.DEFAULT);
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        var p = host.plugins().plugin("Gamma");
        Child l = new Child();
        host.eventsImpl().register(p, l);
        host.eventsImpl().call(new Ping());
        assertEquals(3, l.seen.size());
        assertEquals("pkg", l.seen.get(0), "LOW 가 먼저");
        // 검사 예외를 던져도 다음 처리기로 계속된다(로그만)
        Ping c = new Ping();
        c.setCancelled(true);
        host.eventsImpl().call(c);
        assertTrue(messages().stream().anyMatch(m -> m.startsWith("Could not pass event Ping")));
    }

    @Test
    void monitorChangingCancelStateWarnsOnce() throws Exception {
        Path dir = tmp.resolve("plugins");
        gamma(dir, "");
        host(dir, PluginSettings.DEFAULT);
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        var p = host.plugins().plugin("Gamma");
        host.eventsImpl().subscribe(p, Ping.class, EventPriority.MONITOR, false, e -> e.setCancelled(true));
        host.eventsImpl().subscribe(p, Ping.class, EventPriority.NORMAL, false, e -> { });
        host.eventsImpl().call(new Ping());
        host.eventsImpl().call(new Ping());
        assertEquals(1, messages().stream().filter(m -> m.contains("MONITOR")).count());
    }

    @Test
    void monitorThatOnlyObservesDoesNotWarn() throws Exception {
        Path dir = tmp.resolve("plugins");
        gamma(dir, "");
        host(dir, PluginSettings.DEFAULT);
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        var p = host.plugins().plugin("Gamma");
        host.eventsImpl().subscribe(p, Ping.class, EventPriority.MONITOR, false, e -> { });
        host.eventsImpl().call(new Ping());
        assertTrue(messages().stream().noneMatch(m -> m.contains("MONITOR")));
    }

    @Test
    void deprecatedEventWarnsOnSubscribe() throws Exception {
        Path dir = tmp.resolve("plugins");
        gamma(dir, "");
        host(dir, PluginSettings.DEFAULT);
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        var p = host.plugins().plugin("Gamma");
        host.eventsImpl().subscribe(p, OldPing.class, e -> { });
        host.eventsImpl().subscribe(p, OldPing.class, e -> { });
        assertEquals(1, messages().stream().filter(m -> m.contains("폐기된 이벤트 OldPing")).count());
    }

    // ---- 자원 자동 정리 ----

    @Test
    void ownedResourcesCloseOnDisableInReverseOrder() throws Exception {
        Path dir = tmp.resolve("plugins");
        gamma(dir, "");
        host(dir, PluginSettings.DEFAULT);
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        var p = host.plugins().plugin("Gamma");
        List<String> closed = new ArrayList<>();
        p.own(() -> closed.add("a"));
        p.own(() -> { throw new IllegalStateException("닫기 실패"); });
        p.own(() -> closed.add("c"));
        host.eventsImpl().subscribe(p, Ping.class, e -> { });
        host.plugins().disable(p);
        assertEquals(List.of("c", "a"), closed, "예외가 나도 나머지는 닫히고 등록 역순");
        assertFalse(host.eventsImpl().hasSubscribers(Ping.class));
        p.own(() -> closed.add("late"));
        assertEquals("late", closed.get(2), "꺼진 뒤에 맡기면 바로 닫는다");
    }

    // ---- fail-fast / apiVersion ----

    @Test
    void failFastStopsOnBadJar() throws Exception {
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve("bad.jar"), "furnace-plugin.yml", "name: Bad\nversion: 1\nmain: x.Missing\n");
        host(dir, new PluginSettings(true, null, java.util.Map.of()));
        assertThrows(IllegalStateException.class, host.pluginsImpl()::loadAll);
    }

    @Test
    void failFastStopsOnEnableFailureButDefaultContinues() throws Exception {
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve("b.jar"), "furnace-plugin.yml",
                "name: Broken\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Broken\n", TestPlugins.Broken.class);
        host(dir, new PluginSettings(true, null, java.util.Map.of()));
        host.pluginsImpl().loadAll();
        assertThrows(IllegalStateException.class, host.pluginsImpl()::enableAll);
    }

    @Test
    void noFailFastKeepsGoing() throws Exception {
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve("bad.jar"), "furnace-plugin.yml", "name: Bad\nversion: 1\nmain: x.Missing\n");
        gamma(dir, "");
        host(dir, PluginSettings.DEFAULT);
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        assertTrue(host.plugins().isEnabled("Gamma"));
    }

    @Test
    void apiVersionChecks() throws Exception {
        Path dir = tmp.resolve("plugins");
        gamma(dir, "api-version: '9.0'\n");
        host(dir, PluginSettings.DEFAULT);
        host.pluginsImpl().loadAll();
        assertNull(host.plugins().plugin("Gamma"), "서버보다 새 API 를 요구하면 불러오지 않는다");
        assertTrue(messages().stream().anyMatch(m -> m.contains("API 9.0")));
    }

    @Test
    void apiVersionMissingWarnsButLoads() throws Exception {
        Path dir = tmp.resolve("plugins");
        gamma(dir, "");
        host(dir, PluginSettings.DEFAULT);
        host.pluginsImpl().loadAll();
        assertNotNull(host.plugins().plugin("Gamma"));
        assertTrue(messages().stream().anyMatch(m -> m.contains("api-version 이 없다")));
    }

    @Test
    void apiVersionCurrentLoadsQuietly() throws Exception {
        Path dir = tmp.resolve("plugins");
        gamma(dir, "api-version: '" + ApiVersion.CURRENT + "'\n");
        host(dir, PluginSettings.DEFAULT);
        host.pluginsImpl().loadAll();
        assertNotNull(host.plugins().plugin("Gamma"));
        assertTrue(messages().stream().noneMatch(m -> m.contains("api-version")));
    }

    @Test
    void apiVersionParsing() {
        assertEquals(new ApiVersion(1, 2), ApiVersion.parse("1.2"));
        assertEquals(new ApiVersion(1, 0), ApiVersion.parse("1"));
        assertEquals(new ApiVersion(1, 2), ApiVersion.parse("1.2.3"));
        assertNull(ApiVersion.parse("abc"));
        assertNull(ApiVersion.parse(null));
        assertTrue(new ApiVersion(1, 1).compareTo(new ApiVersion(1, 0)) > 0);
        assertFalse(ApiVersion.serverSatisfies(new ApiVersion(2, 0)));
        assertTrue(ApiVersion.serverSatisfies(ApiVersion.CURRENT));
    }

    // ---- 폴더·로거 ----

    @Test
    void configAndDataFoldersSplitWithDataRoot() throws Exception {
        Path dir = tmp.resolve("plugins");
        gamma(dir, "");
        Path root = tmp.resolve("pdata");
        host(dir, new PluginSettings(false, root, java.util.Map.of()));
        host.pluginsImpl().loadAll();
        var p = host.plugins().plugin("Gamma");
        assertEquals(dir.resolve("Gamma"), p.configFolder());
        assertEquals(root.resolve("Gamma"), p.dataFolder());
        assertTrue(Files.isDirectory(root.resolve("Gamma")));
        p.config().root().set("k", 1);
        p.saveConfig();
        assertTrue(Files.exists(dir.resolve("Gamma").resolve("config.yml")), "설정 파일은 설정 폴더로");
        assertFalse(Files.exists(root.resolve("Gamma").resolve("config.yml")));
    }

    @Test
    void foldersSameByDefault() throws Exception {
        Path dir = tmp.resolve("plugins");
        gamma(dir, "");
        host(dir, PluginSettings.DEFAULT);
        host.pluginsImpl().loadAll();
        var p = host.plugins().plugin("Gamma");
        assertEquals(p.configFolder(), p.dataFolder());
    }

    @Test
    void pluginLogLevelFromSettingsAndRuntime() throws Exception {
        Path dir = tmp.resolve("plugins");
        gamma(dir, "");
        host(dir, new PluginSettings(false, null, java.util.Map.of("gamma", Level.WARNING)));
        host.pluginsImpl().loadAll();
        var p = host.plugins().plugin("Gamma");
        logs.list.clear();
        p.logger().info("조용히");
        p.logger().warning("경고");
        assertEquals(List.of("[Gamma] 경고"), messages().stream().filter(m -> m.contains("조용히") || m.contains("경고")).toList());
        host.plugins().setLogLevel("gamma", Level.INFO);
        logs.list.clear();
        p.logger().info("이제 보임");
        assertEquals(1, messages().stream().filter(m -> m.contains("이제 보임")).count());
        assertEquals("Gamma", logs.list.get(0).getMDCPropertyMap().get("plugin"));
    }

    @Test
    void settingsParse() {
        Properties pr = new Properties();
        pr.setProperty("plugins.fail-fast", "true");
        pr.setProperty("plugins.data-root", "pdata");
        pr.setProperty("plugins.log-level.Foo", "fine");
        pr.setProperty("plugins.log-level.Bar", "nonsense");
        PluginSettings s = PluginSettings.from(pr, org.slf4j.LoggerFactory.getLogger("Furnace"));
        assertTrue(s.failFast());
        assertEquals(Path.of("pdata"), s.dataRoot());
        assertEquals(Level.FINE, s.levelFor("FOO"));
        assertNull(s.levelFor("bar"));
        assertFalse(PluginSettings.DEFAULT.failFast());
    }

    @Test
    void compatNmsOptionsAreValidatedAndPassedThrough() {
        var log = org.slf4j.LoggerFactory.getLogger("Furnace");
        PluginSettings d = PluginSettings.from(new Properties(), log);
        assertEquals("false", d.compatOptions().get("plugins.compat.nms.strict"));
        assertEquals("plain", d.compatOptions().get("plugins.compat.nms.version-package"));
        assertEquals(PluginSettings.DEFAULT, d, "빈 설정 = 기본값");
        Properties pr = new Properties();
        pr.setProperty("plugins.compat.nms.strict", "true");
        pr.setProperty("plugins.compat.nms.version-package", "Versioned");
        pr.setProperty("plugins.compat.extra", " x ");
        PluginSettings s = PluginSettings.from(pr, log);
        assertEquals("true", s.compatOptions().get("plugins.compat.nms.strict"));
        assertEquals("versioned", s.compatOptions().get("plugins.compat.nms.version-package"));
        assertEquals("x", s.compatOptions().get("plugins.compat.extra"), "그 밖의 plugins.compat.* 는 그대로 넘긴다");
        Properties bad = new Properties();
        bad.setProperty("plugins.compat.nms.version-package", "spigot");
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(bad, log), "잘못된 값은 기동을 멈춘다");
        Properties badBool = new Properties();
        badBool.setProperty("plugins.compat.nms.strict", "yes");
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(badBool, log));
        // H7d: Spigot 이름 대응표 — 기본 off, download|files 만, files 는 경로가 있어야 한다
        assertEquals("off", d.compatOptions().get("plugins.compat.nms.spigot-mappings"));
        Properties dl = new Properties();
        dl.setProperty("plugins.compat.nms.spigot-mappings", "Download");
        assertEquals("download", PluginSettings.from(dl, log).compatOptions().get("plugins.compat.nms.spigot-mappings"));
        Properties badMode = new Properties();
        badMode.setProperty("plugins.compat.nms.spigot-mappings", "yes");
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(badMode, log));
        Properties filesNoPath = new Properties();
        filesNoPath.setProperty("plugins.compat.nms.spigot-mappings", "files");
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(filesNoPath, log), "files 는 경로 세 개가 있어야 한다");
    }

    // ---- 조기 콘솔 ----

    @Test
    void earlyConsoleQueuesCommandsAndStopsImmediately() {
        ConsoleInputTestSupport.run(tmp, sched, core);
    }
}
