package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.SimplePluginManager;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.compat.thread.PrimaryThread;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bukkit {@link SimplePluginManager}: helper 처럼 리플렉션으로 commandMap·knownCommands 를 만지는 플러그인, 플러그인 찾기·켜기·끄기,
 * 권한 API(plugin.yml 권한·실행 중 추가·자식 고치기·구독·권한 방송). 플러그인은 시험 안에서 컴파일한 진짜 jar 다.
 */
@SuppressWarnings("removal")
class SimplePluginManagerTest {

    private static final String YML = """
            name: Spm
            version: '1'
            main: spm.SpmPlugin
            api-version: '1.21'
            provides: [SpmAlias]
            permissions:
              spm.use:
                default: true
                children:
                  spm.child: true
              spm.admin:
                default: op
            """;

    /** helper 의 CommandMapUtil.registerCommand 와 같은 순서: 리플렉션 생성자 → register → knownCommands 에 "플러그인:별칭"·"별칭" 넣기. */
    private static final String PLUGIN = """
            package spm;
            import java.lang.reflect.*;
            import java.util.Map;
            import org.bukkit.Bukkit;
            import org.bukkit.command.*;
            import org.bukkit.plugin.*;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class SpmPlugin extends JavaPlugin implements CommandExecutor {
                @Override public void onEnable() {
                    WiringProbe.plugin = this;
                    try {
                        Constructor<PluginCommand> ctor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
                        ctor.setAccessible(true);
                        Field cmField = SimplePluginManager.class.getDeclaredField("commandMap");
                        cmField.setAccessible(true);
                        Field kcField = SimpleCommandMap.class.getDeclaredField("knownCommands");
                        kcField.setAccessible(true);
                        CommandMap map = (CommandMap) cmField.get(Bukkit.getServer().getPluginManager());
                        @SuppressWarnings("unchecked") Map<String, Command> known = (Map<String, Command>) kcField.get(map);
                        String[] aliases = {"hcmd", "hcmd2"};
                        PluginCommand c = ctor.newInstance(aliases[0], this);
                        map.register(getDescription().getName(), c);
                        for (String a : aliases) {
                            known.put(getName().toLowerCase() + ":" + a.toLowerCase(), c);
                            known.put(a.toLowerCase(), c);
                        }
                        c.setLabel(aliases[0].toLowerCase());
                        c.setExecutor(this);
                        WiringProbe.LOG.add("map=" + (map == Bukkit.getCommandMap()));
                        getServer().getPluginManager().registerEvents(new BrokenListener(), this);
                        WiringProbe.LOG.add("broken-listener-skipped");
                    } catch (Throwable t) { WiringProbe.failure = t; }
                }
                @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                    WiringProbe.LOG.add("cmd:" + command.getName() + "|" + label);
                    return true;
                }
            }
            """;

    /** 처리기 인자가 jar 에 없는 클래스(호환 어댑터에 없는 이벤트를 흉내 낸다) — getMethods 가 NoClassDefFoundError. */
    private static final String BROKEN = """
            package spm;
            import org.bukkit.event.*;
            public final class BrokenListener implements Listener {
                @EventHandler public void on(spm.gone.GoneEvent e) {}
            }
            """;

    private static final String GONE = """
            package spm.gone;
            import org.bukkit.event.*;
            public class GoneEvent extends Event {
                private static final HandlerList H = new HandlerList();
                @Override public HandlerList getHandlers() { return H; }
                public static HandlerList getHandlerList() { return H; }
            }
            """;

    private static final String QUIT_YML = """
            name: Quitter
            version: '1'
            main: quit.QuitPlugin
            api-version: '1.21'
            commands:
              quitcmd: {}
            """;

