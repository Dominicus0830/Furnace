package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.compat.thread.PrimaryThread;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * JavaPlugin 에 명령·스케줄러·서비스를 이은 것(H4 다음 묶음 A). 플러그인은 시험 안에서 컴파일한 진짜 jar 이고(그래서 플러그인 클래스로더가
 * 클래스를 직접 올린다 — getProvidingPlugin 이 실제 경로로 확인된다), Bukkit API 만 불러 헤드리스 서버에서 돈다.
 */
class JavaPluginWiringTest {

    private static final String YML = """
            name: Wiring
            version: '1'
            main: wiring.WiringPlugin
            api-version: '1.21'
            commands:
              wire:
                description: Wire things
                usage: "Usage: /<command> <x>"
                aliases: [wi, wr]
                permission: wiring.use
                permission-message: "no way <permission>"
              plain: {}
            """;

    private static final String PLUGIN = """
            package wiring;
            import org.bukkit.Bukkit;
            import org.bukkit.command.*;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class WiringPlugin extends JavaPlugin {
                @Override public void onLoad() { WiringProbe.LOG.add("load:wire=" + (getCommand("wire") != null)); }
                @Override public void onEnable() {
                    WiringProbe.plugin = this;
                    try {
                        PluginCommand c = getCommand("wire");
                        WiringProbe.LOG.add("enable:wire=" + (c != null) + ",exec=" + (c.getExecutor() == this) + ",alias=" + (getCommand("wi") == c)
                                + ",ns=" + (getCommand("wiring:wire") == c) + ",unknown=" + (getCommand("nope") == null));
                        WiringProbe.providingForUtil = Util.who();
                        WiringProbe.selfByClass = JavaPlugin.getPlugin(WiringPlugin.class);
                        getServer().getCommandMap().register("wiring", new Echo());
                    } catch (Throwable t) { WiringProbe.failure = t; }
                }
                @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                    WiringProbe.LOG.add("cmd:" + command.getName() + "|" + label + "|" + String.join(",", args) + "|" + sender.getName() + "|primary=" + Bukkit.isPrimaryThread());
                    if (command.getName().equals("plain")) throw new IllegalStateException("boom");
                    return args.length > 0;
                }
            }
            """;

    private static final String UTIL = """
            package wiring;
            import org.bukkit.plugin.Plugin;
            import org.bukkit.plugin.java.JavaPlugin;
            public final class Util { public static Plugin who() { return JavaPlugin.getProvidingPlugin(Util.class); } }
            """;

