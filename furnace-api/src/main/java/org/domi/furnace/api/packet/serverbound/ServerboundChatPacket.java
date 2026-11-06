// 채팅 메시지. 1.19 부터 채팅은 서명된다 — message 를 바꾸면 서명이 맞지 않아 서버가 거절하거나(보안 채팅 강제 시) 서명 없는 메시지로 처리된다.
// 내용을 바꾸려면 AsyncChatEvent 를 쓰고, 이 패킷은 관찰·취소에 쓴다. signature 는 256바이트(서명 안 했으면 null).
// lastSeenOffset·lastSeenAcknowledged(20비트)는 클라이언트가 확인한 메시지 목록 — 그대로 둔다.
package org.domi.furnace.api.packet.serverbound;

import java.time.Instant;
import java.util.Objects;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundChatPacket(String message, Instant timestamp, long salt, byte[] signature, int lastSeenOffset, int lastSeenAcknowledged)
        implements ServerboundPacket {

    public ServerboundChatPacket {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(timestamp, "timestamp");
        if (signature != null) {
            if (signature.length != 256) throw new IllegalArgumentException("서명은 256바이트: " + signature.length);
            signature = signature.clone();
        }
    }

    @Override
    public byte[] signature() { return signature == null ? null : signature.clone(); }

    @Override
    public PacketType<ServerboundChatPacket> type() { return PacketTypes.Serverbound.CHAT; }
}
