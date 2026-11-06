// 연결 하나에 붙는 플러그인 패킷 가로채기 지점(play 단계에서만 붙는다). 두 메서드 모두 그 연결의 이벤트 루프에서만 불린다.
// 구독자가 없는 종류는 곧바로 그대로 돌려준다(배열 한 칸 확인).
package org.domi.furnace.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.util.AttributeKey;
import org.domi.furnace.server.net.protocol.Packet;

public interface PacketTap {

    AttributeKey<PacketTap> KEY = AttributeKey.valueOf("furnace:packet_tap");

    /** 서버→클라이언트. null 이면 버린다, 아니면 보낼 패킷(그대로거나 바뀐 것). */
    Packet outbound(Packet packet);

    /**
     * 클라이언트→서버. body 는 id 뒤의 본문(읽기 위치를 옮기지 않는다). null 이면 버린다, body 그대로면 원래대로 처리,
     * 다른 ByteBuf 면 [VarInt id][본문] 새 프레임 — 부른 쪽이 처리 뒤 release 한다.
     */
    ByteBuf inbound(int packetId, ByteBuf body, boolean pluginPacket);
}
