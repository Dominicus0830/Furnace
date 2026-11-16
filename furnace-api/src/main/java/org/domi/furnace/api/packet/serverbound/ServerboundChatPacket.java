// 채팅 메시지. 1.19 부터 채팅은 서명된다.
// message 를 바꾸면 서명이 맞지 않는다. 보안 채팅을 강제하면 서버가 거절하고, 아니면 서명 없는 메시지로 처리한다.
// 내용을 바꾸려면 AsyncChatEvent 를 쓰고, 이 패킷은 관찰과 취소에만 쓴다.
//
// signature 는 256바이트이며, 서명하지 않았으면 null 이다.
// lastSeenOffset 과 lastSeenAcknowledged(20비트)는 클라이언트가 확인한 메시지 목록이다. 그대로 둔다.
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
            if (signature.length != 256) throw new IllegalArgumentException("the signature must be 256 bytes: " + signature.length);
            signature = signature.clone();
        }
    }

    @Override
    public byte[] signature() { return signature == null ? null : signature.clone(); }

    @Override
    public PacketType<ServerboundChatPacket> type() { return PacketTypes.Serverbound.CHAT; }
}
