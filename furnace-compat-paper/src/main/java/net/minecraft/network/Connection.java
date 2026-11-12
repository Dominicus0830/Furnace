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
 * 작업이 실행되면 패킷이 Furnace 패킷으로 바뀌어 나간다. 들어오는 쪽(서버로 오는 패킷 가로채기)과 Furnace 가 스스로 보내는 패킷(채팅·엔티티 등)은 아직 이 파이프라인을
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
            this.virtual = new VirtualChannel(l::isAcceptingMessages, () -> l.furnacePlayer().address(), () -> NmsCaller.warnOnce("wire-packet",
                    "{} 가 보낸 바이트 와이어 패킷(ProtocolLib sendWirePacket)은 아직 지원하지 않아 버렸다 — Furnace 패킷 API(docs/api/packets.md)를 쓴다"));
        } else {
            // 서버 쪽 처리자가 없는 연결(시험·자리표시) — 붙어 있지 않은 채널
            this.virtual = new VirtualChannel(() -> false, () -> null, () -> {});
        }
        this.channel = virtual;
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
     * 그 프록시가 작업 안의 패킷을 보고 바꾸거나 취소한 뒤 실행한다. 작업이 실행되면 서버 쪽 처리자의 보내기(Furnace 패킷으로 변환)로 이어진다.
     */
    public void send(Packet<?> packet) {
        if (packet == null) return;
        if (!(packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl)) {
            throw new UnsupportedNmsCall("Connection#send", "이 연결에는 보낼 서버 쪽 처리자가 없다");
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
            PacketDelivery.deliver(((net.minecraft.server.network.ServerCommonPacketListenerImpl) connection.packetListener).furnacePlayer(), packet);
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
