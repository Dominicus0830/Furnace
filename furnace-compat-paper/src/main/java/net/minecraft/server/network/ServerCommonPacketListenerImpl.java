package net.minecraft.server.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;
import org.domi.furnace.compat.nms.SchemaPacket;
import org.domi.furnace.compat.nms.SchemaPacketBridge;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 서버 쪽 연결 공통 처리자 shim(Mojang 이름, H7c): {@code player.connection.send(packet)}.
 * {@code connection} 필드는 바닐라처럼 있지만 Netty 채널은 닫힌 자리표시다 — 파이프라인에 처리기를 끼우는 코드는 아직 지원하지 않는다(Furnace 패킷 API 의 PacketListener 를 쓴다).
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
     * 패킷을 보낸다. 이 모듈의 clientbound 패킷 shim 이 Furnace 패킷으로 바뀌어 나간다 — 손으로 쓴 shim 은 타입 레코드로,
     * 스키마로 생성한 shim 은 범용 패킷({@link SchemaPacketBridge})으로. 접속이 끊겼으면 아무 일도 없다.
     * 묶음(ClientboundBundlePacket)은 바닐라처럼 번들 하나로 보낸다(클라이언트가 한 프레임에 처리). 하나라도 못 바꾸거나 못 쓰면 아무것도 보내지 않는다.
     * 바꿀 수 없는 패킷(플러그인이 직접 구현한 것, 아직 다리가 없는 종류)은 지어내지 않고 UnsupportedNmsCall 을 던진다.
     */
    public void send(Packet<?> packet) {
        if (packet == null) return;   // 바닐라 Connection#send 는 null 을 그대로 내려보내다 인코딩에서 실패한다 — 여기서는 보낼 것이 없다
        var p = f();
        if (packet instanceof BundlePacket<?> bundle) {
            List<ClientboundPacket> out = new ArrayList<>();
            flatten(bundle, out);
            p.sendPacketBundle(out.toArray(ClientboundPacket[]::new));
            return;
        }
        p.sendPacket(convert(packet));
    }

    private static void flatten(Packet<?> packet, List<ClientboundPacket> out) {
        if (packet instanceof BundlePacket<?> bundle) {
            for (Packet<?> sub : bundle.subPackets()) if (sub != null) flatten(sub, out);
            return;
        }
        out.add(convert(packet));
    }

    private static ClientboundPacket convert(Packet<?> packet) {
        if (packet instanceof NmsPacket n) return n.toFurnace();
        if (packet instanceof SchemaPacket s) return SchemaPacketBridge.toFurnace(s);
        throw new UnsupportedNmsCall("ServerCommonPacketListenerImpl#send(" + packet.getClass().getName() + ")",
                "이 패킷 종류는 아직 shim 이 없다 — Furnace 패킷 API(docs/api/packets.md)를 쓴다");
    }

    /** 바닐라: 이유를 보여 주며 연결을 끊는다. */
    public void disconnect(Component reason) { f().kick(Component.furnace(reason)); }

    /** 바닐라: 지연(ms). */
    public int latency() { return f().ping(); }

    @Override
    public boolean isAcceptingMessages() { return f().isOnline(); }
}
