package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.event.Event;
import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.OrderPlugin;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Furnace 이벤트가 Bukkit 처리기에 닿고, 처리기가 바꾼 값이 Furnace 쪽에 돌아오는지 — 헤드리스 서버의 이벤트 버스에 직접 이벤트를 발행해 본다. */
class EventBridgeTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() {
        RecorderPlugin.reset();
        OrderPlugin.LOG.clear();
    }

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
    }

    private Path orderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("order.jar"), "plugin.yml", """
                name: Order
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.OrderPlugin
                api-version: '1.21'
                """, OrderPlugin.class);
    }

    private static List<Event> seen(Class<? extends Event> type) {
        List<Event> out = new ArrayList<>();
        for (Event e : RecorderPlugin.SEEN) if (type.isInstance(e)) out.add(e);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Event> E only(Class<E> type) {
        List<Event> l = seen(type);
        assertEquals(1, l.size(), type.getSimpleName() + " 이 정확히 한 번 와야 한다: " + RecorderPlugin.SEEN);
        return (E) l.get(0);
    }

    /** 지원하는 24종을 모두 한 번씩 발행해 Bukkit 처리기에 닿는지 본다. */
    @Test
    void everyBridgedEventReachesTheBukkitHandler() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer tp = server.join("Dana");
            Player p = tp.player();
            server.tick(2);
            var bus = server.api().events();
            String w = "minecraft:overworld";
            Location at = new Location(w, 1, 64, 2);
            Location to = new Location(w, 5, 64, 6);
            var recorder = server.plugin("Recorder");
            Map<Class<?>, Supplier<org.domi.furnace.api.event.Event>> fire = new java.util.LinkedHashMap<>();
            fire.put(org.bukkit.event.player.PlayerChannelEvent.class, () -> new org.domi.furnace.api.event.player.PlayerChannelEvent(p, "mod:a",
                    org.domi.furnace.api.event.player.PlayerChannelEvent.Action.REGISTER));
            fire.put(org.bukkit.event.player.PlayerJoinEvent.class, () -> new org.domi.furnace.api.event.player.PlayerJoinEvent(p, Text.text("hi")));
            fire.put(org.bukkit.event.player.PlayerQuitEvent.class, () -> new org.domi.furnace.api.event.player.PlayerQuitEvent(p, Text.text("bye")));
            fire.put(org.bukkit.event.player.PlayerKickEvent.class, () -> new org.domi.furnace.api.event.player.PlayerKickEvent(p, Text.text("no")));
            fire.put(org.bukkit.event.player.AsyncPlayerChatEvent.class, () -> new org.domi.furnace.api.event.player.AsyncChatEvent(p, "yo", Text.text("yo")));
            fire.put(org.bukkit.event.player.PlayerMoveEvent.class, () -> new org.domi.furnace.api.event.player.PlayerMoveEvent(p, at, to));
            fire.put(org.bukkit.event.player.PlayerTeleportEvent.class, () -> new org.domi.furnace.api.event.player.PlayerTeleportEvent(p, at, to));
            fire.put(org.bukkit.event.player.PlayerRespawnEvent.class, () -> new org.domi.furnace.api.event.player.PlayerRespawnEvent(p, at, true));
            fire.put(org.bukkit.event.player.PlayerChangedWorldEvent.class, () -> new org.domi.furnace.api.event.player.PlayerChangedWorldEvent(p, w));
            fire.put(org.bukkit.event.player.PlayerGameModeChangeEvent.class, () -> new org.domi.furnace.api.event.player.PlayerGameModeChangeEvent(p, GameMode.CREATIVE));
            fire.put(org.bukkit.event.player.PlayerToggleSneakEvent.class, () -> new org.domi.furnace.api.event.player.PlayerToggleSneakEvent(p, true));
            fire.put(org.bukkit.event.player.PlayerToggleSprintEvent.class, () -> new org.domi.furnace.api.event.player.PlayerToggleSprintEvent(p, true));
            fire.put(org.bukkit.event.player.PlayerToggleFlightEvent.class, () -> new org.domi.furnace.api.event.player.PlayerToggleFlightEvent(p, true));
            fire.put(org.bukkit.event.player.PlayerCommandPreprocessEvent.class, () -> new org.domi.furnace.api.event.player.PlayerCommandPreprocessEvent(p, "/list"));
            fire.put(org.bukkit.event.player.PlayerLevelChangeEvent.class, () -> new org.domi.furnace.api.event.player.PlayerLevelChangeEvent(p, 1, 2));
            fire.put(org.bukkit.event.player.PlayerExpChangeEvent.class, () -> new org.domi.furnace.api.event.player.PlayerExpChangeEvent(p, 7));
            fire.put(org.bukkit.event.block.BlockBreakEvent.class, () -> new org.domi.furnace.api.event.block.BlockBreakEvent(w, new BlockPos(1, 64, 2), "minecraft:stone", p));
            fire.put(org.bukkit.event.entity.EntityDamageEvent.class, () -> new org.domi.furnace.api.event.entity.EntityDamageEvent(p, "minecraft:fall", 3f));
            fire.put(org.bukkit.event.server.PluginEnableEvent.class, () -> new org.domi.furnace.api.event.server.PluginEnableEvent(recorder));
            fire.put(org.bukkit.event.server.PluginDisableEvent.class, () -> new org.domi.furnace.api.event.server.PluginDisableEvent(recorder));
            fire.put(org.bukkit.event.server.ServerCommandEvent.class, () -> new org.domi.furnace.api.event.server.ServerCommandEvent(server.api().console(), "list"));
            fire.put(org.bukkit.event.server.ServerLoadEvent.class, org.domi.furnace.api.event.server.ServerLoadEvent::new);
            // 접속·RCON(LuckPerms) — Bukkit PlayerLoginEvent 는 Player 가 처음 생기는 PlayerCreateEvent 에서
            fire.put(org.bukkit.event.player.AsyncPlayerPreLoginEvent.class, () -> new org.domi.furnace.api.event.player.AsyncPlayerPreLoginEvent("Dana", p.uniqueId(), null));
            fire.put(org.bukkit.event.player.PlayerLoginEvent.class, () -> new org.domi.furnace.api.event.player.PlayerCreateEvent(p, p.connection()));
            fire.put(org.bukkit.event.server.RemoteServerCommandEvent.class, () -> new org.domi.furnace.api.event.server.RemoteServerCommandEvent(
                    ((org.domi.furnace.server.plugin.PluginHost) server.api()).rconSender(t -> {}), "list"));
            fire.put(org.bukkit.event.world.WorldLoadEvent.class, () -> new org.domi.furnace.api.event.world.WorldLoadEvent(w, "world"));
            fire.put(org.bukkit.event.world.WorldUnloadEvent.class, () -> new org.domi.furnace.api.event.world.WorldUnloadEvent(w, "world", false));
            fire.put(org.bukkit.event.world.ChunkLoadEvent.class, () -> new org.domi.furnace.api.event.world.ChunkLoadEvent(w, 3, 4));
            fire.put(org.bukkit.event.world.ChunkUnloadEvent.class, () -> new org.domi.furnace.api.event.world.ChunkUnloadEvent(w, 3, 4));
            var chest = new org.domi.furnace.api.inventory.InventorySnapshot("minecraft:generic_9x3", java.util.Collections.nCopies(63, org.domi.furnace.api.item.ItemStack.EMPTY));
            var stone = org.domi.furnace.api.item.ItemStack.of("minecraft:stone").withAmount(4);
            fire.put(org.bukkit.event.inventory.InventoryClickEvent.class, () -> new org.domi.furnace.api.event.inventory.InventoryClickEvent(p, chest, 3, 0, "pickup", org.domi.furnace.api.item.ItemStack.EMPTY));
            fire.put(org.bukkit.event.inventory.InventoryOpenEvent.class, () -> new org.domi.furnace.api.event.inventory.InventoryOpenEvent(p, chest));
            fire.put(org.bukkit.event.inventory.InventoryCloseEvent.class, () -> new org.domi.furnace.api.event.inventory.InventoryCloseEvent(p, chest));
            fire.put(org.bukkit.event.entity.EntityDeathEvent.class, () -> new org.domi.furnace.api.event.player.PlayerDeathEvent(p, Text.text("Dana died"), false));
            // 끌어 나누기는 시작·칸·칸·끝 네 번의 quick_craft 클릭이 모여 Bukkit 이벤트 하나가 된다
            Map<Class<?>, List<org.domi.furnace.api.event.Event>> sequences = Map.of(org.bukkit.event.inventory.InventoryDragEvent.class, List.of(
                    new org.domi.furnace.api.event.inventory.InventoryClickEvent(p, chest, -999, 0, "quick_craft", stone),
                    new org.domi.furnace.api.event.inventory.InventoryClickEvent(p, chest, 1, 1, "quick_craft", stone),
                    new org.domi.furnace.api.event.inventory.InventoryClickEvent(p, chest, 2, 1, "quick_craft", stone),
                    new org.domi.furnace.api.event.inventory.InventoryClickEvent(p, chest, -999, 2, "quick_craft", stone)));
            fire.put(org.bukkit.event.inventory.InventoryDragEvent.class, null);
            fire.put(org.bukkit.event.player.PlayerInteractEntityEvent.class,
                    () -> new org.domi.furnace.api.event.player.PlayerInteractEntityEvent(p, p, org.domi.furnace.api.item.ItemStack.EMPTY, true));

            // 시험이 빠뜨린 짝이 없는지: 다리가 아는 이벤트 = 이 시험이 발행한 이벤트
            assertEquals(EventBridge.bridgedEvents(), new HashSet<Class<?>>(fire.keySet()));

            for (var en : fire.entrySet()) {
                RecorderPlugin.SEEN.clear();
                if (en.getValue() == null) sequences.get(en.getKey()).forEach(bus::call);
                else bus.call(en.getValue().get());
                assertEquals(1, seen((Class<? extends Event>) en.getKey()).size(), en.getKey().getSimpleName() + " 이 처리기에 오지 않았다");
            }
        }
    }

    @Test
    void joinAndQuitMessagesRoundTrip() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            RecorderPlugin.ACTIONS.put("PlayerJoinEvent", e -> {
                var j = (org.bukkit.event.player.PlayerJoinEvent) e;
                j.setJoinMessage("§aWelcome " + j.getPlayer().getName());
            });
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            var j = only(org.bukkit.event.player.PlayerJoinEvent.class);
            assertEquals("Dana", ((org.bukkit.event.player.PlayerJoinEvent) j).getPlayer().getName());
            // 바닐라 접속 알림은 다른 사람에게 가는 방송 — 접속한 사람이 아닌 이에게 바뀐 문구가 간다
            TestPlayer other = server.join("Lee");
            server.tick(2);
            assertTrue(other.messageLines().stream().anyMatch(s -> s.contains("Welcome Lee")) || tp.messageLines().stream().anyMatch(s -> s.contains("Welcome Lee")),
                    "바꾼 접속 문구가 방송돼야 한다: " + tp.messageLines());
        }
    }

    @Test
    void chatMessageAndFormatWriteBack() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            RecorderPlugin.SEEN.clear();
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> {
                var c = (org.bukkit.event.player.AsyncPlayerChatEvent) e;
                c.setMessage(c.getMessage().toUpperCase());
                c.setFormat("[%1$s] %2$s");
            });
            tp.chat("hello");
            server.tick(3);
            only(org.bukkit.event.player.AsyncPlayerChatEvent.class);
            assertTrue(tp.messageLines().stream().anyMatch(s -> s.equals("[Dana] HELLO")), "서식과 내용이 되돌려져야 한다: " + tp.messageLines());
        }
    }

    @Test
    void cancellingChatStopsTheBroadcast() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            tp.clearMessages();
            RecorderPlugin.ACTIONS.put("AsyncPlayerChatEvent", e -> ((org.bukkit.event.Cancellable) e).setCancelled(true));
            tp.chat("secret");
            server.tick(3);
            assertFalse(tp.messageLines().stream().anyMatch(s -> s.contains("secret")), tp.messageLines().toString());
        }
    }

    @Test
    void writeBacksReachTheFurnaceEvents() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            Player p = server.join("Dana").player();
            server.tick(2);
            var bus = server.api().events();
            Location at = new Location("minecraft:overworld", 1, 64, 2);

            RecorderPlugin.ACTIONS.put("PlayerCommandPreprocessEvent", e -> ((org.bukkit.event.player.PlayerCommandPreprocessEvent) e).setMessage("/seed"));
            var cmd = bus.call(new org.domi.furnace.api.event.player.PlayerCommandPreprocessEvent(p, "/list"));
            assertEquals("/seed", cmd.message());

            RecorderPlugin.ACTIONS.put("PlayerExpChangeEvent", e -> ((org.bukkit.event.player.PlayerExpChangeEvent) e).setAmount(99));
            assertEquals(99, bus.call(new org.domi.furnace.api.event.player.PlayerExpChangeEvent(p, 7)).amount());

            RecorderPlugin.ACTIONS.put("EntityDamageEvent", e -> ((org.bukkit.event.entity.EntityDamageEvent) e).setDamage(1.5));
            assertEquals(1.5f, bus.call(new org.domi.furnace.api.event.entity.EntityDamageEvent(p, "minecraft:fall", 3f)).damage());

            RecorderPlugin.ACTIONS.put("ServerCommandEvent", e -> ((org.bukkit.event.server.ServerCommandEvent) e).setCommand("seed"));
            assertEquals("seed", bus.call(new org.domi.furnace.api.event.server.ServerCommandEvent(server.api().console(), "list")).command());

            RecorderPlugin.ACTIONS.put("PlayerKickEvent", e -> ((org.bukkit.event.player.PlayerKickEvent) e).setReason("§cBye"));
            assertEquals("Bye", bus.call(new org.domi.furnace.api.event.player.PlayerKickEvent(p, Text.text("no"))).reason().plainText());

            RecorderPlugin.ACTIONS.put("PlayerRespawnEvent", e -> {
                var r = (org.bukkit.event.player.PlayerRespawnEvent) e;
                var l = r.getRespawnLocation();
                l.setX(40);
                r.setRespawnLocation(l);
            });
            assertEquals(40.0, bus.call(new org.domi.furnace.api.event.player.PlayerRespawnEvent(p, at, true)).respawnLocation().x());

            RecorderPlugin.ACTIONS.put("PlayerToggleSneakEvent", e -> ((org.bukkit.event.Cancellable) e).setCancelled(true));
            assertTrue(bus.call(new org.domi.furnace.api.event.player.PlayerToggleSneakEvent(p, true)).isCancelled());

            RecorderPlugin.ACTIONS.put("BlockBreakEvent", e -> ((org.bukkit.event.Cancellable) e).setCancelled(true));
            assertTrue(bus.call(new org.domi.furnace.api.event.block.BlockBreakEvent("minecraft:overworld", new BlockPos(1, 64, 2), "minecraft:stone", p)).isCancelled());
        }
    }

    @Test
    void priorityIgnoreCancelledAndNativeObserversSeeTheSameOrder() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(orderJar()).start()) {
            Player p = server.join("Dana").player();
            server.tick(2);
            OrderPlugin.LOG.clear();
            var e = server.api().events().call(new org.domi.furnace.api.event.player.PlayerToggleSneakEvent(p, true));
            assertTrue(e.isCancelled());
            // LOWEST → LOW → NORMAL → HIGH(취소함) → HIGHEST(취소된 건 건너뜀) → MONITOR
            assertEquals(List.of("LOWEST cancelled=false", "LOW cancelled=false", "NORMAL cancelled=false", "HIGH cancelled=false", "MONITOR cancelled=true"),
                    OrderPlugin.LOG);
        }
    }

    @Test
    void unsupportedWritesSayWhatIsMissing() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            Player p = server.join("Dana").player();
            server.tick(2);
            RecorderPlugin.SEEN.clear();
            server.api().events().call(new org.domi.furnace.api.event.player.PlayerMoveEvent(p, new Location("minecraft:overworld", 0, 64, 0), new Location("minecraft:overworld", 1, 64, 0)));
            var mv = only(org.bukkit.event.player.PlayerMoveEvent.class);
            assertThrows(UnsupportedFeature.class, () -> ((org.bukkit.event.player.PlayerMoveEvent) mv).setTo(((org.bukkit.event.player.PlayerMoveEvent) mv).getFrom()));
            RecorderPlugin.SEEN.clear();
            server.api().events().call(new org.domi.furnace.api.event.player.AsyncChatEvent(p, "x", Text.text("x")));
            var chat = (org.bukkit.event.player.AsyncPlayerChatEvent) only(org.bukkit.event.player.AsyncPlayerChatEvent.class);
            assertEquals(1, chat.getRecipients().size(), "받는 사람은 그 시점의 접속자 전원이다");
            chat.getRecipients().clear();   // 받는 사람은 바꿀 수 있다 — ChatAdapterTest
        }
    }

    @Test
    void realPlayerActionsProduceBukkitEvents() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            assertEquals(1, seen(org.bukkit.event.player.PlayerJoinEvent.class).size());
            RecorderPlugin.SEEN.clear();
            tp.command("list");
            server.tick(2);
            var pre = (org.bukkit.event.player.PlayerCommandPreprocessEvent) only(org.bukkit.event.player.PlayerCommandPreprocessEvent.class);
            assertEquals("/list", pre.getMessage());
            tp.close();
            server.tick(3);
            assertEquals(1, seen(org.bukkit.event.player.PlayerQuitEvent.class).size());
        }
    }
}
