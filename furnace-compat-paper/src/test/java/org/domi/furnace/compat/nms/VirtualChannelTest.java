package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ProtocolLib 다리 2b-1: 접속마다 가상 채널(활성·바닐라 처리기 이름), 보내기는 {@code channel.eventLoop().execute(작업)} 을 지난다
 * (패킷 라이브러리가 필드를 갈아 끼워 패킷을 보고 바꾸고 취소하는 자리), 접속이 끝나면 닫힌다.
 * 진짜 ProtocolLib 은 저장소 시험에 넣지 않았다 — 같은 가로채기를 {@code Proxy} 로 흉내 낸다(실제 jar 로는 따로 확인, 문서 참고).
 */
class VirtualChannelTest {

    @TempDir
    Path tmp;

    private FurnaceTestServer start() throws Exception {
        // 호환 계층은 버킷 플러그인이 있을 때만 켜진다 — 빈 플러그인 하나
        Path jar = org.domi.furnace.compat.paper.fixtures.JarCompiler.jar(tmp.resolve("vc.jar"), "name: Vc\nversion: '1'\nmain: vc.P\napi-version: '1.21'\n",
                java.util.Map.of("vc.P", "package vc; public final class P extends org.bukkit.plugin.java.JavaPlugin {}"), java.util.Set.of());
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    private static Field channelField() throws Exception {
        Field f = Connection.class.getDeclaredField("channel");
        f.setAccessible(true);
        return f;
    }

    private static Connection connectionOf(String name) throws Exception {
        ServerPlayer handle = ((CraftPlayer) Bukkit.getPlayer(name)).getHandle();
        Field f = net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
        f.setAccessible(true);
        return (Connection) f.get(handle.connection);
    }

    @Test
    void pipelineHasTheVanillaNamesAndPhaseInfo() throws Exception {
        try (FurnaceTestServer server = start()) {
            server.join("Pipe");
            server.tick(2);
            Connection c = connectionOf("Pipe");
            Channel ch = c.channel;
            assertTrue(ch.isActive());
            assertEquals(List.of("furnace_sink", "decoder", "encoder", "packet_handler"), ch.pipeline().names().stream().filter(n -> !n.startsWith("Default")).toList());
            assertSame(c, ch.pipeline().get("packet_handler"), "처리기 이름 packet_handler 가 Connection");
            PacketEncoder<?> enc = assertInstanceOf(PacketEncoder.class, ch.pipeline().get("encoder"));
            PacketDecoder<?> dec = assertInstanceOf(PacketDecoder.class, ch.pipeline().get("decoder"));
            ProtocolInfo<?> e = enc.protocolInfoForCompat();
            ProtocolInfo<?> d = dec.protocolInfoForCompat();
            assertEquals(ConnectionProtocol.PLAY, e.id());
            assertEquals(PacketFlow.CLIENTBOUND, e.flow());
            assertEquals(ConnectionProtocol.PLAY, d.id());
            assertEquals(PacketFlow.SERVERBOUND, d.flow());
            // 이벤트 루프는 호출 스레드에서 바로 실행한다
            List<String> order = new ArrayList<>();
            ch.eventLoop().execute(() -> order.add("ran"));
            order.add("after");
            assertEquals(List.of("ran", "after"), order);
            assertTrue(ch.eventLoop().inEventLoop());
        }
    }

    /** 패킷 라이브러리처럼 channel 필드를 이벤트 루프만 바꾼 프록시로 갈아 끼운다. hook 이 null 을 돌려주면 작업을 버린다. */
    private static List<Object> install(Connection c, java.util.function.UnaryOperator<Runnable> hook) throws Exception {
        Channel real = c.channel;
        EventLoop realLoop = real.eventLoop();
        List<Object> seen = new ArrayList<>();
        EventLoop loop = (EventLoop) Proxy.newProxyInstance(EventLoop.class.getClassLoader(), new Class<?>[] {EventLoop.class}, (p, m, a) -> {
            if (m.getName().equals("execute")) {
                Runnable r = (Runnable) a[0];
                seen.add(r);
                Runnable out = hook.apply(r);
                if (out != null) realLoop.execute(out);
                return null;
            }
            return m.invoke(realLoop, a);
        });
        Channel proxy = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(), new Class<?>[] {Channel.class}, (p, m, a) ->
                m.getName().equals("eventLoop") ? loop : m.invoke(real, a));
        channelField().set(c, proxy);
        return seen;
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

    @Test
    void sendGoesThroughTheChannelEventLoopWhereALibraryCanChangeOrCancel() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer p = server.join("Hook");
            server.tick(2);
            p.flushReceived();
            p.clearReceived();
            Connection c = connectionOf("Hook");
            ServerPlayer handle = ((CraftPlayer) Bukkit.getPlayer("Hook")).getHandle();

            // 1) 바꾸기: 작업 안의 패킷 필드를 되쓴다(ProtocolLib 이 리스너가 바꾼 패킷을 쓰는 방법)
            AtomicReference<Object> seenPacket = new AtomicReference<>();
            List<Object> seen = install(c, r -> {
                try {
                    Field f = packetField(r);
                    seenPacket.set(f.get(r));
                    f.set(r, new ClientboundSetTimePacket(777L, 888L, true));
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                return r;
            });
            handle.connection.send(new ClientboundSetTimePacket(1L, 2L, true));
            server.tick(1);
            p.flushReceived();
            assertEquals(1, seen.size());
            assertInstanceOf(ClientboundSetTimePacket.class, seenPacket.get());
            assertEquals(List.of("ClientboundSetTimePacket[gameTime=777, dayTime=888]"),
                    p.received(PacketTypes.Clientbound.SET_TIME).stream().map(Object::toString).toList());

            // 2) 취소: 작업을 버리면 나가지 않는다
            p.clearReceived();
            install(c, r -> null);
            handle.connection.send(new ClientboundSetTimePacket(5L, 6L, true));
            server.tick(1);
            p.flushReceived();
            assertTrue(p.received(PacketTypes.Clientbound.SET_TIME).isEmpty(), "취소한 패킷은 나가지 않는다");
        }
    }