    private static final String ECHO = """
            package wiring;
            import java.util.List;
            import org.bukkit.command.*;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class Echo extends Command {
                public Echo() { super("echo"); setAliases(List.of("ec")); setDescription("e"); }
                @Override public boolean execute(CommandSender s, String l, String[] a) {
                    WiringProbe.LOG.add("echo:" + l + "|" + String.join(",", a));
                    s.sendMessage("echo " + String.join(" ", a));
                    return true;
                }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("wiring.jar"), YML,
                Map.of("wiring.WiringPlugin", PLUGIN, "wiring.Util", UTIL, "wiring.Echo", ECHO));
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    private static List<String> lines(FurnaceTestServer server, String cmd) { return server.execute(cmd).lines(); }

    private static JavaPlugin plugin() { return (JavaPlugin) WiringProbe.plugin; }

    // ------------------------------------------------------------------ 로드 순서·클래스로더

    @Test
    void commandsExistFromEnableOnAndProvidingPluginFollowsTheClassLoader() throws Exception {
        try (FurnaceTestServer server = start()) {
            assertNull(WiringProbe.failure, () -> "onEnable 이 던졌다: " + WiringProbe.failure);
            assertEquals("load:wire=false", WiringProbe.LOG.get(0), "Bukkit 처럼 onLoad 에서는 getCommand 가 null");
            assertEquals("enable:wire=true,exec=true,alias=true,ns=true,unknown=true", WiringProbe.LOG.get(1));
            Plugin p = WiringProbe.plugin;
            assertNotNull(p);
            assertSame(p, WiringProbe.providingForUtil, "플러그인 jar 의 다른 클래스에서 getProvidingPlugin");
            assertSame(p, WiringProbe.selfByClass, "getPlugin(Class)");
            assertSame(p, JavaPlugin.getProvidingPlugin(p.getClass()));
            assertThrows(IllegalArgumentException.class, () -> JavaPlugin.getProvidingPlugin(String.class), "플러그인 클래스로더가 올린 클래스가 아니면");
            assertThrows(IllegalArgumentException.class, () -> JavaPlugin.getPlugin(null));
            assertSame(p, Bukkit.getPluginManager().getPlugin("Wiring"));
        }
    }

    // ------------------------------------------------------------------ 명령

    @Test
    void pluginCommandCarriesYamlSettingsAndDefaultsToThePluginAsExecutor() throws Exception {
        try (FurnaceTestServer server = start()) {
            PluginCommand wire = plugin() instanceof JavaPlugin jp ? jp.getCommand("wire") : null;
            assertNotNull(wire);
            assertEquals("Wire things", wire.getDescription());
            assertEquals("Usage: /<command> <x>", wire.getUsage());
            assertEquals("wiring.use", wire.getPermission());
            assertEquals("no way <permission>", wire.getPermissionMessage());
            assertEquals(List.of("wi", "wr"), wire.getAliases());
            assertSame(plugin(), wire.getPlugin());
            assertSame(plugin(), wire.getExecutor());
            assertEquals("", ((JavaPlugin) plugin()).getCommand("plain").getUsage(), "yaml 에 usage 가 없으면 PluginCommand 는 빈 문자열(Bukkit 과 같다)");
            assertTrue(wire.isRegistered());
            assertSame(wire, Bukkit.getPluginCommand("wire"));
            assertSame(wire, Bukkit.getPluginCommand("wiring:wi"));
            assertNull(Bukkit.getPluginCommand("nope"));
            assertSame(wire, Bukkit.getCommandMap().getCommand("WR"), "대소문자 무시");
        }
    }

    @Test
    void treeRoutesTypedLabelToTheDefaultExecutorOnAPrimaryThread() throws Exception {
        try (FurnaceTestServer server = start()) {
            WiringProbe.LOG.clear();
            assertTrue(server.execute("wire a b").lines().isEmpty(), "true 를 돌려주면 사용법 없음");
            server.execute("wi x");
            server.execute("wiring:wire y");
            assertEquals(List.of("cmd:wire|wire|a,b|CONSOLE|primary=true", "cmd:wire|wi|x|CONSOLE|primary=true", "cmd:wire|wiring:wire|y|CONSOLE|primary=true"), WiringProbe.LOG);
            assertEquals(List.of("Usage: /wire <x>"), lines(server, "wire"), "false → usage (<command> 는 친 이름)");
            assertEquals(List.of("Usage: /wr <x>"), lines(server, "wr"));
            assertEquals(List.of("An internal error occurred while attempting to perform this command"), lines(server, "plain"));
        }
    }

    @Test
    void executorAndCompleterCanBeReplacedAndNullRestoresThePlugin() throws Exception {
        try (FurnaceTestServer server = start()) {
            PluginCommand wire = ((JavaPlugin) plugin()).getCommand("wire");
            wire.setExecutor((s, c, l, a) -> {
                WiringProbe.LOG.add("custom:" + l);
                return true;
            });
            WiringProbe.LOG.clear();
            server.execute("wire");
            assertEquals(List.of("custom:wire"), WiringProbe.LOG);
            wire.setExecutor(null);
            assertSame(plugin(), wire.getExecutor());

            server.join("Alice");
            server.join("alfred");
            server.join("Bob");
            CommandSender console = Bukkit.getConsoleSender();
            assertEquals(List.of("alfred", "Alice"), wire.tabComplete(console, "wire", new String[]{"al"}), "플러그인의 onTabComplete 가 null → 접속자 이름");
            wire.setTabCompleter((s, c, a, args) -> List.of("from-completer"));
            assertEquals(List.of("from-completer"), wire.tabComplete(console, "wire", new String[]{"x"}));
            wire.setTabCompleter((s, c, a, args) -> null);
            assertEquals(List.of("Bob"), wire.tabComplete(console, "wire", new String[]{"b"}), "완성기가 null 이면 다음으로");
            List<String> viaMap = Bukkit.getCommandMap().tabComplete(console, "wire al");
            assertEquals(List.of("alfred", "Alice"), viaMap);
            assertTrue(Bukkit.getCommandMap().tabComplete(console, "wi").containsAll(List.of("wire", "wiring:wire")), "첫 낱말은 지도의 이름들");
        }
    }

    @Test
    void permissionAndExceptionsFollowBukkitRules() throws Exception {
        try (FurnaceTestServer server = start()) {
            PluginCommand wire = ((JavaPlugin) plugin()).getCommand("wire");
            var steve = server.join("Steve");
            CommandSender asSteve = Compat.player(steve.player());
            WiringProbe.LOG.clear();
            assertTrue(wire.execute(asSteve, "wire", new String[]{"x"}), "권한이 없으면 거절 문장을 보내고 true");
            assertTrue(WiringProbe.LOG.isEmpty());
            server.tick();
            assertTrue(steve.messageLines().contains("no way wiring.use"), () -> steve.messageLines().toString());
            steve.command("wire q");
            server.tick(2);
            assertTrue(WiringProbe.LOG.isEmpty(), "권한 없는 사람에게는 트리에서 숨는다");

            CommandException boom = assertThrows(CommandException.class, () -> ((JavaPlugin) plugin()).getCommand("plain").execute(Bukkit.getConsoleSender(), "plain", new String[0]));
            assertInstanceOf(IllegalStateException.class, boom.getCause());
            assertTrue(boom.getMessage().contains("Unhandled exception executing command 'plain' in plugin Wiring v1"), boom.getMessage());
        }
    }

    @Test
    void commandsFromAnyCommandSubclassReachTheTreeAndKnownCommandsIsALiveView() throws Exception {
        try (FurnaceTestServer server = start()) {
            var map = Bukkit.getCommandMap();
            assertEquals(List.of("echo hi there"), lines(server, "echo hi there"));
            assertEquals(List.of("echo:echo|hi,there"), WiringProbe.LOG.subList(WiringProbe.LOG.size() - 1, WiringProbe.LOG.size()));
            lines(server, "ec x");
            assertEquals("echo:ec|x", WiringProbe.LOG.get(WiringProbe.LOG.size() - 1));
            Command echo = map.getCommand("echo");
            assertNotNull(echo);
            assertNull(Bukkit.getPluginCommand("echo"), "PluginCommand 가 아니다");
            for (String k : List.of("echo", "ec", "wiring:echo", "wiring:ec")) assertSame(echo, map.getKnownCommands().get(k), k);
            assertEquals("echo", echo.getLabel());
            assertEquals(List.of("ec"), echo.getAliases());
            assertTrue(echo.isRegistered());

            assertSame(echo, map.getKnownCommands().remove("ec"), "지우면 트리에서도 빠진다");
            assertNull(map.getCommand("ec"));
            assertFalse(server.execute("ec x").success());
            assertEquals(List.of("echo yo"), lines(server, "echo yo"), "다른 이름은 그대로");

            assertThrows(UnsupportedOperationException.class, () -> map.getKnownCommands().put("zzz", echo), "넣기는 register 로만");
            map.getKnownCommands().values().removeIf(c -> c == echo);   // 플러그인이 흔히 하는 해제 방식
            assertNull(map.getCommand("echo"));
            assertNull(map.getCommand("wiring:echo"));
            assertFalse(server.execute("echo yo").success());
            assertTrue(echo.unregister(map));
            assertFalse(echo.isRegistered());
            assertSame(plugin(), Bukkit.getPluginCommand("wire").getPlugin(), "PluginCommand 는 그대로");

            // 플러그인 jar 의 클래스도, PluginIdentifiableCommand 도 아니면 주인을 알 수 없다
            Command foreign = new Command("foreign") {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) { return true; }
            };
            var e = assertThrows(UnsupportedFeature.class, () -> map.register("x", foreign));
            assertTrue(e.getMessage().contains("주인 플러그인"), e.getMessage());
        }
    }

    @Test
    void dispatchRunsOldStyleCommandsInlineOnAPrimaryThreadAndHopsOtherwise() throws Exception {
        try (FurnaceTestServer server = start()) {
            WiringProbe.LOG.clear();
            boolean inline = server.onGlobal(() -> {
                boolean r = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "wire q");
                return r && WiringProbe.LOG.size() == 1;   // 돌려주기 전에 이미 실행됐다
            });
            assertTrue(inline);
            assertEquals("cmd:wire|wire|q|CONSOLE|primary=true", WiringProbe.LOG.get(0));
            WiringProbe.LOG.clear();
            assertTrue(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "/wire z"), "주 스레드가 아니면 접수만");
            server.tick(2);
            assertEquals(List.of("cmd:wire|wire|z|CONSOLE|primary=true"), WiringProbe.LOG);
            assertThrows(CommandException.class, () -> server.onGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "plain")),
                    "주 스레드에서 직접 돌린 명령의 예외는 CommandException 으로 나간다");
            assertFalse(Bukkit.getCommandMap().dispatch(Bukkit.getConsoleSender(), "no-such-thing"), "지도에 없으면 false");
        }
    }

    // ------------------------------------------------------------------ 스케줄러

    @Test
    void schedulerRunsSyncTasksOnTicksAndAsyncOnAWorker() throws Exception {
        try (FurnaceTestServer server = start()) {
            BukkitScheduler s = Bukkit.getScheduler();
            Plugin p = plugin();
            assertFalse(Bukkit.isPrimaryThread(), "시험 스레드는 주 스레드가 아니다");
            assertTrue(server.onGlobal(Bukkit::isPrimaryThread));

            List<String> log = new CopyOnWriteArrayList<>();
            BukkitTask later = s.runTaskLater(p, () -> log.add("later@" + Bukkit.isPrimaryThread()), 2);
            assertSame(p, later.getOwner());
            assertTrue(later.isSync());
            s.runTask(p, () -> log.add("next"));
            server.tick();
            assertEquals(List.of("next"), log, "지연 0 은 다음 틱");
            server.tick();
            assertEquals(List.of("next", "later@true"), log);

            // 반복 + 자기 취소(BukkitRunnable)
            AtomicInteger runs = new AtomicInteger();
            BukkitRunnable timer = new BukkitRunnable() {
                @Override
                public void run() {
                    if (runs.incrementAndGet() == 3) cancel();
                }
            };
            assertThrows(IllegalStateException.class, timer::getTaskId, "예약 전");
            BukkitTask t = timer.runTaskTimer(p, 1, 2);
            assertThrows(IllegalStateException.class, () -> timer.runTask(p), "두 번 예약 불가");
            assertEquals(t.getTaskId(), timer.getTaskId());
            assertTrue(s.isQueued(t.getTaskId()));
            assertTrue(s.getPendingTasks().stream().anyMatch(x -> x.getTaskId() == t.getTaskId()));
            server.tick(10);
            assertEquals(3, runs.get());
            assertTrue(timer.isCancelled());
            assertFalse(s.isQueued(t.getTaskId()));

            // Consumer 변형은 자기 핸들을 받는다
            AtomicReference<BukkitTask> got = new AtomicReference<>();
            s.runTaskTimer(p, task -> {
                got.set(task);
                task.cancel();
            }, 1, 1);
            server.tick(3);
            assertNotNull(got.get());
            assertTrue(got.get().isCancelled());

            // callSyncMethod
            Future<String> f = s.callSyncMethod(p, () -> "v@" + Bukkit.isPrimaryThread());
            server.tick();
            assertEquals("v@true", f.get());

            // 비동기: 틱에 도착한 뒤 일꾼에서 돈다
            AtomicReference<String> async = new AtomicReference<>();
            s.runTaskAsynchronously(p, () -> async.set("async@" + Bukkit.isPrimaryThread()));
            server.tick(2);
            long deadline = System.currentTimeMillis() + 5000;
            while (async.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(10);
            assertEquals("async@false", async.get());

            // 번호를 주는 옛 이름들과 취소
            AtomicInteger n = new AtomicInteger();
            int id = s.scheduleSyncRepeatingTask(p, n::incrementAndGet, 0, 1);
            server.tick(2);
            s.cancelTask(id);
            int seen = n.get();
            server.tick(2);
            assertEquals(seen, n.get());
            assertTrue(seen >= 1);
        }
    }

    @Test
    void disablingThePluginCancelsItsTasksAndRefusesNewOnes() throws Exception {
        try (FurnaceTestServer server = start()) {
            BukkitScheduler s = Bukkit.getScheduler();
            Plugin p = plugin();
            AtomicInteger n = new AtomicInteger();
            s.runTaskTimer(p, n::incrementAndGet, 1, 1);
            server.tick(2);
            assertTrue(n.get() >= 1);
            Bukkit.getPluginManager().disablePlugin(p);
            int after = n.get();
            server.tick(3);
            assertEquals(after, n.get(), "꺼지면 작업이 멈춘다");
            assertThrows(IllegalPluginAccessException.class, () -> s.runTask(p, () -> {}), "꺼진 뒤 예약은 IllegalPluginAccessException");
            assertNull(Bukkit.getCommandMap().getCommand("wire"), "명령도 지도에서 빠진다");
            assertNull(Bukkit.getCommandMap().getCommand("echo"));
            assertFalse(server.execute("wire a").success());
            assertThrows(IllegalArgumentException.class, () -> s.runTask(null, () -> {}), "Plugin cannot be null");
        }
    }

    // ------------------------------------------------------------------ 서비스

    public interface Bank { String name(); }

    @Test
    void servicesOrderByPriorityFireEventsAndGoAwayWithThePlugin() throws Exception {
        try (FurnaceTestServer server = start()) {
            ServicesManager sm = Bukkit.getServicesManager();
            Plugin p = plugin();
            List<String> events = new ArrayList<>();
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void reg(ServiceRegisterEvent e) { events.add("+" + ((Bank) e.getProvider().getProvider()).name()); }

                @EventHandler
                public void unreg(ServiceUnregisterEvent e) { events.add("-" + ((Bank) e.getProvider().getProvider()).name()); }
            }, p);
            Bank low = () -> "low";
            Bank high = () -> "high";
            Bank high2 = () -> "high2";
            sm.register(Bank.class, low, p, ServicePriority.Low);
            assertSame(low, sm.load(Bank.class));
            sm.register(Bank.class, high, p, ServicePriority.High);
            assertSame(high, sm.load(Bank.class), "높은 우선순위가 앞");
            sm.register(Bank.class, high2, p, ServicePriority.High);
            assertSame(high2, sm.load(Bank.class), "같은 우선순위는 Bukkit 의 이진 탐색 자리(나중 것이 앞)");
            RegisteredServiceProvider<Bank> reg = sm.getRegistration(Bank.class);
            assertSame(p, reg.getPlugin());
            assertEquals(ServicePriority.High, reg.getPriority());
            assertEquals(3, sm.getRegistrations(Bank.class).size());
            assertEquals(3, sm.getRegistrations(p).size());
            assertTrue(sm.isProvidedFor(Bank.class));
            assertEquals(List.of(Bank.class), new ArrayList<>(sm.getKnownServices()));
            assertEquals(List.of("+low", "+high", "+high2"), events);

            sm.unregister(Bank.class, high2);
            assertSame(high, sm.load(Bank.class));
            assertEquals("-high2", events.get(events.size() - 1));

            Bukkit.getPluginManager().disablePlugin(p);
            assertNull(sm.load(Bank.class), "플러그인이 꺼지면 등록이 풀린다");
            assertFalse(sm.isProvidedFor(Bank.class));
            assertTrue(sm.getKnownServices().isEmpty());
        }
    }

    @Test
    void pluginLoaderBindingEndsWhenThePluginIsDisabled() throws Exception {
        try (FurnaceTestServer server = start()) {
            Plugin p = plugin();
            Class<?> util = p.getClass().getClassLoader().loadClass("wiring.Util");
            assertSame(p, JavaPlugin.getProvidingPlugin(util));
            Bukkit.getPluginManager().disablePlugin(p);
            assertThrows(IllegalArgumentException.class, () -> JavaPlugin.getProvidingPlugin(util), "꺼진 뒤에는 묶임이 풀린다(알려진 차이)");
            assertNotNull(PrimaryThread.class);
        }
    }
}
