package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
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
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ProtocolLib 다리 2b-3(T-180): 클라이언트가 보낸 패킷이 패킷 라이브러리의 받는 처리기("decoder" 뒤)를 지나 리스너가 보고·바꾸고·취소하고·다른 종류로 바꾼다.
 * 라이브러리는 진짜 ProtocolLib 처럼 연결의 channel 필드를 프록시로 바꾸고, 가상 채널 파이프라인의 "decoder" 뒤에 받는 처리기를 끼운다.
 */
class ProtocolLibInboundTest {

    @TempDir
    Path tmp;

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("plin.jar"), "name: Plin\nversion: '1'\nmain: plin.P\napi-version: '1.21'\n",
                Map.of("plin.P", "package plin; public final class P extends org.bukkit.plugin.java.JavaPlugin {}"), Set.of());
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    private static BukkitPluginAdapter owner() {
        BukkitPluginAdapter a = CompatPluginManager.adapterOrNull(Bukkit.getPluginManager().getPlugin("Plin"));
        assertNotNull(a);
        return a;
    }

    /** 본 것 하나: 받은 패킷, 그때 주 스레드였나. */
    record Seen(Object packet, boolean primary) {}

    /** Furnace 쪽에서 서버가 처리할 패킷(MONITOR): 값, 취소됐나, 플러그인이 넣었나. */
    record Got(Object packet, boolean cancelled, boolean plugin) {}

    /**
     * 진짜 ProtocolLib 처럼 주입한다: channel 필드를 프록시로(보내는 쪽은 그대로 통과), 파이프라인 "decoder" 뒤에 받는 처리기. hook 은 받은 패킷을
     * 바꿀 패킷(같은 것이면 그대로)이나 null(취소)로.
     */
    private static List<Seen> inject(Connection c, Function<Object, Object> hook) throws Exception {
        Channel real = c.channel;
        EventLoop realLoop = real.eventLoop();
        Channel proxy = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[] {Channel.class}, (p, m, a) ->
                m.getName().equals("eventLoop") ? realLoop : m.invoke(real, a));
        Field ch = Connection.class.getDeclaredField("channel");
        ch.setAccessible(true);
        ch.set(c, proxy);
        List<Seen> seen = new CopyOnWriteArrayList<>();
        real.pipeline().addAfter("decoder", "protocol_lib_inbound_interceptor", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                seen.add(new Seen(msg, Bukkit.isPrimaryThread()));
                Object out = hook.apply(msg);
                if (out != null) ctx.fireChannelRead(out);
            }
        });
        return seen;
    }

    @Test
    void clientPacketsPassTheLibraryWhichCanSeeChangeCancelOrReplaceThem() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("In");
            server.tick(2);
            tp.sync();
            Set<Class<?>> listened = new CopyOnWriteArraySet<>(List.of(net.minecraft.network.protocol.game.ServerboundChatCommandPacket.class,
                    net.minecraft.network.protocol.game.ServerboundChatPacket.class, net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos.class,
                    net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot.class, net.minecraft.network.protocol.game.ServerboundRenameItemPacket.class,
                    // 아직 못 보여 주는 종류 — 조용히 빠지고 INFO 한 줄
                    net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket.class,
                    net.minecraft.network.protocol.game.ServerboundContainerClickPacket.class));
            ProtocolLibBridge bridge = ProtocolLibBridge.start(owner(), () -> (Collection<Class<?>>) (Collection<?>) listened);
            PacketType<?> rename = PacketTypes.byName(PacketDirection.SERVERBOUND, "minecraft:rename_item");
            try {
                bridge.resync();
                assertEquals(Set.of(PacketTypes.Serverbound.CHAT_COMMAND, PacketTypes.Serverbound.CHAT, PacketTypes.Serverbound.MOVE_PLAYER, rename),
                        bridge.inboundTypes());
                assertEquals(Set.of(), bridge.types(), "보내는 쪽 구독은 없다");

                // 서버가 처리할 패킷(라이브러리 뒤)을 본다
                List<Got> got = new CopyOnWriteArrayList<>();
                Furnace.server().packets().listen(owner(), EventPriority.MONITOR, e -> got.add(new Got(e.packet(), e.isCancelled(), e.isPluginPacket())),
                        PacketTypes.Serverbound.CHAT_COMMAND, PacketTypes.Serverbound.CHAT, PacketTypes.Serverbound.SWING, rename);

                Connection c = ((CraftPlayer) Bukkit.getPlayer("In")).getHandle().connection.compatConnection();
                List<Seen> seen = inject(c, packet -> {
                    if (packet instanceof net.minecraft.network.protocol.game.ServerboundChatCommandPacket cmd) {
                        if (cmd.command().startsWith("secret")) return null;   // 취소
                        if (cmd.command().equals("plin one")) return new net.minecraft.network.protocol.game.ServerboundChatCommandPacket("plin two");
                    }
                    if (packet instanceof net.minecraft.network.protocol.game.ServerboundChatPacket chat && chat.message().equals("swap")) {
                        return new net.minecraft.network.protocol.game.ServerboundSwingPacket(net.minecraft.world.InteractionHand.OFF_HAND);
                    }
                    if (packet instanceof net.minecraft.network.protocol.game.ServerboundRenameItemPacket r && r.getName().equals("a")) {
                        return new net.minecraft.network.protocol.game.ServerboundRenameItemPacket("b");
                    }
                    return packet;
                });

                tp.command("plin one");
                tp.command("secret stuff");
                tp.chat("swap");
                var at = tp.player().location();
                tp.moveTo(at.x(), at.y(), at.z(), true);
                tp.sendPacket(42, b -> org.domi.furnace.server.net.io.Types.writeString(b, "a"));   // rename_item(범용 종류)
                tp.sync();

                List<String> commands = got.stream().filter(g -> g.packet() instanceof org.domi.furnace.api.packet.serverbound.ServerboundChatCommandPacket)
                        .map(g -> ((org.domi.furnace.api.packet.serverbound.ServerboundChatCommandPacket) g.packet()).command() + (g.cancelled() ? "(취소)" : "")).toList();
                assertEquals(List.of("plin two", "secret stuff(취소)"), commands, "라이브러리가 바꾼 명령이 서버에 가고, 취소한 것은 처리되지 않는다");
                assertTrue(got.stream().anyMatch(g -> g.packet() instanceof org.domi.furnace.api.packet.serverbound.ServerboundChatPacket ch
                        && ch.message().equals("swap") && g.cancelled()), "다른 종류로 바꾼 원래 채팅은 처리되지 않는다: " + got);
                assertTrue(got.stream().anyMatch(g -> g.packet() instanceof org.domi.furnace.api.packet.serverbound.ServerboundSwingPacket s
                        && s.hand() == Hand.OFF_HAND && g.plugin()), "대신 넣은 팔 휘두르기가 서버에 간다: " + got);
                assertTrue(got.stream().anyMatch(g -> g.packet() instanceof GenericPacket r && r.type() == rename && "b".equals(r.get("name")) && !g.cancelled()),
                        "범용 종류도 바꾼 값이 간다: " + got);
                assertTrue(seen.stream().anyMatch(s -> s.packet() instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos p
                        && p.getX(Double.NaN) == at.x() && p.isOnGround()), "이동 패킷을 바닐라 shim 으로 본다: " + seen);
                assertTrue(seen.stream().allMatch(Seen::primary), "가로채는 동안은 주 스레드다(ProtocolLib 이 1틱 미루지 않게)");
                assertFalse(tp.messageLines().stream().anyMatch(l -> l.contains("swap")), "취소된 채팅은 퍼지지 않는다: " + tp.messageLines());

                // receiveClientPacket(라이브러리가 이미 리스너를 불렀다) → Connection#read(null, 패킷) → 서버. 라이브러리에 다시 보이지 않는다
                seen.clear();
                got.clear();
                c.read(null, new net.minecraft.network.protocol.game.ServerboundChatCommandPacket("plin three"));
                tp.sync();
                assertTrue(got.stream().anyMatch(g -> g.packet() instanceof org.domi.furnace.api.packet.serverbound.ServerboundChatCommandPacket cmd
                        && cmd.command().equals("plin three") && g.plugin() && !g.cancelled()), "넣은 패킷이 서버에 간다: " + got);
                assertTrue(seen.stream().noneMatch(s -> s.packet() instanceof net.minecraft.network.protocol.game.ServerboundChatCommandPacket),
                        "넣은 패킷은 라이브러리에 다시 보이지 않는다: " + seen);

                // 라이브러리가 듣지 않게 되면 구독도 풀린다
                listened.clear();
                bridge.resync();
                assertEquals(Set.of(), bridge.inboundTypes());
                seen.clear();
                tp.command("plin four");
                assertTrue(seen.isEmpty(), "듣지 않는 종류는 라이브러리를 지나지 않는다: " + seen);
            } finally {
                bridge.stop();
            }
        }
    }

    @Test
    void withoutAnInjectedChannelClientPacketsAreUntouched() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Plain");
            server.tick(2);
            tp.sync();
            ProtocolLibBridge bridge = ProtocolLibBridge.start(owner(), () -> List.of(net.minecraft.network.protocol.game.ServerboundChatCommandPacket.class));
            try {
                bridge.resync();
                List<Got> got = new CopyOnWriteArrayList<>();
                Furnace.server().packets().listen(owner(), EventPriority.MONITOR, e -> got.add(new Got(e.packet(), e.isCancelled(), e.isPluginPacket())),
                        PacketTypes.Serverbound.CHAT_COMMAND);
                tp.command("plain one");
                assertEquals(1, got.size(), got.toString());
                assertFalse(got.get(0).cancelled());
            } finally {
                bridge.stop();
            }
        }
    }
}
