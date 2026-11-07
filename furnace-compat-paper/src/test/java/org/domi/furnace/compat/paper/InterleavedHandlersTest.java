package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.compat.paper.fixtures.InterleavePlugin;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Bukkit 처리기(LOW, HIGH) 사이에 Furnace 고유 처리기(NORMAL)가 값을 바꿀 때 — 뒤의 Bukkit 처리기가 바뀐 값을 보고, 되돌려 쓰기가 낡은 값으로 덮지 않아야 한다. */
class InterleavedHandlersTest {

    @TempDir Path tmp;

    @BeforeEach
    void reset() { InterleavePlugin.LOG.clear(); }

    private FurnaceTestServer server() throws Exception {
        Path jar = Jars.bukkit(tmp.resolve("interleave.jar"), "plugin.yml", """
                name: Interleave
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.InterleavePlugin
                api-version: '1.21'
                """, InterleavePlugin.class);
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    @Test
    void valuesChangedByAFurnaceHandlerSurviveAndAreSeenByLaterBukkitHandlers() throws Exception {
        try (FurnaceTestServer server = server()) {
            var p = server.join("Dana").player();
            server.tick(2);
            var owner = server.plugin("Interleave");
            var bus = server.api().events();
            InterleavePlugin.LOG.clear();

            bus.subscribe(owner, org.domi.furnace.api.event.player.PlayerExpChangeEvent.class, EventPriority.NORMAL, false, e -> e.setAmount(99));
            assertEquals(99, bus.call(new org.domi.furnace.api.event.player.PlayerExpChangeEvent(p, 7)).amount());
            assertTrue(InterleavePlugin.LOG.containsAll(java.util.List.of("exp LOW 7", "exp HIGH 99")), InterleavePlugin.LOG.toString());

            bus.subscribe(owner, org.domi.furnace.api.event.entity.EntityDamageEvent.class, EventPriority.NORMAL, false, e -> e.setDamage(5f));
            assertEquals(5f, bus.call(new org.domi.furnace.api.event.entity.EntityDamageEvent(p, "minecraft:fall", 3f)).damage());
            assertTrue(InterleavePlugin.LOG.contains("dmg HIGH 5.0"), InterleavePlugin.LOG.toString());

            bus.subscribe(owner, org.domi.furnace.api.event.player.PlayerJoinEvent.class, EventPriority.NORMAL, false, e -> e.joinMessage(Text.text("welcome")));
            assertEquals("welcome", bus.call(new org.domi.furnace.api.event.player.PlayerJoinEvent(p, Text.text("orig"))).joinMessage().plainText());
            assertTrue(InterleavePlugin.LOG.contains("join LOW orig") && InterleavePlugin.LOG.contains("join HIGH welcome"), InterleavePlugin.LOG.toString());

            bus.subscribe(owner, org.domi.furnace.api.event.player.PlayerCommandPreprocessEvent.class, EventPriority.NORMAL, false, e -> e.message("/seed"));
            assertEquals("/seed", bus.call(new org.domi.furnace.api.event.player.PlayerCommandPreprocessEvent(p, "/list")).message());
            assertTrue(InterleavePlugin.LOG.contains("cmd HIGH /seed"), InterleavePlugin.LOG.toString());

            bus.subscribe(owner, org.domi.furnace.api.event.player.PlayerRespawnEvent.class, EventPriority.NORMAL, false,
                    e -> e.respawnLocation(new Location("minecraft:overworld", 77, 64, 0)));
            assertEquals(77.0, bus.call(new org.domi.furnace.api.event.player.PlayerRespawnEvent(p, new Location("minecraft:overworld", 1, 64, 2), true)).respawnLocation().x());
            assertTrue(InterleavePlugin.LOG.contains("respawn HIGH 77.0"), InterleavePlugin.LOG.toString());
        }
    }

    /** Bukkit LOW 가 서식을 정하고 Furnace NORMAL 이 renderer 를 바꾸면, 서식을 안 건드린 Bukkit HIGH 가 낡은 서식으로 덮어쓰지 않는다. */
    @Test
    void aFurnaceRendererSetBetweenBukkitHandlersWins() throws Exception {
        try (FurnaceTestServer server = server()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            server.api().events().subscribe(server.plugin("Interleave"), org.domi.furnace.api.event.player.AsyncChatEvent.class, EventPriority.NORMAL, false,
                    e -> e.renderer((source, name, message, viewer) -> Text.text("FURNACE: " + message.plainText())));
            tp.clearMessages();
            tp.chat("hello");
            server.tick(3);
            assertTrue(tp.messageLines().contains("FURNACE: hello"), tp.messageLines().toString());
            assertTrue(InterleavePlugin.LOG.contains("chat LOW hello") && InterleavePlugin.LOG.contains("chat HIGH hello"), InterleavePlugin.LOG.toString());
        }
    }
}
