package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.MenuProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 보관함 메뉴 안전(PR #161 독립 검토에서 재현된 두 문제의 회귀 시험): 한 칸 끌기도 Paper 처럼 PICKUP InventoryClickEvent 로 막을 수 있고,
 * 서로 상대 인벤토리에 쓰는 두 메뉴의 클릭이 연결 스레드를 교착시키지 않는다.
 */
class StorageMenuSafetyTest {

    private static final String YML = """
            name: GuiUser
            version: '1'
            main: gu.GuiUser
            api-version: '1.21'
            commands:
              gui: {}
            """;

    private static final String PLUGIN = """
            package gu;
            import java.util.*;
            import org.bukkit.*;
            import org.bukkit.command.*;
            import org.bukkit.entity.Player;
            import org.bukkit.event.*;
            import org.bukkit.event.inventory.*;
            import org.bukkit.inventory.*;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.MenuProbe;
            public final class GuiUser extends JavaPlugin implements Listener {
                static void log(String s) { MenuProbe.LOG.add(s); }
                Inventory a, b;
                @Override public void onEnable() {
                    getServer().getPluginManager().registerEvents(this, this);
                    a = Bukkit.createInventory(null, 27, "A");
                    b = Bukkit.createInventory(null, 27, "B");
                    a.setItem(0, new ItemStack(Material.DIAMOND, 1));
                    b.setItem(0, new ItemStack(Material.DIAMOND, 1));
                    MenuProbe.inventory = a;
                    MenuProbe.bossBar = b;
                }
                @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                    Player p = (Player) sender;
                    try {
                        if (args[0].equals("give")) p.getInventory().setItem(9, new ItemStack(Material.STONE, 5));
                        else if (args[0].equals("a")) p.openInventory(a);
                        else if (args[0].equals("b")) p.openInventory(b);
                    } catch (Throwable t) { MenuProbe.failure = t; }
                    return true;
                }
                // 보호 GUI: 위쪽 칸 클릭·모든 끌기를 막는다(흔한 GUI 플러그인 패턴)
                @EventHandler public void click(InventoryClickEvent e) {
                    if (e.getInventory() != a && e.getInventory() != b) return;
                    log("click=" + e.getRawSlot() + "," + e.getClick() + "," + e.getAction());
                    if (e.getRawSlot() >= 0 && e.getRawSlot() < 27) {
                        e.setCancelled(true);
                        if (e.getRawSlot() == 8) {   // 서로 이어진 두 창(거래 GUI 흉내): 상대 창 칸을 고친다
                            try { Thread.sleep(300); } catch (InterruptedException ie) {}
                            (e.getInventory() == a ? b : a).setItem(5, new ItemStack(Material.STONE, 1));
                            log("linked.done");
                        }
                    }
                }
                @EventHandler public void drag(InventoryDragEvent e) {
                    if (e.getInventory() != a && e.getInventory() != b) return;
                    log("drag=" + e.getRawSlots());
                    e.setCancelled(true);
                }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { MenuProbe.reset(); }

    private static void click(TestPlayer tp, int slot, int button, int mode) {
        tp.sendPacket(0x0E, b -> {
            b.writeByte(1);
            writeVarInt(b, 0);
            b.writeShort(slot);
            b.writeByte(button);
            writeVarInt(b, mode);
            writeVarInt(b, 0);
            writeVarInt(b, 0);
        });
    }

    private static void writeVarInt(ByteBuf b, int v) {
        while ((v & ~0x7F) != 0) {
            b.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        b.writeByte(v);
    }

    @Test
    void singleSlotDragIsACancellableClick() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("gu.jar"), YML, Map.of("gu.GuiUser", PLUGIN));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            var inv = (org.bukkit.inventory.Inventory) MenuProbe.inventory;
            TestPlayer tp = server.join("Steve");
            server.tick(2);
            tp.command("gui give"); tp.sync(); server.tick(3); tp.sync();
            tp.command("gui a"); tp.sync(); server.tick(3); tp.sync();
            assertNull(MenuProbe.failure);
            click(tp, 27, 0, 0);      // 플레이어 칸의 돌 5개를 커서로(허용)
            server.tick(2);
            // 한 칸 끌기: 시작·0번 칸·끝 — 바닐라는 0번 칸 PICKUP 으로 바꾼다
            click(tp, -999, 0, 5);
            click(tp, 2, 1, 5);
            click(tp, -999, 2, 5);
            server.tick(2);
            tp.sync();
            assertNull(inv.getItem(2), "Paper 는 한 칸 끌기를 PICKUP InventoryClickEvent 로 내 취소된다 — 빈 칸이 그대로여야 한다. log=" + MenuProbe.LOG);
            assertTrue(MenuProbe.LOG.contains("click=2,LEFT,PLACE_ALL"), "한 칸 끌기는 그 칸 왼쪽 클릭으로 보인다: " + MenuProbe.LOG);
        }
    }

    @Test
    void linkedStorageMenusDoNotDeadlock() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("gu.jar"), YML, Map.of("gu.GuiUser", PLUGIN));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            TestPlayer p1 = server.join("Alice");
            TestPlayer p2 = server.join("Bob");
            server.tick(2);
            p1.command("gui a"); p1.sync();
            p2.command("gui b"); p2.sync();
            server.tick(3); p1.sync(); p2.sync();
            ExecutorService ex = Executors.newFixedThreadPool(2);
            Future<?> f1 = ex.submit(() -> click(p1, 8, 0, 0));
            Future<?> f2 = ex.submit(() -> click(p2, 8, 0, 0));
            Throwable t1 = null, t2 = null;
            try { f1.get(60, TimeUnit.SECONDS); } catch (Exception e) { t1 = e; }
            try { f2.get(60, TimeUnit.SECONDS); } catch (Exception e) { t2 = e; }
            ex.shutdownNow();
            assertNull(t1);
            assertNull(t2);
            // 두 처리기(각 300ms)가 끝날 때까지 기다리며 교착을 본다 — 교착이면 sync() 가 영영 돌아오지 않으므로 먼저 본다
            java.lang.management.ThreadMXBean mx = java.lang.management.ManagementFactory.getThreadMXBean();
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (MenuProbe.LOG.stream().filter(s -> s.equals("linked.done")).count() < 2 && System.nanoTime() < until) {
                long[] dl = mx.findMonitorDeadlockedThreads();
                if (dl != null) {
                    StringBuilder sb = new StringBuilder();
                    for (var info : mx.getThreadInfo(dl, true, true)) sb.append(info);
                    org.junit.jupiter.api.Assertions.fail("연결 스레드 교착: " + sb);
                }
                Thread.sleep(50);
            }
            p1.sync();
            p2.sync();
            assertTrue(MenuProbe.LOG.stream().filter(s -> s.equals("linked.done")).count() == 2, MenuProbe.LOG.toString());
        }
    }
}
