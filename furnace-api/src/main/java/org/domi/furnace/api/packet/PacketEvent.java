// 가로챈 패킷 하나. 리스너는 내용을 읽고(packet), 다른 값으로 바꾸거나(setPacket), 버릴 수 있다(setCancelled).
// 이 객체는 리스너가 불리는 동안에만 쓴다. 다른 스레드에 넘기거나 나중에 쓰지 않는다.
package org.domi.furnace.api.packet;

import org.domi.furnace.api.entity.Player;

public interface PacketEvent<P extends Packet> {

    /** 이 패킷의 플레이어. CLIENTBOUND 면 패킷을 받는 플레이어, SERVERBOUND 면 패킷을 보낸 플레이어다. */
    Player player();

    PacketType<P> type();

    /** 지금 값. 앞선 리스너가 바꿨으면 바꾼 값이다. 타입 레코드가 없는 종류면 GenericPacket 이다. */
    P packet();

    /**
     * 값을 바꾼다. 같은 종류여야 한다.
     *
     * <ul>
     * <li>다른 종류면 IllegalArgumentException 을 던진다.
     * <li>서버가 내용을 풀지 못하는 종류(PacketService.isReadable 이 false)는 바꿀 수 없다. UnsupportedOperationException 을 던진다.
     * <li>이 버전의 와이어로 쓸 수 없는 값(GenericPacket 의 빠진 필드나 모르는 필드, 범위 밖 값, 없는 이름 등)이면 그 자리에서 IllegalArgumentException 을 던진다.
     * <li>MONITOR 우선순위에서는 IllegalStateException 을 던진다.
     * </ul>
     */
    void setPacket(P packet);

    boolean isCancelled();

    /**
     * true 면 이 패킷을 버린다. 나가는 패킷은 클라이언트로 가지 않고, 들어오는 패킷은 서버가 처리하지 않는다.
     *
     * <p>MONITOR 우선순위에서는 IllegalStateException 을 던진다.
     * <p>번들 구분자(bundle_delimiter)는 짝이 어긋나면 클라이언트가 연결을 끊으므로, 취소하거나 바꾸면 UnsupportedOperationException 을 던진다.
     */
    void setCancelled(boolean cancelled);

    /** 플러그인이 PacketService.send 나 receive 로 넣은 패킷인가. */
    boolean isPluginPacket();
}
