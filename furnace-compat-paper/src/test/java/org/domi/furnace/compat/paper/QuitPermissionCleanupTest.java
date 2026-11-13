package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 퇴장 때 Bukkit 권한 계산·구독을 걷는다 — Paper: PlayerList.remove → CraftPlayer.disconnect → perm.clearPermissions (T-073 사후 검토 R1).
 * 퇴장 이벤트의 Bukkit 처리기(MONITOR 포함)는 Paper 처럼 권한이 아직 있는 상태로 불린다.
 */
class QuitPermissionCleanupTest {

    private static final String YML = """
            name: QuitPerm
            version: '1'
            main: qp.QuitPerm
            api-version: '1.21'
            permissions:
              qp.everyone:
                default: true
            """;

    /** 이벤트를 하나도 안 듣는 플러그인(권한만 묻는다). */
    private static final String PLAIN = """
            package qp;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class QuitPerm extends JavaPlugin {
                @Override public void onEnable() {
                    WiringProbe.plugin = this;
                    getServer().getPluginManager().getPermission("qp.everyone");   // 권한 객체를 미리 만든다(첫 접속자 생성 중 만들면 재귀 — 별건)
                }
            }
            """;

    /** 입장 처리기를 먼저 등록한 뒤 퇴장 MONITOR 처리기 — 퇴장 처리기가 권한을 아직 보는지 기록한다. */
    private static final String LISTENING = """
            package qp;
            import org.bukkit.Bukkit;
            import org.bukkit.entity.Player;
            import org.bukkit.event.*;
            import org.bukkit.event.player.*;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class QuitPerm extends JavaPlugin implements Listener {
                @Override public void onEnable() {
                    WiringProbe.plugin = this;
                    getServer().getPluginManager().getPermission("qp.everyone");   // 권한 객체를 미리 만든다(첫 접속자 생성 중 만들면 재귀 — 별건)
                    getServer().getPluginManager().registerEvents(this, this);
                }
                @EventHandler public void join(PlayerJoinEvent e) { }
                @EventHandler(priority = EventPriority.MONITOR)
                public void quit(PlayerQuitEvent e) {
                    Player p = e.getPlayer();
                    // 구독 표를 직접 본다(getDefaultPermSubscriptions 는 접속 중인 플레이어를 늘 보태므로 정리 여부를 못 가른다)
                    org.bukkit.plugin.SimplePluginManager pm = (org.bukkit.plugin.SimplePluginManager) Bukkit.getPluginManager();
                    boolean inDef;
                    synchronized (pm.defSubs) { var m = pm.defSubs.get(false); inDef = m != null && m.containsKey(p); }
                    boolean inPerm;
                    synchronized (pm.permSubs) { var m = pm.permSubs.get("qp.everyone"); inPerm = m != null && m.containsKey(p); }
                    WiringProbe.LOG.add("quit-perm:" + p.getEffectivePermissions().size() + ":" + inDef + ":" + inPerm);
                }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    private FurnaceTestServer start(String plugin) throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("qp.jar"), YML, Map.of("qp.QuitPerm", plugin));
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    /** 구독 표(defSubs)에 그 플레이어가 있는가 — getDefaultPermSubscriptions 는 접속 중인 플레이어를 늘 보태므로 접속 중에는 쓸 수 없다. */
    private static boolean subscribed(Player p) {
        var pm = (org.bukkit.plugin.SimplePluginManager) Bukkit.getPluginManager();
        synchronized (pm.defSubs) {
            var m = pm.defSubs.get(false);
            return m != null && m.containsKey(p);
        }
    }

    private void quitClears(String plugin) throws Exception {
        try (FurnaceTestServer server = start(plugin)) {
            TestPlayer tp = server.join("QuitPerm");
            server.tick(2);
            assertNotNull(WiringProbe.plugin);
            Player p = Bukkit.getPlayer("QuitPerm");
            assertNotNull(p);
            assertTrue(p.hasPermission("qp.everyone"));
            assertTrue(subscribed(p), "접속 중에는 기본 권한 구독이 있다");
            tp.close();
            server.tick(5);
            assertFalse(p.isOnline());
            assertTrue(p.getEffectivePermissions().isEmpty(), "퇴장 뒤에도 권한 계산이 남아 있다(Paper 는 비움)");
            assertFalse(subscribed(p), "퇴장 뒤에도 기본 권한 구독이 남아 있다");
        }
    }

    @Test
    void quitClearsPermissionsWhenPluginListensToEvents() throws Exception { quitClears(LISTENING); }

    @Test
    void quitClearsPermissionsWhenPluginListensToNothing() throws Exception { quitClears(PLAIN); }

    @Test
    void quitHandlersStillSeePermissions() throws Exception {
        try (FurnaceTestServer server = start(LISTENING)) {
            TestPlayer tp = server.join("QuitPerm");
            server.tick(2);
            Player p = Bukkit.getPlayer("QuitPerm");
            assertNotNull(p);
            tp.close();
            server.tick(5);
            List<String> seen = WiringProbe.LOG.stream().filter(l -> l.startsWith("quit-perm:")).toList();
            assertTrue(seen.size() == 1, String.valueOf(WiringProbe.LOG));
            String[] f = seen.get(0).split(":");
            assertTrue(Integer.parseInt(f[1]) > 0, "퇴장 처리 중에는 Paper 처럼 계산된 권한이 아직 있다: " + seen.get(0));
            assertEquals("true", f[2], "기본 권한 구독이 아직 있다: " + seen.get(0));
            assertEquals("true", f[3], "권한 구독이 아직 있다: " + seen.get(0));
            assertFalse(subscribed(p), "처리기가 끝난 뒤에는 걷힌다");
        }
    }

    @Test
    void rejoinKeepsNewConnectionPermissions() throws Exception {
        try (FurnaceTestServer server = start(PLAIN)) {
            TestPlayer a = server.join("QuitPerm");
            server.tick(2);
            a.close();
            TestPlayer b = server.join("QuitPerm");   // 퇴장 직후 같은 틱에 재접속 — 옛 접속의 정리가 새 접속의 권한을 지우지 않는다
            server.tick(5);
            Player p = Bukkit.getPlayer("QuitPerm");
            assertNotNull(p);
            assertTrue(p.isOnline());
            assertTrue(p.hasPermission("qp.everyone"));
            assertTrue(subscribed(p), "새 접속의 기본 권한 구독이 남아 있다");
        }
    }
}
