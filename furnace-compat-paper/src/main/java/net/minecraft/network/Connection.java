package net.minecraft.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 연결 shim(Mojang 이름, N3 2단계): 바닐라 {@code Connection} 의 "이름·모양"만 있다.
 * 패킷 라이브러리가 연결 객체의 클래스·필드·메서드를 리플렉션으로 찾는 데 쓰인다.
 * <p>
 * {@link #channel} 은 <b>활성이 아닌 자리표시 채널</b>이다. 라이브러리는 비활성 채널에는 파이프라인을 끼우지 않고 조용히 건너뛴다
 * (ProtocolLib: 접속·재시작 때 주입 실패 대신 "주입 안 됨"). 따라서 가로채기·{@code sendServerPacket} 은 아직 동작하지 않는다 —
 * 진짜 파이프라인 다리(ProtocolLib 다리 2b단계)가 생기면 이 채널을 활성 가상 채널로 바꾼다.
 */
public class Connection {

    private static volatile Channel placeholder;

    /** 지연 생성한 닫힌 채널 하나를 모든 연결이 나눠 쓴다(채널 자체는 아무 데도 이어지지 않는다). */
    private static Channel placeholderChannel() {
        Channel c = placeholder;
        if (c == null) {
            synchronized (Connection.class) {
                c = placeholder;
                if (c == null) {
                    EmbeddedChannel e = new EmbeddedChannel();
                    e.close();
                    placeholder = c = e;
                }
            }
        }
        return c;
    }

    /** 바닐라: 이 연결의 Netty 채널. 여기서는 닫힌 자리표시 채널. */
    public Channel channel = placeholderChannel();

    private final PacketListener packetListener;

    /** 호환 모듈 전용. */
    protected Connection() { this(null); }

    /** 호환 모듈 전용: 이 연결을 쓰는 서버 쪽 처리자. */
    public Connection(PacketListener packetListener) { this.packetListener = packetListener; }

    /** 바닐라: 이 연결이 받는 방향. */
    public net.minecraft.network.protocol.PacketFlow getReceiving() { return net.minecraft.network.protocol.PacketFlow.SERVERBOUND; }

    /** 바닐라: 이 연결의 패킷 처리자. */
    public PacketListener getPacketListener() { return packetListener; }

    /** 바닐라: 패킷을 보낸다 — 서버 쪽 처리자의 send 로 잇는다(Furnace 가 보낼 줄 아는 패킷만). */
    public void send(Packet<?> packet) {
        if (packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl l) l.send(packet);
        else throw new UnsupportedNmsCall("Connection#send", "이 연결에는 보낼 서버 쪽 처리자가 없다");
    }

    /** 바닐라 {@code channelRead0} 에 해당(ProtocolLib 은 이름을 read 로 찾는다): 받은 패킷을 처리자에게 넘긴다 — 아직 지원하지 않는다. */
    public void read(ChannelHandlerContext ctx, Packet<?> packet) {
        throw new UnsupportedNmsCall("Connection#read", "들어오는 패킷을 서버에 되먹이는 다리가 아직 없다(ProtocolLib 다리 2b단계)");
    }

    /** 바닐라: 이유를 보여 주며 끊는다. */
    public void disconnect(Component reason) {
        if (packetListener instanceof net.minecraft.server.network.ServerCommonPacketListenerImpl l) l.disconnect(reason);
    }
}
