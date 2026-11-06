// 접속 방어(EmbeddedChannel): 단계별 패킷 속도·프레임 상한, 채팅·명령 길이, PROXY 프로토콜 헤더
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.net.io.FrameDecoder;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.ConnectionState;
import org.junit.jupiter.api.Test;

class ConnectionDefenseTest {

    private static ConnectionLimits limits(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return ConnectionLimits.fromProperties(p, m -> {});
    }

    private record Rig(EmbeddedChannel ch, ConnectionHandler handler) {}

    private static Rig rig(ConnectionLimits l) {
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        ch.pipeline().addLast("frameDecoder", new FrameDecoder());
        ConnectionHandler h = new ConnectionHandler(ServerLoginContext.offline("t").limits(l), -1, null, null, null);
        ch.pipeline().addLast("handler", h);
        return new Rig(ch, h);
    }

    private static ByteBuf frame(int id, java.util.function.Consumer<ByteBuf> body) {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        ByteBuf f = Unpooled.buffer();
        Types.writeVarInt(f, b.readableBytes());
        f.writeBytes(b);
        b.release();
        return f;
    }

    @Test
    void packetRate_isPerState_andAppliesBurst() {
        // 구성 단계만 버스트 3, 회복 초당 1 로 조인다
        Rig r = rig(limits("connection.configuration.packets-burst", "3", "connection.configuration.packets-per-second", "1"));
        r.handler.state(ConnectionState.CONFIGURATION);
        for (int i = 0; i < 3; i++) {
            r.ch.writeInbound(frame(0x00, null));   // Client Information(빈 본문 — 파싱 실패는 무시)
            assertTrue(r.ch.isOpen(), (i + 1) + "번째는 버스트 안");
        }
        r.ch.writeInbound(frame(0x00, null));
        assertFalse(r.ch.isOpen(), "4번째는 버스트 초과 — 끊긴다");
        r.ch.finishAndReleaseAll();
    }

