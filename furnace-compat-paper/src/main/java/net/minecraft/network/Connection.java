package net.minecraft.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.domi.furnace.compat.nms.NmsCaller;
import org.domi.furnace.compat.nms.PacketDelivery;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;
import org.domi.furnace.compat.nms.VirtualChannel;

/**
 * 연결 shim(Mojang 이름, N3 2단계 → ProtocolLib 다리 2b): 바닐라 {@code Connection} 의 이름·모양과, 패킷 라이브러리가 끼어드는 두 자리.
 * <p>
 * {@link #channel} 은 이 접속의 <b>가상 채널</b>({@link VirtualChannel})이다 — 접속이 붙어 있는 동안 활성이고, 파이프라인에는 바닐라와 같은 이름의
 * {@code "decoder"}·{@code "encoder"}(단계를 알리는 자리)와 이 연결({@code "packet_handler"})이 있다. 패킷 라이브러리(ProtocolLib)는 이 필드를 자기 프록시 채널로
 * 갈아 끼우고, {@link #send} 가 {@code channel.eventLoop().execute(…)} 로 올리는 작업 안의 패킷을 보고 바꾸거나 취소한다(바닐라와 같은 자리).
 * 작업이 실행되면 패킷이 파이프라인에 쓰이고(플러그인이 {@code channel.writeAndFlush(패킷)} 으로 직접 써도 같다) 끝 처리기가 Furnace 패킷으로 바꿔 내보낸다.
 * Furnace 가 스스로 보내는 패킷(채팅·엔티티 등)은 {@link #compatIntercept} 로 같은 자리를 지난다(2b-2). 들어오는 쪽(서버로 오는 패킷 가로채기)은 아직 이 파이프라인을
 * 지나지 않는다 — 다음 단계(docs/feature-parity/compat-protocollib-pipeline-2b-plan-2026-11-12.md).
 */
public class Connection extends ChannelInboundHandlerAdapter {

    /** 바닐라: 이 연결의 Netty 채널(패킷 라이브러리가 갈아 끼울 수 있게 비정적·비 final). */
    public Channel channel;

    private final PacketListener packetListener;
    private final VirtualChannel virtual;

    /** 호환 모듈 전용. */
    protected Connection() { this(null); }

    /** 호환 모듈 전용: 이 연결을 쓰는 서버 쪽 처리자. */
    public Connection(PacketListener packetListener) {
        this.packetListener = packetListener;
        if (packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl l) {
            this.virtual = new VirtualChannel(l::isAcceptingMessages, () -> l.furnacePlayer().address());
        } else {
            // 서버 쪽 처리자가 없는 연결(시험·자리표시) — 붙어 있지 않은 채널
            this.virtual = new VirtualChannel(() -> false, () -> null);
        }
        this.channel = virtual;
        virtual.pipeline().addLast("furnace_sink", new Sink());   // 쓰기의 끝 — 아래 Sink
        virtual.pipeline().addLast("decoder", new PacketDecoder<>(ProtocolInfos.SERVERBOUND));
        virtual.pipeline().addLast("encoder", new PacketEncoder<>(ProtocolInfos.CLIENTBOUND));
        virtual.pipeline().addLast("packet_handler", this);
    }

    /** 바닐라: 이 연결이 받는 방향. */
    public PacketFlow getReceiving() { return PacketFlow.SERVERBOUND; }

    /** 바닐라: 이 연결의 패킷 처리자. */
    public PacketListener getPacketListener() { return packetListener; }

    /**
     * 바닐라: 패킷을 보낸다. 바닐라처럼 {@code channel.eventLoop().execute(작업)} 로 올린다 — 패킷 라이브러리가 {@link #channel} 을 프록시로 바꿔 두었으면
     * 그 프록시가 작업 안의 패킷을 보고 바꾸거나 취소한 뒤 실행한다. 작업은 바닐라처럼 {@code channel.writeAndFlush(패킷)} 으로 파이프라인에 써서
     * (프록시가 "이미 처리함" 표시를 끈다) 끝 처리기({@link Sink})가 Furnace 패킷으로 바꿔 내보낸다. 아무도 {@link #channel} 을 바꾸지 않았으면
     * (라이브러리 없음) 작업 객체·파이프라인을 거치지 않고 바로 내보낸다(결과는 같다).
     * 변환·전송 오류는 바닐라 인코딩 오류가 아니라 호출한 플러그인에 그대로 던진다(UnsupportedNmsCall·IllegalArgumentException).
     */
    public void send(Packet<?> packet) {
        if (packet == null) return;
        if (!(packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl l)) {
            throw new UnsupportedNmsCall("Connection#send", "이 연결에는 보낼 서버 쪽 처리자가 없다");
        }
        if (channel == virtual) {
            PacketDelivery.deliver(l.furnacePlayer(), packet);
            return;
        }
        channel.eventLoop().execute(new Send(this, packet));
    }

