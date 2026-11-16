package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** 권한 객체 하위 클래스로 재계산 횟수를 잰다: PermissibleBase.recalculatePermissions 가 기본 권한마다 getChildren 을 부른다. */
    private static final String PROBE = """
            package fp;
            import java.util.Map;
            import java.util.concurrent.atomic.AtomicInteger;
            import org.bukkit.permissions.Permission;
            import org.bukkit.permissions.PermissionDefault;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class FirstPerm extends JavaPlugin {
                public static final AtomicInteger CALLS = new AtomicInteger();
                static final class Probe extends Permission {
                    Probe() { super("fp.probe", PermissionDefault.TRUE); }
                    @Override public Map<String, Boolean> getChildren() { CALLS.incrementAndGet(); return super.getChildren(); }
                }
                @Override public void onEnable() {
                    WiringProbe.plugin = this;
                    getServer().getPluginManager().addPermission(new Probe());
                }
            }
            """;

    private static org.domi.furnace.api.entity.Player handle(String name) {
        for (var p : Compat.furnace().onlinePlayers()) if (p.name().equals(name)) return p;
        throw new AssertionError("no player " + name);
    }

    private static boolean keyedBy(Map<org.bukkit.permissions.Permissible, Boolean> map, Object who) {
        if (map == null) return false;
        synchronized (map) {
            for (var k : new java.util.ArrayList<>(map.keySet())) if (k == who) return true;
        }
        return false;
    }

    /** 안 감싼 접속자가 많아도 첫 감싸기의 권한 재계산이 접속자 수만큼 중첩되지 않는다(생성 중에는 이미 감싼 것만 본다). 생성 밖에서는 전원이 구독자다. */
    @Test
    void firstWrapDoesNotNestPerUnwrappedPlayer() throws Exception {
        int n = 40;
        Path jar = JarCompiler.jar(tmp.resolve("fp3.jar"), YML, Map.of("fp.FirstPerm", PROBE));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).maxPlayers(n + 5).pluginJar(jar).start()) {
            for (int i = 0; i < n; i++) server.join("P" + i);
            server.tick(2);
            assertNull(Compat.existingPlayer(handle("P0")), "안 감싼 접속자여야 한다");
            var calls = (java.util.concurrent.atomic.AtomicInteger) WiringProbe.plugin.getClass().getField("CALLS").get(null);
            calls.set(0);
            // 작은 스택 스레드에서 — 접속자 수만큼 중첩하면 넘친다
            java.util.concurrent.atomic.AtomicReference<Throwable> err = new java.util.concurrent.atomic.AtomicReference<>();
            Thread t = new Thread(null, () -> {
                try { Compat.player(handle("P0")); } catch (Throwable e) { err.set(e); }
            }, "small-stack", 256 * 1024);
            t.start();
            t.join(60_000);
            assertNull(err.get(), String.valueOf(err.get()));
            assertTrue(calls.get() <= 4, "첫 감싸기의 재계산이 접속자 수에 비례한다: " + calls.get());
            assertNull(Compat.existingPlayer(handle("P1")), "생성 중에는 다른 안 감싼 접속자를 감싸지 않는다");
            // 생성 밖: 플러그인이 구독자를 물으면 접속자 전원이 든다(Paper 는 전원이 CraftPlayer 를 가진다)
            assertTrue(Bukkit.getPluginManager().getDefaultPermSubscriptions(false).size() >= n);
            for (int i = 0; i < n; i++) assertTrue(Bukkit.getPlayer("P" + i).hasPermission("fp.probe"));
        }
    }

    /**
     * 같은 접속을 두 스레드가 동시에 처음 감싸면 둘 다 CraftPlayer 를 만든다. 진 쪽(같은 핸들이라 equals 참)의 생성자·retire 가 이긴 쪽의 구독 칸을
     * 지우지 않고, 이긴 객체 키가 두 구독 표에 남는다.
     */
    @Test
    void raceLoserDoesNotDropWinnerSubscriptions() throws Exception {
        try (FurnaceTestServer server = start()) {
            server.join("Racer");
            server.tick(2);
            var h = handle("Racer");
            assertNull(Compat.existingPlayer(h), "아직 안 감싼 접속이어야 경합을 만든다");
            var spm = (org.bukkit.plugin.SimplePluginManager) Bukkit.getPluginManager();
            java.util.concurrent.atomic.AtomicReference<Player> r1 = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<Player> r2 = new java.util.concurrent.atomic.AtomicReference<>();
            Thread t1 = new Thread(() -> r1.set(Compat.player(h)), "race-1");
            Thread t2 = new Thread(() -> r2.set(Compat.player(h)), "race-2");
            synchronized (spm.defSubs) {   // 두 스레드 모두 생성자 안(구독 해제)에서 멈추게 한다
                t1.start();
                t2.start();
                long until = System.nanoTime() + 10_000_000_000L;
                while ((t1.getState() != Thread.State.BLOCKED || t2.getState() != Thread.State.BLOCKED) && System.nanoTime() < until) Thread.onSpinWait();
                assertTrue(t1.getState() == Thread.State.BLOCKED && t2.getState() == Thread.State.BLOCKED, "두 스레드 모두 생성 중이어야 한다");
            }
            t1.join(30_000);
            t2.join(30_000);
            Player winner = r1.get();
            assertNotNull(winner);
            assertSame(winner, r2.get());
            assertSame(winner, Compat.existingPlayer(h));
            Map<org.bukkit.permissions.Permissible, Boolean> defs;
            Map<org.bukkit.permissions.Permissible, Boolean> perms;
            synchronized (spm.defSubs) { defs = spm.defSubs.get(false); }
            synchronized (spm.permSubs) { perms = spm.permSubs.get("fp.everyone"); }
            assertTrue(keyedBy(defs, winner), "이긴 CraftPlayer 의 기본 권한 구독이 표에 없다");
            assertTrue(keyedBy(perms, winner), "이긴 CraftPlayer 의 fp.everyone 구독이 표에 없다");
        }
    }
}
