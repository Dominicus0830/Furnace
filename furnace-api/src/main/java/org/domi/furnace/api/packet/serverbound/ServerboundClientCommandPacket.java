// 리스폰 요청·통계 요청.
package org.domi.furnace.api.packet.serverbound;

import java.util.Objects;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundClientCommandPacket(Action action) implements ServerboundPacket {

    public enum Action { PERFORM_RESPAWN, REQUEST_STATS }

    public ServerboundClientCommandPacket {
        Objects.requireNonNull(action, "action");
    }

    @Override
    public PacketType<ServerboundClientCommandPacket> type() { return PacketTypes.Serverbound.CLIENT_COMMAND; }
}