    /** 이 패킷을 이 연결에서 실제로 내보내는 작업. 패킷 라이브러리가 필드를 읽고 바꿀 수 있게 이름 있는 클래스·비 final 필드다. */
    private static final class Send implements Runnable {
        private final Connection connection;
        private Packet<?> packet;

        Send(Connection connection, Packet<?> packet) {
            this.connection = connection;
            this.packet = packet;
        }

        @Override
        public void run() {
            Packet<?> p = packet;
            if (p == null) return;
            io.netty.channel.ChannelFuture f;
            SENDING.set(Boolean.TRUE);
            try {
                f = connection.channel.writeAndFlush(p);
            } finally {
                SENDING.remove();
            }
            // 끝 처리기는 그 자리에서 끝낸다 — 실패는 호출한 플러그인에 던진다(취소돼 아직 안 끝난 경우는 아무것도 안 한다)
            if (f.isDone() && !f.isSuccess()) {
                Throwable t = f.cause();
                if (t instanceof RuntimeException r) throw r;
                if (t instanceof Error e) throw e;
                throw new IllegalStateException(t);
            }
        }
    }

    /**
     * 호환 모듈 전용(ProtocolLib 다리 2b-2): Furnace 서버가 스스로 보내는 패킷(shim 으로 바꾼 것)을 패킷 라이브러리에 보여 준다. {@link #send} 와 같은 자리
     * ({@code channel.eventLoop().execute(작업)} → 작업이 {@code channel.writeAndFlush(패킷)})를 지나되 끝 처리기가 내보내지 않고 받아 둔다 —
     * 라이브러리 리스너가 바꿨으면 바꾼 패킷, 취소했으면 null 을 돌려준다. 라이브러리가 {@link #channel} 을 바꾸지 않았으면 그대로 돌려준다.
     * 가로채기는 이 스레드에서 그 자리에 끝난다(가상 채널의 이벤트 루프). 라이브러리가 작업을 나중으로 미루면(주 스레드로 되돌리기 등) 지금은 null 이다.
     */
    public Packet<?> compatIntercept(Packet<?> packet) {
        if (packet == null || channel == virtual) return packet;
        Capture c = new Capture(this, packet);
        channel.eventLoop().execute(c);
        return c.ran ? c.result : null;
    }

    /** {@link #compatIntercept} 의 작업. 패킷 라이브러리가 필드를 읽고 바꿀 수 있게 이름 있는 클래스·비 final {@link Packet} 필드다(Send 와 같은 모양). */
    private static final class Capture implements Runnable {
        private final Connection connection;
        private Packet<?> packet;
        boolean ran;
        Packet<?> result;

        Capture(Connection connection, Packet<?> packet) {
            this.connection = connection;
            this.packet = packet;
        }

        @Override
        public void run() {
            ran = true;
            Packet<?> p = packet;
            result = p;
            if (p == null) return;
            // 바닐라처럼 채널에 써서(라이브러리의 "이미 처리함" 표시를 끈다) 끝 처리기가 받아 둔다
            CAPTURE.set(this);
            try {
                connection.channel.writeAndFlush(p);
            } finally {
                CAPTURE.remove();
            }
        }
    }

    /** 지금 이 스레드에서 받아 두는 중인 Capture(끝 처리기가 내보내는 대신 여기에 남긴다). */
    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();

    /** Send 작업이 쓰는 중인가(그 실패는 호출한 쪽으로 던지므로 끝 처리기가 따로 경고하지 않는다). */
    private static final ThreadLocal<Boolean> SENDING = new ThreadLocal<>();

