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
 * 받는 쪽은 주 스레드 표시를 하지 않는다(ProtocolLib 받는 리스너는 기본이 Netty 스레드, SYNC 만 라이브러리가 미룬다 — PR #389 검토 R1).
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
     * 바꿀 패킷(같은 것이면 그대로)이나 null(취소, 또는 hook 이 나중에 ctx 로 직접 넘김)로.
     */
    interface Hook {
        Object apply(ChannelHandlerContext ctx, Object packet);
    }

    private static List<Seen> inject(Connection c, Function<Object, Object> hook) throws Exception {
        return inject(c, (Hook) (ctx, p) -> hook.apply(p));
    }

    private static List<Seen> inject(Connection c, Hook hook) throws Exception {
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
                Object out = hook.apply(ctx, msg);
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
                assertTrue(seen.stream().noneMatch(Seen::primary), "기본 받는 리스너는 네트워크 스레드다(Paper 의 ProtocolLib 처럼 isAsync): " + seen);
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

    /** 서버가 처리한 명령(MONITOR, 취소 안 된 것) 순서. */
    private static List<String> commands(List<Got> got) {
        return got.stream().filter(g -> !g.cancelled() && g.packet() instanceof org.domi.furnace.api.packet.serverbound.ServerboundChatCommandPacket)
                .map(g -> ((org.domi.furnace.api.packet.serverbound.ServerboundChatCommandPacket) g.packet()).command()).toList();
    }

    /**
     * 리스너 안에서 넣은 패킷(receiveClientPacket → read(null, …))과 다른 종류로 바꾼 패킷은 그 자리에서 처리된다 — 원래 패킷과 뒤이어 온 패킷보다 먼저,
     * 원래 패킷도 한 번(PR #389 검토 R2·R3). SYNC 리스너처럼 라이브러리가 주 스레드로 미뤘다가 넘긴 패킷은 나중에 한 번 처리된다.
     */
    @Test
    void packetsInjectedByTheLibraryKeepPaperOrderAndDelayedOnesArriveOnce() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer tp = server.join("Order");
            server.tick(2);
            tp.sync();
            ProtocolLibBridge bridge = ProtocolLibBridge.start(owner(), () -> List.of(net.minecraft.network.protocol.game.ServerboundChatCommandPacket.class,
                    net.minecraft.network.protocol.game.ServerboundChatPacket.class));
            try {
                bridge.resync();
                List<Got> got = new CopyOnWriteArrayList<>();
                Furnace.server().packets().listen(owner(), EventPriority.MONITOR, e -> got.add(new Got(e.packet(), e.isCancelled(), e.isPluginPacket())),
                        PacketTypes.Serverbound.CHAT_COMMAND, PacketTypes.Serverbound.CHAT);
                Connection c = ((CraftPlayer) Bukkit.getPlayer("Order")).getHandle().connection.compatConnection();
                List<Boolean> laterPrimary = new CopyOnWriteArrayList<>();
                inject(c, (Hook) (ctx, packet) -> {
                    if (packet instanceof net.minecraft.network.protocol.game.ServerboundChatCommandPacket cmd) {
                        switch (cmd.command()) {
                            case "inject" -> c.read(null, new net.minecraft.network.protocol.game.ServerboundChatCommandPacket("injected"));   // receiveClientPacket
                            case "later" -> {   // SYNC 리스너: 주 스레드로 미뤘다가 넘긴다(ProtocolLib processInboundInternal)
                                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("Plin"), () -> {
                                    laterPrimary.add(Bukkit.isPrimaryThread());
                                    ctx.channel().eventLoop().execute(() -> ctx.fireChannelRead(packet));
                                });
                                return null;
                            }
                            default -> { }
                        }
                    }
                    if (packet instanceof net.minecraft.network.protocol.game.ServerboundChatPacket chat && chat.message().equals("swap")) {
                        try {
                            Thread.sleep(300);   // 뒤 프레임이 그동안 도착한다
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        return new net.minecraft.network.protocol.game.ServerboundChatCommandPacket("swapped");
                    }
                    return packet;
                });

                tp.command("inject");
                assertEquals(List.of("injected", "inject"), commands(got), "넣은 것이 먼저, 원래 것도 한 번: " + got);

                got.clear();
                tp.sendPacketNoWait(0x06, b -> {   // 채팅 "swap"
                    org.domi.furnace.server.net.io.Types.writeString(b, "swap");
                    b.writeLong(System.currentTimeMillis());
                    b.writeLong(0L);
                    b.writeBoolean(false);
                    org.domi.furnace.server.net.io.Types.writeVarInt(b, 0);
                    b.writeBytes(new byte[3]);
                });
                tp.sendPacketNoWait(0x04, b -> org.domi.furnace.server.net.io.Types.writeString(b, "after"));
                tp.sync();
                assertEquals(List.of("swapped", "after"), commands(got), "다른 종류로 바꾼 것이 뒤이어 온 패킷보다 먼저: " + got);

                got.clear();
                tp.command("later");
                server.tick(2);
                tp.sync();
                assertEquals(List.of(true), laterPrimary, "미룬 리스너는 주 스레드에서");
                assertEquals(List.of("later"), commands(got), "미룬 패킷은 나중에 한 번 처리된다: " + got);
                assertTrue(got.stream().anyMatch(g -> g.cancelled() && !g.plugin()), "원래 것은 취소로 보이고 다시 넣은 것은 플러그인 패킷이다: " + got);
            } finally {
                bridge.stop();
            }
        }
    }

    /** 서명 있는 채팅은 서명 내용이 같으면 손대지 않은 것으로 본다(배열 객체가 달라도 — 검토 S2). */
    @Test
    void signedChatWithTheSameSignatureBytesIsUntouched() {
        byte[] sig = new byte[256];
        var a = new org.domi.furnace.api.packet.serverbound.ServerboundChatPacket("x", java.time.Instant.EPOCH, 1L, sig, 0, 5);
        var b = new org.domi.furnace.api.packet.serverbound.ServerboundChatPacket("x", java.time.Instant.EPOCH, 1L, sig.clone(), 0, 5);
        assertTrue(ProtocolLibBridge.sameInbound(a, b));
        byte[] other = sig.clone();
        other[0] = 1;
        assertFalse(ProtocolLibBridge.sameInbound(a, new org.domi.furnace.api.packet.serverbound.ServerboundChatPacket("x", java.time.Instant.EPOCH, 1L, other, 0, 5)));
        assertFalse(ProtocolLibBridge.sameInbound(a, new org.domi.furnace.api.packet.serverbound.ServerboundChatPacket("y", java.time.Instant.EPOCH, 1L, sig, 0, 5)));
    }
}