    @Test
    void packetRate_otherStatesKeepDefaults() {
        Rig r = rig(limits("connection.configuration.packets-burst", "3", "connection.configuration.packets-per-second", "1"));
        r.handler.state(ConnectionState.PLAY);
        for (int i = 0; i < 100; i++) r.ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, 99)));   // 확인 안 된 텔레포트 id — 무시
        assertTrue(r.ch.isOpen(), "플레이 단계는 기본 버스트 400 이라 100개쯤은 괜찮다");
        r.ch.finishAndReleaseAll();
    }

    @Test
    void packetRate_zeroMeansUnlimited() {
        Rig r = rig(limits("connection.play.packets-per-second", "0"));
        r.handler.state(ConnectionState.PLAY);
        for (int i = 0; i < 2000; i++) r.ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, 99)));
        assertTrue(r.ch.isOpen());
        r.ch.finishAndReleaseAll();
    }

    @Test
    void packetRate_defaultStillKicksFlood() {
        Rig r = rig(ConnectionLimits.DEFAULT);
        r.handler.state(ConnectionState.PLAY);
        for (int i = 0; i < 1000 && r.ch.isOpen(); i++) r.ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, 99)));
        assertFalse(r.ch.isOpen(), "기본 버킷(버스트 400)이 폭주를 끊는다");
        r.ch.finishAndReleaseAll();
    }

    @Test
    void frameLimit_followsStateAndConfig() {
        Rig r = rig(limits("connection.login.max-frame-bytes", "64", "connection.configuration.max-frame-bytes", "128"));
        FrameDecoder fd = r.ch.pipeline().get(FrameDecoder.class);
        assertEquals(ConnectionHandler.HANDSHAKE_FRAME_MAX, fd.maxLength());
        r.handler.state(ConnectionState.LOGIN);
        assertEquals(64, fd.maxLength());
        r.handler.state(ConnectionState.CONFIGURATION);
        assertEquals(128, fd.maxLength());
        r.handler.state(ConnectionState.PLAY);
        assertEquals(ConnectionLimits.HARD_FRAME_MAX, fd.maxLength());
        r.ch.finishAndReleaseAll();
    }

    @Test
    void loginFrame_overLimitIsClosedBeforeBuffering() {
        Rig r = rig(limits("connection.login.max-frame-bytes", "64"));
        r.handler.state(ConnectionState.LOGIN);
        ByteBuf big = Unpooled.buffer();
        Types.writeVarInt(big, 1000);   // 본문 없이 길이만으로 끊는다
        r.ch.writeInbound(big);
        assertFalse(r.ch.isOpen());
        r.ch.finishAndReleaseAll();
    }

    @Test
    void defaultLoginAndConfigurationFramesAcceptVanillaMaximum() {
        Rig r = rig(ConnectionLimits.DEFAULT);
        FrameDecoder fd = r.ch.pipeline().get(FrameDecoder.class);
        r.handler.state(ConnectionState.LOGIN);
        assertTrue(fd.maxLength() >= 1 + 5 + 1_048_576, "로그인 질의 응답 최대 크기");
        r.handler.state(ConnectionState.CONFIGURATION);
        assertTrue(fd.maxLength() >= 1 + 3 + 32767 * 3 + 32767, "구성 custom_payload 최대 크기");
        r.ch.finishAndReleaseAll();
    }

    @Test
    void commandLength_tighterLimitKicks_vanillaLimitStays() {
        Rig tight = rig(limits("limits.command-length", "10"));
        tight.handler.state(ConnectionState.PLAY);
        tight.ch.writeInbound(frame(0x04, b -> Types.writeString(b, "x".repeat(11))));
        assertFalse(tight.ch.isOpen(), "11자 명령은 상한 10 을 넘는다");
        tight.ch.finishAndReleaseAll();

        Rig vanilla = rig(ConnectionLimits.DEFAULT);
        vanilla.handler.state(ConnectionState.PLAY);
        vanilla.ch.writeInbound(frame(0x04, b -> Types.writeString(b, "x".repeat(256))));
        assertTrue(vanilla.ch.isOpen(), "256자(바닐라 상한)는 받는다");
        vanilla.ch.writeInbound(frame(0x04, b -> Types.writeString(b, "x".repeat(257))));
        assertFalse(vanilla.ch.isOpen(), "257자는 바닐라처럼 거부");
        vanilla.ch.finishAndReleaseAll();
    }

    // ------------------------------------------------------------------ PROXY 프로토콜

    private static final byte[] V2_SIG = {0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A};

    /** PROXY v2 TCP4 헤더. */
    static ByteBuf v2Tcp4(int[] src, int srcPort) {
        ByteBuf b = Unpooled.buffer();
        b.writeBytes(V2_SIG);
        b.writeByte(0x21);   // v2, PROXY
        b.writeByte(0x11);   // TCP over IPv4
        b.writeShort(12);
        for (int x : src) b.writeByte(x);
        b.writeBytes(new byte[]{10, 0, 0, 1});   // 도착 주소
        b.writeShort(srcPort);
        b.writeShort(25565);
        return b;
    }

    private static EmbeddedChannel proxyChannel(ConnectionLimits l, InetAddress peer, java.util.List<Object> sink) {
        EmbeddedChannel ch = new EmbeddedChannel() {
            @Override
            protected SocketAddress remoteAddress0() {
                return isActive() ? new InetSocketAddress(peer, 40000) : null;
            }
        };
        AtomicBoolean warned = new AtomicBoolean();
        ch.pipeline().addLast("haproxySniffer", new ProxyProtocolHandler.Sniffer(warned))
                .addLast("haproxyDecoder", new HAProxyMessageDecoder())
                .addLast("haproxyHandler", new ProxyProtocolHandler(l, warned))
                .addLast("sink", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        sink.add(msg);
                    }
                });
        return ch;
    }

    @Test
    void proxyProtocol_v2HeaderSetsSourceAndPassesPayload() throws Exception {
        var sink = new java.util.ArrayList<Object>();
        EmbeddedChannel ch = proxyChannel(limits("proxy-protocol", "true"), InetAddress.getByName("10.0.0.5"), sink);
        ByteBuf in = v2Tcp4(new int[]{203, 0, 113, 9}, 51234);
        in.writeBytes(new byte[]{1, 2, 3});   // 헤더 뒤 본문(핸드셰이크 등)
        ch.writeInbound(in);
        InetSocketAddress src = ProxyProtocolHandler.sourceOf(ch);
        assertEquals(new InetSocketAddress(InetAddress.getByName("203.0.113.9"), 51234), src);
        assertTrue(ch.isOpen());
        assertEquals(1, sink.size());
        ByteBuf rest = (ByteBuf) sink.get(0);
        assertEquals(3, rest.readableBytes(), "헤더만 먹고 본문은 그대로 넘긴다");
        rest.release();
        assertNull(ch.pipeline().get("haproxyHandler"), "헤더를 읽은 뒤 스스로 빠진다");
        ch.finishAndReleaseAll();
    }

    @Test
    void proxyProtocol_v1TextHeaderAlsoWorks() throws Exception {
        var sink = new java.util.ArrayList<Object>();
        EmbeddedChannel ch = proxyChannel(limits("proxy-protocol", "true"), InetAddress.getByName("10.0.0.5"), sink);
        ch.writeInbound(Unpooled.copiedBuffer("PROXY TCP4 198.51.100.7 10.0.0.1 4444 25565\r\n", StandardCharsets.US_ASCII));
        assertEquals(new InetSocketAddress(InetAddress.getByName("198.51.100.7"), 4444), ProxyProtocolHandler.sourceOf(ch));
        ch.finishAndReleaseAll();
    }

    @Test
    void proxyProtocol_v6Source() throws Exception {
        var sink = new java.util.ArrayList<Object>();
        EmbeddedChannel ch = proxyChannel(limits("proxy-protocol", "true"), InetAddress.getByName("10.0.0.5"), sink);
        ch.writeInbound(Unpooled.copiedBuffer("PROXY TCP6 2001:db8::7 2001:db8::1 4444 25565\r\n", StandardCharsets.US_ASCII));
        assertEquals(new InetSocketAddress(InetAddress.getByName("2001:db8::7"), 4444), ProxyProtocolHandler.sourceOf(ch));
        ch.finishAndReleaseAll();
    }

    @Test
    void proxyProtocol_localCommandKeepsRealAddress() throws Exception {
        var sink = new java.util.ArrayList<Object>();
        EmbeddedChannel ch = proxyChannel(limits("proxy-protocol", "true"), InetAddress.getByName("10.0.0.5"), sink);
        ByteBuf b = Unpooled.buffer();
        b.writeBytes(V2_SIG);
        b.writeByte(0x20);   // v2, LOCAL(앞단의 상태 점검)
        b.writeByte(0x00);
        b.writeShort(0);
        ch.writeInbound(b);
        assertNull(ProxyProtocolHandler.sourceOf(ch));
        assertTrue(ch.isOpen());
        ch.finishAndReleaseAll();
    }

    @Test
    void proxyProtocol_missingHeaderIsRejected() throws Exception {
        var sink = new java.util.ArrayList<Object>();
        EmbeddedChannel ch = proxyChannel(limits("proxy-protocol", "true"), InetAddress.getByName("10.0.0.5"), sink);
        // 일반 마인크래프트 핸드셰이크 바이트(PROXY 헤더 없음)
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 1); }));
        assertFalse(ch.isOpen(), "헤더 없는 직접 접속은 거부");
        assertTrue(sink.isEmpty(), "뒤 핸들러로는 아무것도 넘어가지 않는다");
        ch.finishAndReleaseAll();
    }

    @Test
    void proxyProtocol_untrustedSenderIsRejected() throws Exception {
        var sink = new java.util.ArrayList<Object>();
        ConnectionLimits l = limits("proxy-protocol", "true", "proxy-protocol.trusted", "10.0.0.0/8");
        EmbeddedChannel bad = proxyChannel(l, InetAddress.getByName("203.0.113.5"), sink);
        bad.writeInbound(v2Tcp4(new int[]{1, 2, 3, 4}, 1));
        assertFalse(bad.isOpen(), "신뢰 목록 밖의 주소가 보낸 PROXY 헤더는 거부");
        assertNull(ProxyProtocolHandler.sourceOf(bad));
        bad.finishAndReleaseAll();

        EmbeddedChannel good = proxyChannel(l, InetAddress.getByName("10.4.4.4"), sink);
        good.writeInbound(v2Tcp4(new int[]{1, 2, 3, 4}, 1));
        assertTrue(good.isOpen());
        assertNotNull(ProxyProtocolHandler.sourceOf(good));
        good.finishAndReleaseAll();
    }

    @Test
    void clientAddress_prefersProxiedSource() throws Exception {
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        ConnectionHandler h = new ConnectionHandler(ServerLoginContext.offline("t"), -1, null, null, null);
        ch.pipeline().addLast("handler", h);
        ChannelHandlerContext ctx = ch.pipeline().context(h);
        assertEquals(ch.remoteAddress(), h.clientAddress(ctx), "헤더가 없으면 실제 소켓 주소");
        InetSocketAddress real = new InetSocketAddress(InetAddress.getByName("203.0.113.9"), 51234);
        ch.attr(ProxyProtocolHandler.SOURCE).set(real);
        assertEquals(real, h.clientAddress(ctx));
        ch.finishAndReleaseAll();
    }
}
