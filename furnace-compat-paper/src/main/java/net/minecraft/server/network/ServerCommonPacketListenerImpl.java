package net.minecraft.server.network;

import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;

/**
 * 서버 쪽 연결 공통 처리자 shim(Mojang 이름, H7c): {@code player.connection.send(packet)}.
 * {@code connection} 필드는 바닐라처럼 있고 그 채널은 가상 채널이다({@link net.minecraft.network.Connection}) — 보내는 쪽 가로채기는 되고 들어오는 쪽은 아직이다.
 */
public abstract class ServerCommonPacketListenerImpl implements PacketListener {

    private final ServerPlayer owner;

    /** 바닐라: 이 접속의 연결(패킷 라이브러리가 형식으로 이 필드를 찾는다). */
    protected final net.minecraft.network.Connection connection;

    protected ServerCommonPacketListenerImpl(ServerPlayer owner) {
        this.owner = owner;
        this.connection = new net.minecraft.network.Connection(this);
    }

    private org.domi.furnace.api.entity.Player f() { return owner.getBukkitEntity().furnaceHandle(); }

    /**
     * 패킷을 보낸다 — 바닐라처럼 연결({@code connection.send})의 이벤트 루프 작업으로 올려 패킷 라이브러리가 가로챌 수 있게 하고,
     * 실행되면 Furnace 패킷으로 바뀌어 나간다({@link org.domi.furnace.compat.nms.PacketDelivery}: 묶음·변환 규칙·오류).
     */
    public void send(Packet<?> packet) {
        connection.send(packet);
    }

    /** 호환 모듈 전용: 접속이 끝났다 — 연결의 가상 채널을 닫는다. */
    public void compatDisconnected() { connection.disconnected(); }

    /** 호환 모듈 전용(ProtocolLib 다리 2b-2): 이 접속의 연결. */
    public net.minecraft.network.Connection compatConnection() { return connection; }

    /** 호환 모듈 전용: 이 접속의 Furnace 플레이어(패킷을 실제로 내보낼 곳). */
    public org.domi.furnace.api.entity.Player furnacePlayer() { return f(); }

    /** 바닐라: 이유를 보여 주며 연결을 끊는다. */
    public void disconnect(Component reason) { f().kick(Component.furnace(reason)); }

    /** 바닐라: 지연(ms). */
    public int latency() { return f().ping(); }

    @Override
    public boolean isAcceptingMessages() { return f().isOnline(); }
}