    /**
     * 파이프라인 맨 앞(쓰기의 끝)의 처리기: 쓰기를 여기서 끝내 Netty 의 출력 버퍼·unsafe(한 스레드 전용)를 건드리지 않는다 — 여러 스레드가 같은 접속에 써도 안전하다.
     * {@link Packet} 은 Furnace 패킷으로 바꿔 내보내고({@link PacketDelivery}), 바이트(ProtocolLib 의 sendWirePacket 이 인코딩한 것)는 해제하고 실패로 알리며
     * 플러그인 이름으로 한 번 경고한다(Furnace 패킷 API 에 원본 바이트 보내기는 없다). flush 는 아래로 넘기지 않는다.
     */
    private final class Sink extends io.netty.channel.ChannelDuplexHandler {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise) {
            Capture cap = CAPTURE.get();
            if (cap != null && msg instanceof Packet<?> captured) {   // 내보내지 않고 받아 둔다(compatIntercept)
                cap.result = captured;
                CAPTURE.remove();   // 한 번만 — 리스너가 이 안에서 다른 패킷을 쓰면 그것은 보통대로 나간다
                promise.trySuccess();
                return;
            }
            if (msg instanceof Packet<?> p && packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl l) {
                try {
                    PacketDelivery.deliver(l.furnacePlayer(), p);
                    promise.trySuccess();
                } catch (RuntimeException | Error e) {
                    promise.tryFailure(e);
                    if (SENDING.get() == null) {
                        NmsCaller.warnOnce("channel-write", "{} 가 connection.channel 로 쓴 패킷을 보내지 못했다(첫 오류): " + String.valueOf(e.getMessage()).replace("{", "(").replace("}", ")"));
                    }
                }
                return;
            }
            boolean bytes = msg instanceof io.netty.buffer.ByteBuf;
            io.netty.util.ReferenceCountUtil.release(msg);
            String what = bytes ? "바이트 와이어 패킷(ProtocolLib sendWirePacket 등)" : "패킷이 아닌 값 " + (msg == null ? "null" : msg.getClass().getName());
            promise.tryFailure(new UnsupportedNmsCall("Channel#write", what + " 은 아직 지원하지 않는다 — Furnace 패킷 API(docs/api/packets.md)를 쓴다"));
            NmsCaller.warnOnce(bytes ? "wire-packet" : "channel-write-other", "{} 가 보낸 " + what + " 은 아직 지원하지 않아 버렸다 — Furnace 패킷 API(docs/api/packets.md)를 쓴다");
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            // 쓰기는 write 에서 끝났다 — 출력 버퍼가 없으므로 flush 할 것이 없다
        }
    }

    /** 바닐라 {@code channelRead0} 에 해당(ProtocolLib 은 이름을 read 로 찾는다): 받은 패킷을 처리자에게 넘긴다 — 아직 지원하지 않는다. */
    public void read(ChannelHandlerContext ctx, Packet<?> packet) {
        throw new UnsupportedNmsCall("Connection#read", "들어오는 패킷을 서버에 되먹이는 다리가 아직 없다(ProtocolLib 다리 2b-3단계)");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Packet<?> p) read(ctx, p);
        else io.netty.util.ReferenceCountUtil.release(msg);
    }

    /** 바닐라: 이유를 보여 주며 끊는다. */
    public void disconnect(Component reason) {
        if (packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl l) l.disconnect(reason);
    }

    /** 호환 모듈 전용: 접속이 끝났다 — 가상 채널을 닫는다(패킷 라이브러리가 자기 처리기·프록시를 걷는다). */
    public void disconnected() { virtual.disconnected(); }

    /** 호환 모듈 전용: 단계·방향 표(파이프라인의 인코더·디코더가 들고 있다). */
    private static final class ProtocolInfos {
        static final ProtocolInfo<PacketListener> SERVERBOUND = org.domi.furnace.compat.nms.ShimProtocols.<PacketListener>unbound(ConnectionProtocol.PLAY, PacketFlow.SERVERBOUND).bind(b -> b);
        static final ProtocolInfo<PacketListener> CLIENTBOUND = org.domi.furnace.compat.nms.ShimProtocols.<PacketListener>unbound(ConnectionProtocol.PLAY, PacketFlow.CLIENTBOUND).bind(b -> b);

        private ProtocolInfos() {}
    }
}
