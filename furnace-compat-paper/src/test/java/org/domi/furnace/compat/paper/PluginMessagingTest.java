package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerChannelEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.ChannelNameTooLongException;
import org.bukkit.plugin.messaging.ChannelNotRegisteredException;
import org.bukkit.plugin.messaging.MessageTooLargeException;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.messaging.PluginMessageListenerRegistration;
import org.bukkit.plugin.messaging.ReservedChannelException;
import org.bukkit.plugin.messaging.StandardMessenger;
import org.domi.furnace.api.packet.clientbound.ClientboundCustomPayloadPacket;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.MessagingPlugin;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** H4 묶음 E: Bukkit 플러그인 메시징(Messenger·PluginMessageListener·sendPluginMessage·BungeeCord 채널·채널 등록 이벤트). */
class PluginMessagingTest {

    @TempDir Path tmp;

    @BeforeEach
    void reset() {
        MessagingPlugin.REGISTERED.clear();
        RecorderPlugin.reset();
    }

    private Path messagingJar() throws Exception {
        return Jars.bukkit(tmp.resolve("messaging.jar"), "plugin.yml", """
                name: Messaging
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.MessagingPlugin
                api-version: '1.21'
                """, MessagingPlugin.class);
    }

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, java.util.Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
    }

    private static JavaPlugin plugin(FurnaceTestServer server, String name) {
        return ((BukkitPluginAdapter) server.plugin(name)).javaPlugin();
    }

    private static List<String> channelsIn(TestPlayer tp, String which) {
        List<String> out = new ArrayList<>();
        for (ClientboundCustomPayloadPacket m : tp.pluginMessages()) {
            if (!m.channel().equals(which)) continue;
            out.addAll(Arrays.asList(new String(m.data(), StandardCharsets.UTF_8).split("\0")));
        }
        return out;
    }

    private static List<ClientboundCustomPayloadPacket> on(TestPlayer tp, String channel) {
        return tp.pluginMessages().stream().filter(m -> m.channel().equals(channel)).toList();
    }

    private record Got(String channel, String player, byte[] data, boolean primary, String thread) {}

    // ------------------------------------------------------------------ 이름 검사(서버 없이)

    @Test
    void channelNamesAreValidatedAndBungeeCordIsTranslatedBothWays() {
        assertEquals("bungeecord:main", StandardMessenger.validateAndCorrectChannel("BungeeCord"));
        assertEquals("BungeeCord", StandardMessenger.validateAndCorrectChannel("bungeecord:main"));
        assertEquals("my:chan", StandardMessenger.validateAndCorrectChannel("my:chan"));
        assertThrows(IllegalArgumentException.class, () -> StandardMessenger.validateAndCorrectChannel("nocolon"));
        assertThrows(IllegalArgumentException.class, () -> StandardMessenger.validateAndCorrectChannel("My:Chan"));
        assertThrows(ChannelNameTooLongException.class, () -> StandardMessenger.validateAndCorrectChannel("a:" + "b".repeat(63)));
        assertEquals(64, Messenger.MAX_CHANNEL_SIZE);
        assertEquals(1048576, Messenger.MAX_MESSAGE_SIZE);
        StandardMessenger m = new StandardMessenger();
        assertTrue(m.isReservedChannel("minecraft:register"));
        assertTrue(m.isReservedChannel("myminecraft:x"));
        assertFalse(m.isReservedChannel("minecraft:brand"));
        assertFalse(m.isReservedChannel("BungeeCord"));
    }

    // ------------------------------------------------------------------ 서버

    @Test
    void outgoingNeedsRegistrationAndOnlyReachesClientsListeningOnTheChannel() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(messagingJar()).start()) {
            JavaPlugin pl = plugin(server, "Messaging");
            Messenger messenger = Bukkit.getMessenger();
            assertSame(messenger, Bukkit.getServer().getMessenger());
            TestPlayer tp = server.join("Dana");
            Player dana = Bukkit.getPlayerExact("Dana");
            byte[] hello = "hi".getBytes(StandardCharsets.UTF_8);

            assertThrows(ChannelNotRegisteredException.class, () -> dana.sendPluginMessage(pl, "test:out", hello));
            assertThrows(ReservedChannelException.class, () -> messenger.registerOutgoingPluginChannel(pl, "minecraft:register"));
            messenger.registerOutgoingPluginChannel(pl, "test:out");
            assertTrue(messenger.isOutgoingChannelRegistered(pl, "test:out"));
            assertEquals(Set.of("test:out"), messenger.getOutgoingChannels(pl));
            assertThrows(MessageTooLargeException.class, () -> dana.sendPluginMessage(pl, "test:out", new byte[Messenger.MAX_MESSAGE_SIZE + 1]));

            // 클라이언트가 알리지 않은 채널로는 보내지 않는다(CraftPlayer 와 같다)
            dana.sendPluginMessage(pl, "test:out", hello);
            tp.sync();
            assertTrue(on(tp, "test:out").isEmpty(), "알리지 않은 채널로 보냈다");

            tp.registerChannels("test:out");
            assertEquals(Set.of("test:out"), dana.getListeningPluginChannels());
            dana.sendPluginMessage(pl, "test:out", hello);
            Bukkit.getServer().sendPluginMessage(pl, "test:out", "all".getBytes(StandardCharsets.UTF_8));
            tp.sync();
            List<ClientboundCustomPayloadPacket> got = on(tp, "test:out");
            assertEquals(2, got.size(), String.valueOf(tp.pluginMessages()));
            assertArrayEquals(hello, got.get(0).data());
            assertEquals("all", new String(got.get(1).data(), StandardCharsets.UTF_8));
            assertEquals(Set.of("test:out"), Bukkit.getServer().getListeningPluginChannels());

            messenger.unregisterOutgoingPluginChannel(pl, "test:out");
            assertThrows(ChannelNotRegisteredException.class, () -> dana.sendPluginMessage(pl, "test:out", hello));
        }
    }

    @Test
    void incomingMessagesRunOnThePrimaryThreadAndRegistrationIsAnnounced() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(messagingJar()).start()) {
            JavaPlugin pl = plugin(server, "Messaging");
            Messenger messenger = Bukkit.getMessenger();
            TestPlayer early = server.join("Early");
            List<Got> got = new CopyOnWriteArrayList<>();
            PluginMessageListener listener = (channel, player, message) ->
                    got.add(new Got(channel, player.getName(), message, Bukkit.isPrimaryThread(), Thread.currentThread().getName()));
            PluginMessageListenerRegistration reg = messenger.registerIncomingPluginChannel(pl, "test:in", listener);
            assertTrue(reg.isValid());
            assertEquals("test:in", reg.getChannel());
            assertSame(pl, reg.getPlugin());
            assertThrows(IllegalArgumentException.class, () -> messenger.registerIncomingPluginChannel(pl, "test:in", listener), "같은 등록 두 번");
            assertEquals(Set.of("test:in"), messenger.getIncomingChannels());
            assertTrue(messenger.isIncomingChannelRegistered(pl, "test:in"));
            assertEquals(Set.of("test:in"), server.api().pluginChannels(), "Furnace 쪽 채널 등록");

            // 이미 들어온 사람에게도, 새로 들어오는 사람에게도 minecraft:register 로 알린다
            early.sync();
            assertTrue(channelsIn(early, "minecraft:register").contains("test:in"), String.valueOf(early.pluginMessages()));
            TestPlayer late = server.join("Late");
            late.sync();
            assertTrue(channelsIn(late, "minecraft:register").contains("test:in"));

            late.sendPluginMessage("test:in", new byte[]{1, 2, 3});
            late.sendPluginMessage("test:other", new byte[]{9});   // 등록 없는 채널은 아무도 받지 않는다
            assertEquals(1, got.size(), String.valueOf(got));
            Got g = got.get(0);
            assertEquals("test:in", g.channel());
            assertEquals("Late", g.player());
            assertArrayEquals(new byte[]{1, 2, 3}, g.data());
            assertTrue(g.primary(), "리스너는 주 스레드(전역)에서 돈다: " + g.thread());

            // 리스너 예외는 로그만 남고 다음 리스너는 계속 받는다
            List<String> second = new CopyOnWriteArrayList<>();
            PluginMessageListener boom = (c, p, m) -> { throw new IllegalStateException("boom"); };
            messenger.registerIncomingPluginChannel(pl, "test:in", boom);
            messenger.registerIncomingPluginChannel(pl, "test:in", (c, p, m) -> second.add(c));
            late.sendPluginMessage("test:in", new byte[]{4});
            assertEquals(2, got.size());
            assertEquals(List.of("test:in"), second);

            // 리스너를 모두 빼면 클라이언트에 minecraft:unregister
            early.clearPluginMessages();
            messenger.unregisterIncomingPluginChannel(pl, "test:in", listener);
            assertFalse(reg.isValid());
            assertEquals(Set.of("test:in"), server.api().pluginChannels(), "아직 리스너가 남았다");
            messenger.unregisterIncomingPluginChannel(pl, "test:in");
            assertTrue(messenger.getIncomingChannels().isEmpty());
            assertTrue(server.api().pluginChannels().isEmpty());
            early.sync();
            assertEquals(List.of("test:in"), channelsIn(early, "minecraft:unregister"));
            late.sendPluginMessage("test:in", new byte[]{5});
            assertEquals(2, got.size(), "등록을 푼 뒤에는 받지 않는다");
        }
    }

    @Test
    void bungeeCordChannelUsesTheLegacyNameOnTheBukkitSide() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(messagingJar()).start()) {
            JavaPlugin pl = plugin(server, "Messaging");
            Messenger messenger = Bukkit.getMessenger();
            // 프록시(BungeeCord·Velocity)는 구성 단계에서 bungeecord:main 을 알린다
            TestPlayer tp = server.join("Proxied", "bungeecord:main");
            Player p = Bukkit.getPlayerExact("Proxied");
            assertEquals(Set.of("BungeeCord"), p.getListeningPluginChannels());

            messenger.registerOutgoingPluginChannel(pl, "BungeeCord");
            assertEquals(Set.of("bungeecord:main"), messenger.getOutgoingChannels(pl));
            assertTrue(messenger.isOutgoingChannelRegistered(pl, "BungeeCord"));
            List<String> got = new CopyOnWriteArrayList<>();
            messenger.registerIncomingPluginChannel(pl, "BungeeCord", (c, who, m) -> got.add(c + " " + new String(m, StandardCharsets.UTF_8)));
            assertEquals(Set.of("bungeecord:main"), messenger.getIncomingChannels());
            assertEquals(Set.of("bungeecord:main"), server.api().pluginChannels());

            byte[] connect = "Connect".getBytes(StandardCharsets.UTF_8);
            p.sendPluginMessage(pl, "BungeeCord", connect);
            tp.sync();
            List<ClientboundCustomPayloadPacket> out = on(tp, "bungeecord:main");
            assertEquals(1, out.size(), String.valueOf(tp.pluginMessages()));
            assertArrayEquals(connect, out.get(0).data());

            tp.sendPluginMessage("bungeecord:main", "PlayerCount".getBytes(StandardCharsets.UTF_8));
            assertEquals(List.of("BungeeCord PlayerCount"), got, "리스너는 옛 이름 BungeeCord 로 받는다");
        }
    }

    @Test
    void disablingThePluginDropsItsRegistrations() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(messagingJar()).start()) {
            JavaPlugin pl = plugin(server, "Messaging");
            Messenger messenger = Bukkit.getMessenger();
            messenger.registerOutgoingPluginChannel(pl, "test:out");
            messenger.registerIncomingPluginChannel(pl, "test:in", (c, p, m) -> {});
            TestPlayer tp = server.join("Dana");
            server.api().plugins().disable(server.plugin("Messaging"));
            assertFalse(pl.isEnabled());
            assertTrue(messenger.getIncomingChannels().isEmpty());
            assertTrue(messenger.getOutgoingChannels().isEmpty());
            assertTrue(server.api().pluginChannels().isEmpty());
            tp.sync();
            assertTrue(channelsIn(tp, "minecraft:unregister").contains("test:in"));
            Player dana = Bukkit.getPlayerExact("Dana");
            assertThrows(IllegalArgumentException.class, () -> dana.sendPluginMessage(pl, "test:out", new byte[0]), "꺼진 플러그인은 보낼 수 없다");
        }
    }

    @Test
    void clientChannelRegistrationFiresBukkitChannelEvents() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(messagingJar()).pluginJar(recorderJar()).start()) {
            // 구성 단계에서 알린 채널은 접속 알림(PlayerJoinEvent) 앞에 온다
            TestPlayer tp = server.join("Modded", "mod:a", "bungeecord:main");
            tp.registerChannels("mod:b", "mod:a");   // mod:a 는 이미 있다 — 다시 오지 않는다
            tp.unregisterChannels("mod:a", "mod:zzz");   // 없는 채널은 이벤트가 없다
            List<String> order = new ArrayList<>();
            for (Event e : RecorderPlugin.SEEN) {
                if (e instanceof PlayerJoinEvent) order.add("join");
                else if (e instanceof PlayerRegisterChannelEvent r) order.add("+" + r.getChannel());
                else if (e instanceof PlayerUnregisterChannelEvent u) order.add("-" + u.getChannel());
                else if (e instanceof PlayerChannelEvent) order.add("?");
            }
            assertEquals(List.of("+mod:a", "+BungeeCord", "join", "+mod:b", "-mod:a"), order);
            // PlayerRegisterChannelEvent 만 받는 처리기에는 거둔 이벤트가 오지 않는다
            assertEquals(List.of("Modded mod:a", "Modded BungeeCord", "Modded mod:b"), MessagingPlugin.REGISTERED);
            assertEquals(Set.of("BungeeCord", "mod:b"), Bukkit.getPlayerExact("Modded").getListeningPluginChannels());
        }
    }
}
