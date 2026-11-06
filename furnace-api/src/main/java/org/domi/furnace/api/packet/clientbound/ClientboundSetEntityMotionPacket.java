// 엔티티 속도(블록/틱). 축마다 ±3.9 로 잘리고 1/8000 단위로 보내진다.
package org.domi.furnace.api.packet.clientbound;

import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;

public record ClientboundSetEntityMotionPacket(int entityId, double velocityX, double velocityY, double velocityZ) implements ClientboundPacket {

    @Override
    public PacketType<ClientboundSetEntityMotionPacket> type() { return PacketTypes.Clientbound.SET_ENTITY_MOTION; }
}
