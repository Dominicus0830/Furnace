// 타입 래퍼가 아직 없는 패킷. 종류만 알 수 있고 내용은 읽거나 바꿀 수 없다 — 가로채서 관찰·취소하는 데만 쓴다.
// 보낼 수 없다(send/receive 는 IllegalArgumentException).
package org.domi.furnace.api.packet;

import java.util.Objects;

public record OpaquePacket(PacketType<OpaquePacket> type) implements ClientboundPacket, ServerboundPacket {

    public OpaquePacket {
        Objects.requireNonNull(type, "type");
    }
}
