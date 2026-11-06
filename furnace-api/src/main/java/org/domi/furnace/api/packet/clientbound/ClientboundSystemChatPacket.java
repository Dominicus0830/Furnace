// 시스템 채팅(명령 결과·공지). actionBar 면 채팅 창이 아니라 핫바 위에 뜬다.
package org.domi.furnace.api.packet.clientbound;

import java.util.Objects;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.text.Text;

public record ClientboundSystemChatPacket(Text message, boolean actionBar) implements ClientboundPacket {

    public ClientboundSystemChatPacket {
        Objects.requireNonNull(message, "message");
    }

    public ClientboundSystemChatPacket withMessage(Text m) { return new ClientboundSystemChatPacket(m, actionBar); }

    @Override
    public PacketType<ClientboundSystemChatPacket> type() { return PacketTypes.Clientbound.SYSTEM_CHAT; }
}
