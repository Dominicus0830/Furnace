// 팔 휘두르기(좌클릭 동작).
package org.domi.furnace.api.packet.serverbound;

import java.util.Objects;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundSwingPacket(Hand hand) implements ServerboundPacket {

    public ServerboundSwingPacket {
        Objects.requireNonNull(hand, "hand");
    }

    @Override
    public PacketType<ServerboundSwingPacket> type() { return PacketTypes.Serverbound.SWING; }
}
