package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
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
 * 첫 CraftPlayer 를 만드는 중(권한 계산) 플러그인이 선언한 권한 객체가 처음 만들어져도 "Recursive update" 가 나지 않는다.
 * 경로: CraftPlayer 생성 → PermissibleBase.recalculatePermissions → getDefaultPermissions → Permission 만들기 →
 * recalculatePermissibles → getPermissionSubscriptions → getOnlinePlayers → 같은 UUID 의 Compat.player (같은 표에서 compute 가 다시 불림).
 * Paper 는 접속자 목록에 아직 없는 새 플레이어의 CraftPlayer 를 만드는 중이므로 그 순회에 새 플레이어가 없다.
 */
class FirstPlayerPermissionRecursionTest {

    private static final String YML = """
            name: FirstPerm
            version: '1'
            main: fp.FirstPerm
            api-version: '1.21'
            permissions:
              fp.everyone:
                default: true
              fp.ops:
                default: op
            """;

    /** 권한 객체를 미리 만들지 않는다 — 첫 접속자의 CraftPlayer 생성 중에 처음 만들어진다. */
    private static final String PLUGIN = """
            package fp;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class FirstPerm extends JavaPlugin {
                @Override public void onEnable() { WiringProbe.plugin = this; }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("fp.jar"), YML, Map.of("fp.FirstPerm", PLUGIN));
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    @Test
    void firstPlayerCreationDeclaresPermissionsWithoutRecursion() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("First");
            server.tick(2);
            Player p = Bukkit.getPlayer("First");
            assertNotNull(p);
            assertSame(p, Bukkit.getPlayer("First"), "같은 접속은 같은 CraftPlayer");
            assertTrue(p.hasPermission("fp.everyone"));
            assertNotNull(Bukkit.getPluginManager().getPermission("fp.everyone"));
            assertEquals(1, Bukkit.getPluginManager().getDefaultPermSubscriptions(false).stream().filter(x -> x == p).count(),
                    "기본 권한 구독에 한 번만 있다");
            assertEquals(1, Bukkit.getOnlinePlayers().size());
            assertSame(p, Bukkit.getOnlinePlayers().iterator().next());
            tp.close();
        }
    }

    @Test
    void secondPlayerSeesFirstPlayerInRecalculation() throws Exception {
        try (FurnaceTestServer server = start()) {
            server.join("First");
            server.tick(2);
            server.join("Second");
            server.tick(2);
            Player a = Bukkit.getPlayer("First");
            Player b = Bukkit.getPlayer("Second");
            assertNotNull(a);
            assertNotNull(b);
            assertTrue(a.hasPermission("fp.everyone") && b.hasPermission("fp.everyone"));
            var subs = Bukkit.getPluginManager().getPermissionSubscriptions("fp.everyone");
            assertTrue(subs.contains(a) && subs.contains(b));
            assertEquals(2, Bukkit.getOnlinePlayers().size());
        }
    }

    @Test
    void concurrentFirstWrapGivesOneObject() throws Exception {
        try (FurnaceTestServer server = start()) {
            server.join("First");
            server.join("Second");
            server.tick(2);
            int n = 8;
            var go = new java.util.concurrent.CountDownLatch(1);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(n);
            try {
                java.util.List<java.util.concurrent.Future<Player[]>> fs = new java.util.ArrayList<>();
                for (int i = 0; i < n; i++) {
                    fs.add(pool.submit(() -> {
                        go.await();
                        return new Player[] {Bukkit.getPlayer("First"), Bukkit.getPlayer("Second")};
                    }));
                }
                go.countDown();
                Player[] first = null;
                for (var f : fs) {
                    Player[] got = f.get(30, java.util.concurrent.TimeUnit.SECONDS);   // 서로의 칸을 기다리는 교착이면 시간 초과
                    if (first == null) first = got;
                    assertSame(first[0], got[0]);
                    assertSame(first[1], got[1]);
                }
                var subs = Bukkit.getPluginManager().getDefaultPermSubscriptions(false);
                assertEquals(1, subs.stream().filter(x -> x == Bukkit.getPlayer("First")).count());
                assertEquals(2, subs.size());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    /** 권한만 선언하고 입장·퇴장 처리기를 가진 평범한 플러그인(권한 객체를 미리 만들지 않는다). */
    private static final String LISTENING = """
            package fp;
            import org.bukkit.event.*;
            import org.bukkit.event.player.*;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class FirstPerm extends JavaPlugin implements Listener {
                @Override public void onEnable() {
                    WiringProbe.plugin = this;
                    getServer().getPluginManager().registerEvents(this, this);
                }
                @EventHandler public void join(PlayerJoinEvent e) { WiringProbe.LOG.add("join:" + e.getPlayer().getName() + ":" + e.getPlayer().hasPermission("fp.everyone")); }
                @EventHandler public void quit(PlayerQuitEvent e) { WiringProbe.LOG.add("quit:" + e.getPlayer().getName() + ":" + e.getPlayer().hasPermission("fp.everyone")); }
            }
            """;

    @Test
    void ordinaryPluginJoinAndQuitHandlersRun() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("fp2.jar"), YML, Map.of("fp.FirstPerm", LISTENING));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            TestPlayer tp = server.join("First");
            server.tick(2);
            tp.close();
            server.tick(5);
            assertTrue(WiringProbe.LOG.contains("join:First:true"), String.valueOf(WiringProbe.LOG));
            assertTrue(WiringProbe.LOG.contains("quit:First:true"), String.valueOf(WiringProbe.LOG));
        }
    }

    /** 감싸지 않은 접속자가 둘일 때 하나를 감싸는 중에 다른 하나도 감싸진다(중첩) — 권한 객체는 하나, 구독은 각각 한 번. */
    @Test
    void nestedWrapOfUnwrappedPlayersKeepsOnePermissionObject() throws Exception {
        try (FurnaceTestServer server = start()) {
            server.join("First");
            server.join("Second");
            server.tick(2);
            Player b = Bukkit.getPlayer("Second");
            Player a = Bukkit.getPlayer("First");
            assertNotNull(a);
            assertNotNull(b);
            var perm = Bukkit.getPluginManager().getPermission("fp.everyone");
            assertNotNull(perm);
            assertSame(perm, Bukkit.getPluginManager().getPermission("fp.everyone"));
            var subs = Bukkit.getPluginManager().getDefaultPermSubscriptions(false);
            assertEquals(1, subs.stream().filter(x -> x == a).count());
            assertEquals(1, subs.stream().filter(x -> x == b).count());
            assertTrue(a.hasPermission("fp.everyone") && b.hasPermission("fp.everyone"));
        }
    }
}
