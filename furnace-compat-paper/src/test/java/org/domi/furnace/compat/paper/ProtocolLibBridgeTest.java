package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.ClientboundSetTimePacket;
import org.domi.furnace.api.packet.clientbound.ClientboundSystemChatPacket;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ProtocolLib 다리 2b-2: Furnace 서버가 스스로 보내는 패킷이 연결의 가로채기 자리({@code channel.eventLoop().execute(작업)})를 지나
 * 패킷 라이브러리가 보고·바꾸고·취소한다. 진짜 ProtocolLib 은 저장소 시험에 넣지 않았다 — 리스너 목록과 채널 프록시를 흉내 낸다(VirtualChannelTest 와 같은 방법).
 */
class ProtocolLibBridgeTest {

    @TempDir
    Path tmp;

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("plb.jar"), "name: Plb\nversion: '1'\nmain: plb.P\napi-version: '1.21'\n",
                Map.of("plb.P", "package plb; public final class P extends org.bukkit.plugin.java.JavaPlugin {}"), Set.of());
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    private static Connection connectionOf(String name) {
        return ((CraftPlayer) Bukkit.getPlayer(name)).getHandle().connection.compatConnection();
    }

    private static Field packetField(Object action) {
        for (Field f : action.getClass().getDeclaredFields()) {
            if (Packet.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new AssertionError("작업에 Packet 형식 필드가 없다: " + action.getClass());
    }

    /** 본 것 하나: 작업 안의 패킷, 그때 주 스레드였나. */
    record Seen(Object packet, boolean primary) {}

    /**
     * 패킷 라이브러리처럼 channel 필드를 이벤트 루프만 바꾼 프록시로 갈아 끼운다. hook 은 작업 안의 패킷을 받아 바꿀 패킷(같은 것이면 그대로)이나
     * null(취소)을 돌려준다.
     */
    private static List<Seen> install(Connection c, Function<Object, Object> hook) throws Exception {
        Channel real = c.channel;
        EventLoop realLoop = real.eventLoop();
        List<Seen> seen = new CopyOnWriteArrayList<>();
        EventLoop loop = (EventLoop) Proxy.newProxyInstance(EventLoop.class.getClassLoader(), new Class<?>[] {EventLoop.class}, (p, m, a) -> {
            if (m.getName().equals("execute")) {
                Runnable r = (Runnable) a[0];
                Field f = packetField(r);
                Object packet = f.get(r);
                seen.add(new Seen(packet, Bukkit.isPrimaryThread()));
                Object out = hook.apply(packet);
                if (out == null) return null;
                if (out != packet) f.set(r, out);
                realLoop.execute(r);
                return null;
            }
            return m.invoke(realLoop, a);
        });
        Channel proxy = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[] {Channel.class}, (p, m, a) ->
                m.getName().equals("eventLoop") ? loop : m.invoke(real, a));
        Field ch = Connection.class.getDeclaredField("channel");
        ch.setAccessible(true);
        ch.set(c, proxy);
        return seen;
    }

    private static BukkitPluginAdapter owner() {
        BukkitPluginAdapter a = CompatPluginManager.adapterOrNull(Bukkit.getPluginManager().getPlugin("Plb"));
        assertNotNull(a);
        return a;
    }

    @Test
    void serverSentPacketsPassTheLibraryWhichCanChangeCancelOrReplaceThem() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Lib");
            server.tick(2);
            tp.sync();
            Set<Class<?>> listened = new CopyOnWriteArraySet<>(List.of(net.minecraft.network.protocol.game.ClientboundSetTimePacket.class,
                    net.minecraft.network.protocol.game.ClientboundSystemChatPacket.class, net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket.class,
                    net.minecraft.network.protocol.game.ClientboundSetHealthPacket.class,
                    // 로그인 단계·번들은 Furnace 패킷 API 범위 밖 — 조용히 건너뛴다
                    net.minecraft.network.protocol.login.ClientboundGameProfilePacket.class));
            ProtocolLibBridge bridge = ProtocolLibBridge.start(owner(), () -> (Collection<Class<?>>) (Collection<?>) listened);
            try {
                bridge.resync();
                assertEquals(Set.of(PacketTypes.Clientbound.SET_TIME, PacketTypes.Clientbound.SYSTEM_CHAT, PacketTypes.Clientbound.SET_TITLE_TEXT,
                        PacketTypes.Clientbound.SET_HEALTH), bridge.types());

                Connection c = connectionOf("Lib");
                List<Seen> seen = install(c, packet -> {
                    // 시간은 바꾸고, "secret" 채팅은 취소하고, 제목은 다른 종류(채팅)로 바꾼다
                    if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetTimePacket t && t.getGameTime() == 1L) {
                        return new net.minecraft.network.protocol.game.ClientboundSetTimePacket(777L, 888L, true);
                    }
                    if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket s
                            && net.minecraft.network.chat.Component.furnace(s.content()).plainText().equals("secret")) {
                        return null;
                    }
                    if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket) {
                        return new net.minecraft.network.protocol.game.ClientboundSystemChatPacket(net.minecraft.network.chat.Component.literal("was title"), false);
                    }
                    return packet;
                });
                tp.clearReceived();
                var player = tp.player();
                // Furnace 패킷 API 로 보낸 것(플러그인 패킷)·서버 자신이 보내는 채팅 모두 라이브러리를 지난다
                Furnace.server().packets().send(player, new ClientboundSetTimePacket(1L, 2L));
                player.sendMessage(Text.text("secret"));
                player.sendMessage(Text.text("open"));
                player.sendPacket(new org.domi.furnace.api.packet.clientbound.ClientboundSetTitleTextPacket(Text.text("t")));
                Furnace.server().packets().send(player, GenericPacket.of(PacketTypes.Clientbound.SET_HEALTH,
                        PacketStruct.of(Map.of("health", 5f, "food", 11, "saturation", 0.5f))));
                tp.awaitPacket(PacketTypes.Clientbound.SET_HEALTH, g -> Integer.valueOf(11).equals(g.get("food")));
                tp.flushReceived();

                assertEquals(List.of("ClientboundSetTimePacket[gameTime=777, dayTime=888]"),
                        tp.received(PacketTypes.Clientbound.SET_TIME).stream().filter(t -> t.gameTime() != 0L && t.gameTime() == 777L || t.gameTime() == 1L)
                                .map(Object::toString).toList(), "라이브러리가 바꾼 시간이 나간다");
                List<String> chat = tp.received(PacketTypes.Clientbound.SYSTEM_CHAT).stream().map(m -> m.message().plainText()).toList();
                assertFalse(chat.contains("secret"), "라이브러리가 취소한 채팅은 나가지 않는다: " + chat);
                assertTrue(chat.contains("open"), chat.toString());
                assertTrue(chat.contains("was title"), "다른 종류로 바꾼 것은 그것이 나간다: " + chat);
                assertTrue(tp.received(PacketTypes.Clientbound.SET_TITLE_TEXT).isEmpty());
                assertEquals(5f, tp.received(PacketTypes.Clientbound.SET_HEALTH).getLast().get("health"), "손대지 않은 것은 그대로");
                assertTrue(seen.stream().allMatch(Seen::primary), "가로채는 동안은 주 스레드다(ProtocolLib 이 주 스레드 리스너 때문에 1틱 미루지 않게)");
                // 한 번씩만 본다: 바꾼 시간(라이브러리가 낸 것)이 다시 라이브러리를 지나지 않는다
                long times = seen.stream().filter(s -> s.packet() instanceof net.minecraft.network.protocol.game.ClientboundSetTimePacket t && t.getGameTime() == 777L).count();
                assertEquals(0, times, "라이브러리가 바꾼 결과는 다시 보이지 않는다");
                long replaced = seen.stream().filter(s -> s.packet() instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket m
                        && net.minecraft.network.chat.Component.furnace(m.content()).plainText().equals("was title")).count();
                assertEquals(0, replaced, "다른 종류로 바꾼 결과도 다시 보이지 않는다");

                // 플러그인이 NMS 로 보낸 것(2b-1 경로)은 라이브러리가 한 번만 본다 — Furnace 쪽 다리가 또 보이지 않는다
                seen.clear();
                tp.clearReceived();
                ((CraftPlayer) Bukkit.getPlayer("Lib")).getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundSetTimePacket(42L, 43L, true));
                tp.awaitPacket(PacketTypes.Clientbound.SET_TIME, t -> t.gameTime() == 42L);
                assertEquals(1, seen.stream().filter(s -> s.packet() instanceof net.minecraft.network.protocol.game.ClientboundSetTimePacket t && t.getGameTime() == 42L).count());

                // 라이브러리가 듣지 않게 되면(리스너 제거) 구독도 풀린다
                listened.clear();
                bridge.resync();
                assertEquals(Set.of(), bridge.types());
                seen.clear();
                Furnace.server().packets().send(player, new ClientboundSystemChatPacket(Text.text("plain"), false));
                tp.awaitPacket(PacketTypes.Clientbound.SYSTEM_CHAT, m -> m.message().plainText().equals("plain"));
                assertTrue(seen.isEmpty(), "듣지 않는 종류는 라이브러리를 지나지 않는다: " + seen);
            } finally {
                bridge.stop();
            }
            assertTrue(ProtocolLibBridge.active() == null);
        }
    }

    @Test
    void withoutAnInjectedChannelNothingChanges() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Raw");
            server.tick(2);
            tp.sync();
            ProtocolLibBridge bridge = ProtocolLibBridge.start(owner(), () -> List.of(net.minecraft.network.protocol.game.ClientboundSetTimePacket.class));
            try {
                bridge.resync();
                tp.clearReceived();
                // 채널을 갈아 끼우지 않은 연결(아직 주입 전) — 원래 패킷 그대로
                Furnace.server().packets().send(tp.player(), new ClientboundSetTimePacket(5L, 6L));
                assertEquals(6L, tp.awaitPacket(PacketTypes.Clientbound.SET_TIME, t -> t.gameTime() == 5L).dayTime());
            } finally {
                bridge.stop();
            }
        }
    }
}
