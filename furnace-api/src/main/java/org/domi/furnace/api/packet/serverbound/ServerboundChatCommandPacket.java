// 명령 입력(앞의 '/' 없이). 서명할 인자가 있는 명령(/msg 등)은 chat_command_signed 로 오며 여기 오지 않는다.
package org.domi.furnace.api.packet.serverbound;

import java.util.Objects;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundChatCommandPacket(String command) implements ServerboundPacket {

    public ServerboundChatCommandPacket {
        Objects.requireNonNull(command, "command");
    }

    @Override
    public PacketType<ServerboundChatCommandPacket> type() { return PacketTypes.Serverbound.CHAT_COMMAND; }
}