    @Test
    void sendWithoutALibraryStillDeliversSynchronouslyAndKeepsItsErrors() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer p = server.join("Plain");
            server.tick(2);
            p.flushReceived();
            p.clearReceived();
            ServerPlayer handle = ((CraftPlayer) Bukkit.getPlayer("Plain")).getHandle();
            handle.connection.send(new ClientboundSetTimePacket(11L, 12L, true));
            server.tick(1);
            p.flushReceived();
            assertEquals(1, p.received(PacketTypes.Clientbound.SET_TIME).size());
            handle.connection.send(null);   // 바닐라처럼 보낼 것이 없다 — 예외 없음
            // 바꿀 수 없는 패킷은 지어내지 않고 플러그인에 알린다
            Packet<?> custom = new Packet<net.minecraft.network.PacketListener>() {};
            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedNmsCall.class, () -> handle.connection.send(custom));
        }
    }

    @Test
    void channelClosesWhenThePlayerLeaves() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer p = server.join("Gone");
            server.tick(2);
            ServerPlayer handle = ((CraftPlayer) Bukkit.getPlayer("Gone")).getHandle();
            Connection c = connectionOf("Gone");
            Channel ch = c.channel;
            assertTrue(ch.isActive());
            assertNotNull(ch.closeFuture());
            p.close();
            server.tick(5);
            assertFalse(ch.isActive(), "접속이 끝나면 활성이 아니다");
            // 퇴장 이벤트를 Bukkit 처리기가 듣는 서버(ProtocolLib 등)에서는 그 전달 뒤 이것이 불린다(Mappings 의 PlayerQuitEvent push)
            handle.connection.compatDisconnected();
            assertTrue(ch.closeFuture().isDone(), "닫힘 알림이 나간다(라이브러리가 자기 처리기를 걷는다)");
        }
    }

    @Test
    void channelWritesAreSafeFromManyThreadsAndDeliverPackets() throws Exception {
        try (FurnaceTestServer server = start()) {
            TestPlayer p = server.join("Many");
            server.tick(2);
            p.flushReceived();
            p.clearReceived();
            Connection c = connectionOf("Many");
            Channel ch = c.channel;
            int threads = 8, each = 50;
            java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger failedWire = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicInteger okPackets = new java.util.concurrent.atomic.AtomicInteger();
            List<Thread> ts = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int base = t * each;
                Thread th = new Thread(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    for (int i = 0; i < each; i++) {
                        // 바닐라 NMS 플러그인 모양: channel.writeAndFlush(패킷) — 끝 처리기가 Furnace 패킷으로 보낸다
                        if (ch.writeAndFlush(new ClientboundSetTimePacket(base + i + 1, 0L, true)).syncUninterruptibly().isSuccess()) okPackets.incrementAndGet();
                        // 바이트(와이어 패킷)는 실패로 알리되 채널은 멀쩡해야 한다
                        io.netty.buffer.ByteBuf b = io.netty.buffer.Unpooled.buffer(4).writeInt(1);
                        if (!ch.writeAndFlush(b).awaitUninterruptibly().isSuccess()) failedWire.incrementAndGet();
                        assertEquals(0, b.refCnt(), "바이트는 해제된다");
                    }
                });
                th.start();
                ts.add(th);
            }
            go.countDown();
            for (Thread th : ts) th.join(20_000);
            assertEquals(threads * each, okPackets.get());
            assertEquals(threads * each, failedWire.get());
            assertTrue(ch.isOpen() && ch.isActive(), "여러 스레드 쓰기 뒤에도 가상 채널은 열려 있다");
            server.tick(2);
            p.flushReceived();
            assertEquals(threads * each, p.received(PacketTypes.Clientbound.SET_TIME).size());
        }
    }

    @Test
    void eventLoopSubmitRunsInlineScheduleRunsLaterAndIdsAreDistinct() throws Exception {
        try (FurnaceTestServer server = start()) {
            server.join("Loop1");
            server.join("Loop2");
            server.tick(2);
            Channel a = connectionOf("Loop1").channel;
            Channel b = connectionOf("Loop2").channel;
            assertFalse(a.id().equals(b.id()), "접속마다 채널 번호가 다르다");
            assertFalse(a.id().asLongText().equals(b.id().asLongText()));
            List<String> order = new ArrayList<>();
            io.netty.util.concurrent.Future<String> f = a.eventLoop().submit(() -> {
                order.add("submit");
                return "v";
            });
            order.add("after");
            assertTrue(f.isDone(), "submit 은 그 자리에서 돌고 끝난 future 를 준다");
            assertEquals("v", f.get());
            assertEquals(List.of("submit", "after"), order);
            // 처리기를 이벤트 루프 작업으로 끼우는 흔한 모양
            a.eventLoop().submit(() -> a.pipeline().addBefore("packet_handler", "late", new io.netty.channel.ChannelInboundHandlerAdapter())).get();
            assertNotNull(a.pipeline().get("late"));
            java.util.concurrent.CountDownLatch ran = new java.util.concurrent.CountDownLatch(1);
            a.eventLoop().schedule(ran::countDown, 20, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertTrue(ran.await(5, java.util.concurrent.TimeUnit.SECONDS), "schedule 은 시간이 되면 돈다");
        }
    }
}
