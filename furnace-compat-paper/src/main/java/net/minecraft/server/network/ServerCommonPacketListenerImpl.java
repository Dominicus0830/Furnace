package net.minecraft.server.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import org.domi.furnace.compat.nms.NmsPacket;
import org.domi.furnace.compat.nms.SchemaPacket;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 서버 쪽 연결 공통 처리자 shim(Mojang 이름, H7c): {@code player.connection.send(packet)}.
 * 실제 연결({@code connection} 필드, Netty 채널)은 내보내지 않는다 — 파이프라인에 처리기를 끼우는 코드는 등급 C 다(Furnace 패킷 API 의 PacketListener 를 쓴다).
 */
public abstract class ServerCommonPacketListenerImpl implements PacketListener {

    private final ServerPlayer owner;

    protected ServerCommonPacketListenerImpl(ServerPlayer owner) { this.owner = owner; }

    private org.domi.furnace.api.entity.Player f() { return owner.getBukkitEntity().furnaceHandle(); }

    /**
     * 패킷을 보낸다. 이 모듈의 clientbound 패킷 shim 만 보낼 수 있다(Furnace 패킷 API 로 바뀌어 나간다). 접속이 끊겼으면 아무 일도 없다.
     * 그 밖의 패킷(플러그인이 직접 구현한 것 등)은 지어내지 않고 UnsupportedNmsCall 을 던진다.
     */
    public void send(Packet<?> packet) {
        if (packet == null) return;   // 바닐라 Connection#send 는 null 을 그대로 내려보내다 인코딩에서 실패한다 — 여기서는 보낼 것이 없다
        // 묶음: 안의 패킷을 차례로 보낸다(묶음 경계 없이 — 클라이언트가 한 프레임에 모아 처리하는 것까지는 아직 같지 않다).
        // 바닐라 묶음은 한꺼번에 나가므로, 하나라도 못 보내면 아무것도 보내지 않는다(앞의 것만 나가는 일이 없게 먼저 확인).
        List<NmsPacket> out = new ArrayList<>();
        flatten(packet, out);
        var p = f();
        for (NmsPacket n : out) p.sendPacket(n.toFurnace());
    }

    private static void flatten(Packet<?> packet, List<NmsPacket> out) {
        if (packet instanceof BundlePacket<?> bundle) {
            for (Packet<?> sub : bundle.subPackets()) if (sub != null) flatten(sub, out);
            return;
        }
        if (!(packet instanceof NmsPacket n)) {
            throw new UnsupportedNmsCall("ServerCommonPacketListenerImpl#send(" + packet.getClass().getName() + ")",
                    packet instanceof SchemaPacket
                            ? "이 패킷은 모양(shim)은 있지만 보내기 변환이 아직 없다(범용 패킷 코덱 대기) — Furnace 패킷 API(docs/api/packets.md)를 쓴다"
                            : "이 패킷 종류는 아직 shim 이 없다 — Furnace 패킷 API(docs/api/packets.md)를 쓴다");
        }
        out.add(n);
    }

    /** 바닐라: 이유를 보여 주며 연결을 끊는다. */
    public void disconnect(Component reason) { f().kick(Component.furnace(reason)); }

    /** 바닐라: 지연(ms). */
    public int latency() { return f().ping(); }

    @Override
    public boolean isAcceptingMessages() { return f().isOnline(); }
}
