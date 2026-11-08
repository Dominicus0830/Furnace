package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.MenuProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Paper 의 InventoryView#setTitle 은 그 창(그 플레이어) 하나만 바꾼다 — CraftInventoryView.title 필드와
 * 그 플레이어에게만 가는 open_screen. 같은 Inventory 를 보는 다른 사람·다음에 다시 연 창은 createInventory 제목 그대로다. null 은 IllegalArgumentException.
 */
class InventoryViewTitleTest {

    private static final String YML = """
            name: TitleUser
            version: '1'
            main: tu.TitleUser
            api-version: '1.21'
            commands:
              menu: {}
            """;

    private static final String PLUGIN = """
            package tu;
            import org.bukkit.*;
            import org.bukkit.command.*;
            import org.bukkit.entity.Player;
            import org.bukkit.inventory.*;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.MenuProbe;
            public final class TitleUser extends JavaPlugin {
                static void log(String s) { MenuProbe.LOG.add(s); }
                Inventory inv;
                @Override public void onEnable() { inv = Bukkit.createInventory(null, 9, "Shop"); }
                @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                    Player p = (Player) sender;
                    try {
                        switch (args.length == 0 ? "open" : args[0]) {
                            case "open" -> log("opened." + p.getName() + "=" + p.openInventory(inv).getTitle());
                            case "title" -> p.getOpenInventory().setTitle(args[1]);
                            case "fancy" -> p.getOpenInventory().setTitle("\u00a7lBold\u00a7aGreen");
                            case "view" -> log("view." + p.getName() + "=" + p.getOpenInventory().getTitle() + "," + p.getOpenInventory().getOriginalTitle());
                            case "close" -> p.closeInventory();
                            case "null" -> {
                                try { p.getOpenInventory().setTitle(null); log("null=accepted," + p.getOpenInventory().getTitle()); }
                                catch (IllegalArgumentException e) { log("null=" + e.getMessage()); }
                            }
                        }
                    } catch (Throwable t) { MenuProbe.failure = t; }
                    return true;
                }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { MenuProbe.reset(); }

    private static void run(FurnaceTestServer server, TestPlayer tp, String cmd) {
        tp.command(cmd);
        tp.sync();
        server.tick(2);
        tp.sync();
    }

    @Test
    void setTitleIsPerViewInPaper() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("tu.jar"), YML, Map.of("tu.TitleUser", PLUGIN));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            Map<String, AtomicInteger> openScreens = new ConcurrentHashMap<>();
            server.internals().plugins().packetsImpl().listenInternal(PacketTypes.Clientbound.OPEN_SCREEN, EventPriority.MONITOR,
                    e -> openScreens.computeIfAbsent(e.player().name(), k -> new AtomicInteger()).incrementAndGet());
            var log = MenuProbe.LOG;
            TestPlayer steve = server.join("Steve");
            TestPlayer alex = server.join("Alex");
            server.tick(2);
            run(server, steve, "menu");
            run(server, alex, "menu");
            assertTrue(log.contains("opened.Steve=Shop") && log.contains("opened.Alex=Shop"), log.toString());
            int alexBefore = openScreens.get("Alex").get();

            run(server, steve, "menu title Page2");
            run(server, steve, "menu view");
            run(server, alex, "menu view");
            assertNull(MenuProbe.failure, () -> "명령이 던졌다: " + MenuProbe.failure);
            assertTrue(log.contains("view.Steve=Page2,Shop"), log.toString());
            StringBuilder diffs = new StringBuilder();
            if (!log.contains("view.Alex=Shop,Shop")) diffs.append("[Alex 의 창 제목도 바뀜] ");
            if (openScreens.get("Alex").get() != alexBefore) diffs.append("[Alex 에게도 open_screen 이 다시 감] ");

            run(server, steve, "menu close");
            run(server, steve, "menu");
            if (!log.contains("opened.Steve=Shop") || log.lastIndexOf("opened.Steve=Shop") < log.indexOf("view.Steve=Page2,Shop"))
                diffs.append("[다시 연 창이 createInventory 제목이 아님] ");

            run(server, steve, "menu fancy");
            run(server, steve, "menu view");
            if (!log.contains("view.Steve=§lBold§aGreen,Shop")) diffs.append("[getTitle 이 setTitle 문자열과 다름] ");

            run(server, steve, "menu null");
            if (!log.contains("null=Title cannot be null")) diffs.append("[null 을 받아들임] ");
            assertEquals("", diffs.toString(), "Paper 와 다른 점: " + diffs + " / 기록 " + log);
        }
    }
}
