package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PR #256 검토 회귀: 처리기 해제 시 버스 구독 해제, HandlerList 직접 등록, 뗐다 다시 달면 맨 뒤. */
class A15HandlerSubscriptionTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    private static String yml(String name) {
        return "name: " + name + "\nversion: '1'\nmain: " + name.toLowerCase() + ".P\napi-version: '1.21'\n";
    }

    private static String src(String name) {
        return "package " + name.toLowerCase() + ";\npublic final class P extends org.bukkit.plugin.java.JavaPlugin {}\n";
    }

    private FurnaceTestServer start() throws Exception {
        Path a = JarCompiler.jar(tmp.resolve("a.jar"), yml("RevA"), Map.of("reva.P", src("RevA")));
        Path b = JarCompiler.jar(tmp.resolve("b.jar"), yml("RevB"), Map.of("revb.P", src("RevB")));
        return FurnaceTestServer.builder().failFast(false).pluginJar(a).pluginJar(b).start();
    }

    static final class Tagged implements Listener {
        final String tag;
        Tagged(String tag) { this.tag = tag; }
        @EventHandler public void on(PlayerToggleSneakEvent e) { WiringProbe.LOG.add(tag); }
    }

    @SuppressWarnings("unchecked")
    private static int busHandlers(FurnaceTestServer server) throws Exception {
        Object bus = ((org.domi.furnace.server.plugin.PluginHost) server.api()).eventsImpl();
        var f = bus.getClass().getDeclaredField("byType");
        f.setAccessible(true);
        var m = (Map<Class<?>, List<?>>) f.get(bus);
        List<?> l = m.get(org.domi.furnace.api.event.player.PlayerToggleSneakEvent.class);
        return l == null ? 0 : l.size();
    }

    /** R1: 처리기를 달았다 뗐다 하는 플러그인(메뉴·미니게임) — 버스 구독과 리스너 객체가 플러그인을 끌 때까지 남는다. */
    @Test
    void registerUnregisterCyclesLeakBusSubscriptions() throws Exception {
        try (FurnaceTestServer server = start()) {
            var p = server.join("Dana").player();
            server.tick(2);
            Plugin a = Bukkit.getPluginManager().getPlugin("RevA");
            int before = busHandlers(server);
            WeakReference<Listener> last = null;
            for (int i = 0; i < 1000; i++) {
                Listener l = new Tagged("tmp");
                Bukkit.getPluginManager().registerEvents(l, a);
                HandlerList.unregisterAll(l);
                last = new WeakReference<>(l);
            }
            int after = busHandlers(server);
            for (int i = 0; i < 5 && last.get() != null; i++) { System.gc(); Thread.sleep(50); }
            boolean collected = last.get() == null;
            // 핫 이벤트 비용: 살아 있는 처리기 0개인 상태에서 1만 번 발화
            var bus = server.api().events();
            for (int i = 0; i < 2000; i++) bus.call(new org.domi.furnace.api.event.player.PlayerToggleSneakEvent(p, (i & 1) == 0));
            long t0 = System.nanoTime();
            for (int i = 0; i < 10000; i++) bus.call(new org.domi.furnace.api.event.player.PlayerToggleSneakEvent(p, (i & 1) == 0));
            long perCallNs = (System.nanoTime() - t0) / 10000;
            System.out.println("[REVIEW256] bus handlers before=" + before + " after=" + after + " lastListenerCollected=" + collected
                    + " perCallNs(no live handler)=" + perCallNs + " log=" + WiringProbe.LOG.size());
            assertEquals(before, after, "해제한 처리기의 버스 구독이 남는다");
            assertEquals(true, collected, "해제한 리스너 객체가 회수되지 않는다");
        }
    }

    /** R2: HandlerList#register 로 직접 단 RegisteredListener(공개 API) — Paper 는 부른다. */
    @Test
    void directHandlerListRegisterIsCalled() throws Exception {
        try (FurnaceTestServer server = start()) {
            var p = server.join("Dana").player();
            server.tick(2);
            Plugin a = Bukkit.getPluginManager().getPlugin("RevA");
            Bukkit.getPluginManager().registerEvents(new Tagged("N1"), a);
            Tagged direct = new Tagged("D1");
            PlayerToggleSneakEvent.getHandlerList().register(new RegisteredListener(direct, (l, e) -> ((Tagged) l).on((PlayerToggleSneakEvent) e),
                    EventPriority.NORMAL, a, false));
            server.api().events().call(new org.domi.furnace.api.event.player.PlayerToggleSneakEvent(p, true));
            System.out.println("[REVIEW256] direct register log=" + WiringProbe.LOG);
            assertEquals(List.of("N1", "D1"), WiringProbe.LOG);
        }
    }

    /** R3: 같은 RegisteredListener 를 뗐다 다시 달면 Paper 는 그 우선순위 맨 뒤로 간다. */
    @Test
    void reRegisteredListenerMovesToTheEnd() throws Exception {
        try (FurnaceTestServer server = start()) {
            var p = server.join("Dana").player();
            server.tick(2);
            Plugin a = Bukkit.getPluginManager().getPlugin("RevA");
            Plugin b = Bukkit.getPluginManager().getPlugin("RevB");
            Bukkit.getPluginManager().registerEvents(new Tagged("A1"), a);
            Bukkit.getPluginManager().registerEvents(new Tagged("B1"), b);
            HandlerList hl = PlayerToggleSneakEvent.getHandlerList();
            RegisteredListener rlA = null;
            for (RegisteredListener rl : hl.getRegisteredListeners()) if (rl.getPlugin() == a) rlA = rl;
            hl.unregister(rlA);
            hl.register(rlA);
            server.api().events().call(new org.domi.furnace.api.event.player.PlayerToggleSneakEvent(p, true));
            System.out.println("[REVIEW256] re-register log=" + WiringProbe.LOG);
            assertEquals(List.of("B1", "A1"), WiringProbe.LOG);
        }
    }
}
