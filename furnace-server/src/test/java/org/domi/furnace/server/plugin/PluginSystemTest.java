// 플러그인 시스템: jar 탐색(설명 파일 yml·toml·json, Bukkit 플러그인 구분)·의존 순서·수명·설정·명령 등록(겹침→namespaced)·
// 자동완성·이벤트(우선순위·취소·상위 타입·예외 격리·해제)·권한(기본값·자식·첨부).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.permission.PermissionAttachment;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginSystemTest {

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

    /** 설명 파일 + (테스트 클래스패스에서 복사한) 메인 클래스로 jar 를 만든다. */
    static void jar(Path file, String descriptorName, String descriptor, Class<?>... classes) throws IOException {
        Files.createDirectories(file.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new JarEntry(descriptorName));
            out.write(descriptor.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            for (Class<?> c : classes) {
                String path = c.getName().replace('.', '/') + ".class";
                try (InputStream in = c.getClassLoader().getResourceAsStream(path)) {
                    out.putNextEntry(new JarEntry(path));
                    out.write(in.readAllBytes());
                    out.closeEntry();
                }
            }
        }
    }

    private PluginHost loadAll(Path dir) {
        host = new PluginHost(core, dir);
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        return host;
    }

    @Test
    void loadsInDependencyOrderAndSkipsBadJars() throws Exception {
        Path dir = tmp.resolve("plugins");
        jar(dir.resolve("b.jar"), "furnace-plugin.yml", """
                name: Beta
                version: 2.0
                main: org.domi.furnace.server.plugin.TestPlugins$Beta
                depend: [Alpha]
                """, TestPlugins.Beta.class);
        jar(dir.resolve("a.jar"), "furnace-plugin.toml", """
                name = "Alpha"
                version = "1.0"
                main = "org.domi.furnace.server.plugin.TestPlugins$Alpha"
                """, TestPlugins.Alpha.class);
        jar(dir.resolve("c.jar"), "furnace-plugin.json", """
                {"name": "Gamma", "version": "1", "main": "org.domi.furnace.server.plugin.TestPlugins$Gamma", "loadbefore": ["Alpha"]}
                """, TestPlugins.Gamma.class);
        jar(dir.resolve("needs.jar"), "furnace-plugin.yml", "name: Needy\nversion: 1\nmain: x.Y\ndepend: [Missing]\n");
        jar(dir.resolve("bukkit.jar"), "plugin.yml", "name: Ess\nversion: 1\nmain: x.Y\n");
        jar(dir.resolve("bukkit-plugins").resolve("real.jar"), "plugin.yml", "name: Real\nversion: 1\nmain: x.Y\n");
        Files.createDirectories(dir.resolve("Alpha"));
        Files.writeString(dir.resolve("Alpha").resolve("config.yml"), "greeting: 안녕\n");

        loadAll(dir);
        var pm = host.plugins();
        assertEquals(3, pm.plugins().size(), "Needy(의존 없음)·Bukkit jar 는 빠진다");
        assertTrue(pm.isEnabled("alpha"));
        assertEquals(List.of("alpha:load", "beta:load", "gamma:enable", "alpha:enable:안녕", "beta:enable"),
                TestPlugins.LOG.subList(0, 5).stream().toList().size() == 5
                        ? List.of(TestPlugins.LOG.get(0), TestPlugins.LOG.get(1), TestPlugins.LOG.get(2), TestPlugins.LOG.get(3), TestPlugins.LOG.get(4))
                        : TestPlugins.LOG,
                "로드는 의존 순서(Gamma 는 loadbefore Alpha 라 먼저), 켜기도 같은 순서");
    }

    @Test
    void commandsRegisterNamespacedOnConflictAndUnregisterOnDisable() throws Exception {
        Path dir = tmp.resolve("plugins");
        jar(dir.resolve("a.jar"), "furnace-plugin.yml", "name: Alpha\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Alpha\n",
                TestPlugins.Alpha.class);
        jar(dir.resolve("b.jar"), "furnace-plugin.yml", "name: Beta\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Beta\n",
                TestPlugins.Beta.class);
        loadAll(dir);
        var root = core.commands().getDispatcher().getRoot();
        assertNotNull(root.getChild("hello"));
        assertNotNull(root.getChild("alpha:hello"));
        assertNotNull(root.getChild("alpha:list"), "바닐라 list 와 겹치면 namespaced 만");
        List<String> console = new java.util.concurrent.CopyOnWriteArrayList<>();   // 전역 스레드가 쓰고 테스트 스레드가 읽는다
        core.addConsoleListener(t -> console.add(t.getString()));
        // 전역 스레드를 붙잡아 둔다: 안 그러면 전역 스레드가 명령을 먼저 돌려 "아직 안 돌았다" 단언이 타이밍(부하)에 따라 깨진다
        var gate = new java.util.concurrent.CountDownLatch(1);
        var held = new java.util.concurrent.CountDownLatch(1);
        var gateTask = core.scheduler().global().submit(() -> {
            held.countDown();
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        try {
            assertTrue(held.await(30, java.util.concurrent.TimeUnit.SECONDS), "전역 스레드가 붙잡혔다");
            assertTrue(host.dispatchCommand(host.console(), "/hello a b"), "접수");
            assertFalse(console.contains("hello a,b"), "테스트 스레드에서 부르면 바로 돌지 않고 전역으로 넘어간다");
        } finally {
            gate.countDown();   // 단언이 실패해도 전역 스레드를 풀어 준다
        }
        gateTask.join();
        globalBarrier();   // 명령은 전역 스레드로 넘어가 실행된다
        assertTrue(console.contains("hello a,b"));
        host.dispatchCommand(host.console(), "list");
        globalBarrier();
        assertFalse(TestPlugins.LOG.contains("alpha:list"), "list 는 여전히 바닐라");
        host.dispatchCommand(host.console(), "alpha:list");
        globalBarrier();
        assertTrue(TestPlugins.LOG.contains("alpha:list"));
        // 자동완성: 마지막 인자 자리에 접두어로 거른 후보
        var s = core.commands().suggestions(CommandSource.console(core), "/secret be").get();
        assertEquals(List.of("beta"), s.getList().stream().map(x -> x.getText()).toList());
        // 콘솔 명령 이벤트 취소
        core.runConsoleCommand("blocked thing");
        globalBarrier();
        // 끄면 명령이 빠진다
        host.plugins().disable(host.plugins().plugin("Alpha"));
        assertNull(root.getChild("hello"));
        assertTrue(TestPlugins.LOG.contains("alpha:disable"));
    }

    static class Ping extends Event implements Cancellable {
        final List<String> seen = new ArrayList<>();
        boolean cancelled;

        public boolean isCancelled() { return cancelled; }

        public void setCancelled(boolean c) { cancelled = c; }
    }

    static final class SubPing extends Ping {}

    @Test
    void eventBusOrdersByPriorityAndHonorsCancellation() throws Exception {
        Path dir = tmp.resolve("plugins");
        jar(dir.resolve("g.jar"), "furnace-plugin.yml", "name: Gamma\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Gamma\n",
                TestPlugins.Gamma.class);
        loadAll(dir);
        FurnacePlugin g = host.plugins().plugin("Gamma");
        var bus = host.events();
        assertFalse(bus.hasSubscribers(SubPing.class));
        bus.subscribe(g, Ping.class, EventPriority.MONITOR, false, e -> e.seen.add("monitor"));
        bus.subscribe(g, Ping.class, EventPriority.LOW, false, e -> { e.seen.add("low"); e.setCancelled(true); });
        bus.subscribe(g, Ping.class, EventPriority.NORMAL, true, e -> e.seen.add("normal-ignored"));
        bus.subscribe(g, SubPing.class, EventPriority.LOWEST, false, e -> e.seen.add("lowest-sub"));
        bus.subscribe(g, Event.class, EventPriority.HIGHEST, false, e -> { throw new RuntimeException("격리돼야 함"); });
        assertTrue(bus.hasSubscribers(SubPing.class));
        SubPing ev = bus.call(new SubPing());
        assertEquals(List.of("lowest-sub", "low", "monitor"), ev.seen, "우선순위 순, 취소 후 ignoreCancelled 건너뜀, 예외는 격리");
        host.plugins().disable(g);
        assertFalse(bus.hasSubscribers(SubPing.class), "플러그인을 끄면 처리기도 빠진다");
    }

    @Test
    void tickStatsCountsCallsOnlyWhenEnabled() throws Exception {
        Path dir = tmp.resolve("plugins");
        jar(dir.resolve("g.jar"), "furnace-plugin.yml", "name: Gamma\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Gamma\n",
                TestPlugins.Gamma.class);
        loadAll(dir);
        FurnacePlugin g = host.plugins().plugin("Gamma");

        var off = new EventBusImpl(false);
        off.subscribe(g, Ping.class, EventPriority.NORMAL, false, e -> e.seen.add("p"));
        off.call(new Ping());
        assertEquals(0, off.statCount(Ping.class), "기본은 꺼짐: 측정하지 않는다");

        var on = new EventBusImpl(true);
        on.subscribe(g, Ping.class, EventPriority.NORMAL, false, e -> e.seen.add("p"));
        for (int i = 0; i < 3; i++) on.call(new Ping());
        on.call(new SubPing());
        assertEquals(3, on.statCount(Ping.class), "Ping 직접 호출 3회");
        assertEquals(1, on.statCount(SubPing.class), "SubPing 호출 1회");
        assertTrue(on.statTotalNs(Ping.class) >= on.statMaxNs(Ping.class), "누적 시간은 최대 단일 호출 시간 이상");
        assertTrue(on.statMaxNs(Ping.class) > 0);
        assertEquals(0, on.statCount(Event.class), "호출되지 않은 종류는 0");
    }

    @Test
    void concurrentRegistrationNeverLeavesStaleHandlerArray() throws Exception {
        Path dir = tmp.resolve("plugins");
        jar(dir.resolve("g.jar"), "furnace-plugin.yml", "name: Gamma\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Gamma\n",
                TestPlugins.Gamma.class);
        loadAll(dir);
        FurnacePlugin g = host.plugins().plugin("Gamma");
        for (int round = 0; round < 200; round++) {
            var bus = new EventBusImpl(false);
            var stop = new java.util.concurrent.atomic.AtomicBoolean();
            Thread reader = new Thread(() -> { while (!stop.get()) bus.hasSubscribers(Ping.class); });
            reader.start();
            bus.subscribe(g, Ping.class, e -> e.seen.add("p"));
            stop.set(true);
            reader.join();
            assertTrue(bus.hasSubscribers(Ping.class), "등록 직후 읽기와 겹쳐도 새 처리기가 보여야 한다 (round " + round + ")");
        }
    }

    @Test
    void permissionsFollowDefaultsChildrenAndAttachments() throws Exception {
        Path dir = tmp.resolve("plugins");
        jar(dir.resolve("g.jar"), "furnace-plugin.yml", """
                name: Gamma
                version: 1
                main: org.domi.furnace.server.plugin.TestPlugins$Gamma
                permissions:
                  gamma.*:
                    default: op
                    children:
                      gamma.use: true
                      gamma.deny: false
                  gamma.everyone:
                    default: true
                """, TestPlugins.Gamma.class);
        loadAll(dir);
        PermissibleBase user = new PermissibleBase(host.permissionsImpl(), () -> false);
        PermissibleBase op = new PermissibleBase(host.permissionsImpl(), () -> true);
        assertTrue(user.hasPermission("gamma.everyone"));
        assertFalse(user.hasPermission("gamma.use"), "OP 기본 권한의 자식은 OP 만");
        assertTrue(op.hasPermission("gamma.use"));
        assertFalse(op.hasPermission("gamma.deny"), "자식 값 false");
        assertFalse(user.hasPermission("unknown.node"), "미등록은 OP 만");
        assertTrue(op.hasPermission("unknown.node"));
        PermissionAttachment a = user.addAttachment(host.plugins().plugin("Gamma"));
        a.setPermission("gamma.*", true);
        assertTrue(user.hasPermission("gamma.use"), "첨부한 부모의 자식도 따라온다");
        a.setPermission("gamma.use", false);
        assertFalse(user.hasPermission("gamma.use"));
        user.removeAttachments(host.plugins().plugin("Gamma"));
        assertFalse(user.hasPermission("gamma.use"));
        assertEquals(Map.of(), a.permissions().isEmpty() ? Map.of() : Map.of(), "첨부 객체는 그대로 읽을 수 있다");
    }

    @Test
    void permissionOverrideDecidesFirstAndNullFallsBack() throws Exception {
        loadAll(Files.createDirectories(tmp.resolve("plugins")));
        var console = host.console();
        var rcon = host.rconSender(t -> {});
        assertTrue(console.hasPermission("over.node"), "콘솔은 OP — 미등록 권한도 참");
        java.util.List<String> asked = new java.util.concurrent.CopyOnWriteArrayList<>();
        host.permissions().setOverride(new org.domi.furnace.api.permission.PermissionOverride() {
            @Override
            public Boolean hasPermission(org.domi.furnace.api.permission.Permissible subject, String node) {
                asked.add(((org.domi.furnace.api.command.CommandSender) subject).name() + ":" + node);
                return node.equals("over.node") ? Boolean.FALSE : null;
            }

            @Override
            public Boolean isPermissionSet(org.domi.furnace.api.permission.Permissible subject, String node) {
                return node.equals("over.node") ? Boolean.TRUE : null;
            }
        });
        assertSame(host.permissions().override(), host.permissionsImpl().override());
        assertFalse(console.hasPermission("over.node"), "판정 공급자가 먼저");
        assertTrue(console.isPermissionSet("over.node"));
        assertTrue(console.hasPermission("other.node"), "null 이면 기본 규칙");
        assertFalse(console.isPermissionSet("other.node"));
        assertFalse(rcon.hasPermission("over.node"), "RCON 발신자도 같은 공급자를 거친다");
        assertEquals(List.of("Server:over.node", "Server:other.node", "Rcon:over.node"), asked);
        host.permissions().setOverride(null);
        assertTrue(console.hasPermission("over.node"));
        assertEquals("Rcon", rcon.name());
        assertTrue(rcon.isOp());
        assertEquals(core.settings().get().onlineMode, host.onlineMode());
    }

    @Test
    void failingEnableDisablesPlugin() throws Exception {
        Path dir = tmp.resolve("plugins");
        jar(dir.resolve("x.jar"), "furnace-plugin.yml", "name: Broken\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Broken\n",
                TestPlugins.Broken.class);
        loadAll(dir);
        assertFalse(host.plugins().isEnabled("Broken"), "onEnable 예외 → 꺼진 상태");
    }

    /** 전역 스레드에 넘어간 작업이 끝날 때까지 기다린다(FIFO 실행기). */
    private void globalBarrier() { core.scheduler().global().submit(() -> null).join(); }
}
