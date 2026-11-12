package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.packet.ClientboundPacket;

/**
 * shim 패킷 → Furnace 패킷으로 바꿔 한 플레이어에게 내보내는 마지막 걸음(ProtocolLib 다리). {@code ServerCommonPacketListenerImpl#send} 가
 * 연결의 이벤트 루프 작업으로 올리고, 패킷 라이브러리가 그 작업을 보고 난 뒤 이 메서드가 실행된다. 플러그인용이 아니다.
 *
 * <p>변환 규칙: 이 모듈의 clientbound 패킷 shim 이 Furnace 패킷으로 바뀌어 나간다 — 손으로 쓴 shim 은 타입 레코드로, 스키마로 생성한 shim 은 범용 패킷
 * ({@link SchemaPacketBridge})으로. 접속이 끊겼으면 아무 일도 없다.
 * 묶음(ClientboundBundlePacket)은 바닐라처럼 번들 하나로 보낸다(클라이언트가 한 프레임에 처리). 하나라도 못 바꾸거나 못 쓰면 아무것도 보내지 않는다.
 * 바닐라와 다른 가장자리(검토 R5): 묶음 안의 묶음·null 은 IllegalArgumentException(바닐라 unbundler 는 한 겹만 풀고 안쪽 묶음은 종류 번호가 없어
 * 인코딩이 실패해 연결이 끊긴다), 4096개 초과도 IllegalArgumentException(바닐라는 보내고 클라이언트가 끊는다) — 둘 다 플레이어를 끊지 않고
 * 플러그인에 알린다. 빈 묶음은 아무것도 보내지 않는다(바닐라는 구분자 둘 — 게임 화면 차이 없음, 서버 번들 규칙).
 * 바꿀 수 없는 패킷(플러그인이 직접 구현한 것, 아직 다리가 없는 종류)은 지어내지 않고 UnsupportedNmsCall 을 던진다.
 */
public final class PacketDelivery {

    private PacketDelivery() {}

    public static void deliver(Player p, Packet<?> packet) {
        if (packet == null) return;   // 바닐라 Connection#send 는 null 을 그대로 내려보내다 인코딩에서 실패한다 — 여기서는 보낼 것이 없다
        if (packet instanceof BundlePacket<?> bundle) {
            List<ClientboundPacket> out = new ArrayList<>();
            for (Packet<?> sub : bundle.subPackets()) {
                if (sub == null) throw new IllegalArgumentException("묶음 안에 null 패킷이 있다(바닐라도 인코딩이 실패해 연결이 끊긴다)");
                if (sub instanceof BundlePacket<?>) {
                    throw new IllegalArgumentException("묶음 안에 묶음을 넣을 수 없다(바닐라도 안쪽 묶음을 인코딩하지 못해 연결이 끊긴다)");
                }
                out.add(convert(sub));
            }
            p.sendPacketBundle(out.toArray(ClientboundPacket[]::new));
            return;
        }
        p.sendPacket(convert(packet));
    }

    private static ClientboundPacket convert(Packet<?> packet) {
        if (packet instanceof NmsPacket n) return n.toFurnace();
        if (packet instanceof SchemaPacket s) return SchemaPacketBridge.toFurnace(s);
        throw new UnsupportedNmsCall("ServerCommonPacketListenerImpl#send(" + packet.getClass().getName() + ")",
                "이 패킷 종류는 아직 shim 이 없다 — Furnace 패킷 API(docs/api/packets.md)를 쓴다");
    }
}
