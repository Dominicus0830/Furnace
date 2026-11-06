// 가로챈 패킷 하나. 리스너는 내용을 읽고(packet), 다른 값으로 바꾸거나(setPacket), 버릴 수 있다(setCancelled).
// 이 객체는 리스너가 불리는 동안만 쓴다(다른 스레드에 넘기거나 나중에 쓰지 않는다).
package org.domi.furnace.api.packet;

import org.domi.furnace.api.entity.Player;

public interface PacketEvent<P extends Packet> {

    /** 패킷을 받는(CLIENTBOUND) 또는 보낸(SERVERBOUND) 플레이어. */
    Player player();

    PacketType<P> type();

    /** 지금 값 — 앞선 리스너가 바꿨으면 바꾼 값. 타입 래퍼가 없는 종류면 OpaquePacket. */
    P packet();

    /**
     * 값을 바꾼다. 같은 종류여야 한다(아니면 IllegalArgumentException). 타입 래퍼가 없는 종류(OpaquePacket)는 바꿀 수 없다
     * (UnsupportedOperationException). MONITOR 우선순위에서는 IllegalStateException.
     */
    void setPacket(P packet);

    boolean isCancelled();

    /** true 면 이 패킷을 버린다(클라이언트로 가지 않거나, 서버가 처리하지 않는다). MONITOR 우선순위에서는 IllegalStateException. */
    void setCancelled(boolean cancelled);

    /** 플러그인이 PacketService.send/receive 로 넣은 패킷인가. */
    boolean isPluginPacket();
}