    /** 의존이 없을 때 흔히 하는 것처럼 처음 켤 때 스스로 끈다. */
    private static final String QUIT = """
            package quit;
            import org.bukkit.command.*;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class QuitPlugin extends JavaPlugin {
                static int enables;
                @Override public void onEnable() {
                    enables++;
                    WiringProbe.LOG.add("quit-enable:" + enables + ",cmd=" + (getCommand("quitcmd") != null));
                    if (enables == 1) getServer().getPluginManager().disablePlugin(this);
                }
                @Override public void onDisable() { WiringProbe.LOG.add("quit-disable"); }
                @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) { WiringProbe.LOG.add("quitcmd"); return true; }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path spm = JarCompiler.jar(tmp.resolve("spm.jar"), YML, Map.of("spm.SpmPlugin", PLUGIN, "spm.BrokenListener", BROKEN, "spm.gone.GoneEvent", GONE),
                Set.of("spm.gone."));
        Path quit = JarCompiler.jar(tmp.resolve("quit.jar"), QUIT_YML, Map.of("quit.QuitPlugin", QUIT));
        return FurnaceTestServer.builder().failFast(false).pluginJar(spm).pluginJar(quit).start();
    }

    private static boolean got(TestPlayer tp, String text) {
        for (Text t : tp.messages()) if (t.plainText().contains(text)) return true;
        return false;
    }

    // ------------------------------------------------------------------ helper 의 리플렉션 경로

    @Test
    void helperStyleReflectionFindsTheCommandMapAndKnownCommandsPutAddsWorkingAliases() throws Exception {
        try (FurnaceTestServer server = start()) {
            assertNull(WiringProbe.failure, () -> "onEnable 이 던졌다: " + WiringProbe.failure);
            assertInstanceOf(SimplePluginManager.class, Bukkit.getPluginManager());
            assertTrue(WiringProbe.LOG.contains("map=true"), "SimplePluginManager.commandMap 은 서버의 명령 지도");
            CommandMap map = Bukkit.getCommandMap();
            assertNotNull(map.getCommand("hcmd"));
            assertSame(map.getCommand("hcmd"), map.getCommand("hcmd2"), "knownCommands.put 으로 넣은 별칭");
            assertSame(map.getCommand("hcmd"), map.getCommand("spm:hcmd2"));
            server.execute("hcmd");
            server.execute("hcmd2");
            server.execute("spm:hcmd2");
            server.tick();
            assertEquals(List.of("cmd:hcmd|hcmd", "cmd:hcmd|hcmd2", "cmd:hcmd|spm:hcmd2"),
                    WiringProbe.LOG.stream().filter(l -> l.startsWith("cmd:")).toList());
            // 지우면 트리에서도 빠진다(H4A 그대로)
            Map<String, ?> known = map.getKnownCommands();
            known.remove("hcmd2");
            assertNull(map.getCommand("hcmd2"));
            assertNotNull(map.getCommand("hcmd"));
        }
    }

    @Test
    void knownCommandsPutRejectsAnotherPluginsPrefix() throws Exception {
        try (FurnaceTestServer server = start()) {
            var c = Bukkit.getCommandMap().getCommand("hcmd");
            assertThrows(UnsupportedOperationException.class, () -> Bukkit.getCommandMap().getKnownCommands().put("other:x", c));
        }
    }

    @Test
    void listenerWithMissingEventClassIsSkippedLikePaperAndThePluginStaysEnabled() throws Exception {
        try (FurnaceTestServer server = start()) {
            assertNull(WiringProbe.failure, () -> "registerEvents 가 던졌다: " + WiringProbe.failure);
            assertTrue(WiringProbe.LOG.contains("broken-listener-skipped"));
            assertTrue(WiringProbe.plugin.isEnabled());
        }
    }

    // ------------------------------------------------------------------ 찾기·켜기·끄기

    @Test
    void getPluginIsCaseInsensitiveAndKnowsProvides() throws Exception {
        try (FurnaceTestServer server = start()) {
            PluginManager pm = Bukkit.getPluginManager();
            Plugin p = WiringProbe.plugin;
            assertSame(p, pm.getPlugin("Spm"));
            assertSame(p, pm.getPlugin("SPM"));
            assertSame(p, pm.getPlugin("spmalias"), "plugin.yml 의 provides");
            assertNull(pm.getPlugin("nope"));
            assertTrue(pm.isPluginEnabled("spm"));
            assertTrue(pm.isPluginEnabled(p));
            assertFalse(pm.isPluginEnabled((Plugin) null));
            assertEquals(2, pm.getPlugins().length);
        }
    }

    @Test
    void pluginDisablingItselfInOnEnableStaysOffAndEnablePluginTurnsItBackOn() throws Exception {
        try (FurnaceTestServer server = start()) {
            PluginManager pm = Bukkit.getPluginManager();
            Plugin q = pm.getPlugin("Quitter");
            assertNotNull(q);
            assertFalse(q.isEnabled());
            assertFalse(pm.isPluginEnabled("Quitter"));
            assertEquals(List.of("quit-enable:1,cmd=true", "quit-disable"), WiringProbe.LOG.stream().filter(l -> l.startsWith("quit")).toList());
            assertNull(Bukkit.getCommandMap().getCommand("quitcmd"), "끈 플러그인의 명령은 빠진다");

            pm.enablePlugin(q);
            assertTrue(q.isEnabled());
            assertTrue(WiringProbe.LOG.contains("quit-enable:2,cmd=true"), "다시 켤 때 plugin.yml 명령을 다시 올린다");
            server.execute("quitcmd");
            server.tick();
            assertTrue(WiringProbe.LOG.contains("quitcmd"));
            pm.enablePlugin(q);   // 이미 켜져 있으면 아무 일도 없다
            assertEquals(1, WiringProbe.LOG.stream().filter(l -> l.startsWith("quit-enable:2")).count());

            assertThrows(org.bukkit.plugin.InvalidPluginException.class, () -> pm.loadPlugin(tmp.resolve("quit.jar").toFile()));
            assertEquals(0, pm.loadPlugins(tmp.toFile()).length);
        }
    }

    // ------------------------------------------------------------------ 이벤트 스레드 검사

    static final class AsyncPing extends Event {
        private static final HandlerList H = new HandlerList();

        AsyncPing() { super(true); }

        @Override
        public HandlerList getHandlers() { return H; }

        public static HandlerList getHandlerList() { return H; }
    }

    @Test
    void asyncEventOnThePrimaryThreadIsRejectedLikePaper() throws Exception {
        try (FurnaceTestServer server = start()) {
            PluginManager pm = Bukkit.getPluginManager();
            pm.callEvent(new AsyncPing());   // 시험 스레드는 주 스레드가 아니다
            try (PrimaryThread.Scope ignored = PrimaryThread.enter()) {
                IllegalStateException e = assertThrows(IllegalStateException.class, () -> pm.callEvent(new AsyncPing()));
                assertEquals("AsyncPing may only be triggered asynchronously.", e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ 권한

    @Test
    void pluginYmlPermissionsAreVisibleAsStableBukkitObjects() throws Exception {
        try (FurnaceTestServer server = start(); TestPlayer tp = server.join("Dana")) {
            PluginManager pm = Bukkit.getPluginManager();
            Permission use = pm.getPermission("SPM.USE");
            assertNotNull(use);
            assertSame(use, pm.getPermission("spm.use"), "같은 노드는 같은 객체");
            assertEquals(PermissionDefault.TRUE, use.getDefault());
            assertEquals(Map.of("spm.child", true), use.getChildren());
            assertEquals(PermissionDefault.OP, pm.getPermission("spm.admin").getDefault());
            assertTrue(pm.getDefaultPermissions(false).contains(use));
            assertFalse(pm.getDefaultPermissions(false).contains(pm.getPermission("spm.admin")));
            assertTrue(pm.getDefaultPermissions(true).contains(pm.getPermission("spm.admin")));
            assertTrue(pm.getPermissions().contains(use));

            Player dana = Bukkit.getPlayerExact("Dana");
            assertTrue(dana.hasPermission("spm.use"));
            assertTrue(dana.hasPermission("spm.child"), "자식");
            assertFalse(dana.hasPermission("spm.admin"));

            // Paper 처럼 public 필드로도 보인다
            Field f = SimplePluginManager.class.getField("permissions");
            assertSame(use, ((Map<?, ?>) f.get(pm)).get("spm.use"));
        }
    }

    @Test
    void runtimePermissionsReachFurnaceAndFollowRecalculation() throws Exception {
        try (FurnaceTestServer server = start(); TestPlayer tp = server.join("Dana")) {
            PluginManager pm = Bukkit.getPluginManager();
            Player dana = Bukkit.getPlayerExact("Dana");
            Permission rt = new Permission("Spm.Runtime", PermissionDefault.TRUE);
            assertFalse(dana.hasPermission("spm.runtime"), "올리기 전에는 미등록 → OP 만");
            pm.addPermission(rt);
            assertSame(rt, pm.getPermission("spm.runtime"));
            assertEquals("Spm.Runtime", pm.getPermission("spm.runtime").getName(), "Bukkit 처럼 이름 대소문자는 그대로");
            assertNotNull(server.api().permissions().permission("spm.runtime"), "Furnace 등록소에도 있다");
            assertTrue(dana.hasPermission("spm.runtime"));
            assertThrows(IllegalArgumentException.class, () -> pm.addPermission(new Permission("spm.runtime")));
            assertEquals(PermissionDefault.TRUE, pm.getPermission("spm.runtime").getDefault(), "같은 이름의 새 객체는 등록된 것을 건드리지 않는다");

            rt.getChildren().put("spm.extra", false);
            assertFalse(dana.isPermissionSet("spm.extra"), "recalculatePermissibles 전에는 반영되지 않는다");
            rt.recalculatePermissibles();
            assertTrue(dana.isPermissionSet("spm.extra"));
            assertFalse(dana.hasPermission("spm.extra"));

            rt.setDefault(PermissionDefault.FALSE);
            assertFalse(dana.hasPermission("spm.runtime"));

            Permission parent = rt.addParent("spm.parent", true);
            assertSame(parent, pm.getPermission("spm.parent"));
            assertEquals(Map.of("Spm.Runtime", true), parent.getChildren());

            pm.removePermission("SPM.RUNTIME");
            assertNull(pm.getPermission("spm.runtime"));
            assertNull(server.api().permissions().permission("spm.runtime"));
            assertFalse(dana.hasPermission("spm.runtime"));
        }
    }

    @Test
    void hasPermissionWithAnObjectUsesThatObjectsDefaultWhenUnset() throws Exception {
        try (FurnaceTestServer server = start(); TestPlayer tp = server.join("Dana")) {
            Player dana = Bukkit.getPlayerExact("Dana");
            Permission loose = new Permission("not.registered", PermissionDefault.TRUE);
            assertTrue(dana.hasPermission(loose));
            assertFalse(dana.hasPermission("not.registered"));
            assertTrue(dana.hasPermission(Bukkit.getPluginManager().getPermission("spm.use")));
        }
    }

    @Test
    void subscriptionsAndPermissionBroadcastFollowBukkit() throws Exception {
        try (FurnaceTestServer server = start(); TestPlayer tp = server.join("Dana")) {
            PluginManager pm = Bukkit.getPluginManager();
            Player dana = Bukkit.getPlayerExact("Dana");
            Set<Permissible> subs = pm.getPermissionSubscriptions("spm.use");
            assertTrue(subs.contains(dana));
            assertTrue(subs.contains(Bukkit.getConsoleSender()));
            assertTrue(pm.getPermissionSubscriptions("no.such.node").isEmpty(), "등록되지 않은 권한은 아무도 구독하지 않는다");
            assertTrue(pm.getDefaultPermSubscriptions(false).contains(dana));
            assertFalse(pm.getDefaultPermSubscriptions(true).contains(dana));
            assertTrue(pm.getDefaultPermSubscriptions(true).contains(Bukkit.getConsoleSender()));

            Permissible extra = Bukkit.getConsoleSender();
            pm.subscribeToPermission("spm.custom", extra);
            assertEquals(Set.of(extra), pm.getPermissionSubscriptions("spm.custom"));
            pm.unsubscribeFromPermission("spm.custom", extra);
            assertTrue(pm.getPermissionSubscriptions("spm.custom").isEmpty());

            assertEquals(2, Bukkit.broadcast("hello-use", "spm.use"), "Dana + 콘솔");
            assertEquals(1, Bukkit.broadcast("hello-admin", "spm.admin"), "콘솔만");
            assertEquals(0, Bukkit.broadcast("hello-none", "no.such.node"), "등록되지 않은 권한이면 OP·콘솔도 받지 않는다(Bukkit 과 같다)");
            server.tick();
            assertTrue(got(tp, "hello-use"));
            assertFalse(got(tp, "hello-admin"));
            assertFalse(got(tp, "hello-none"));
        }
    }
}
