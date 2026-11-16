// 엔티티의 절대 위치와 방향. 각도는 도다.
package org.domi.furnace.api.packet.clientbound;

import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;

public record ClientboundTeleportEntityPacket(int entityId, double x, double y, double z, float yaw, float pitch, boolean onGround)
        implements ClientboundPacket {

    @Override
    public PacketType<ClientboundTeleportEntityPacket> type() { return PacketTypes.Clientbound.TELEPORT_ENTITY; }
}
