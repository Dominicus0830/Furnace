// 엔티티 머리 방향(yaw, 도).
package org.domi.furnace.api.packet.clientbound;

import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;

public record ClientboundRotateHeadPacket(int entityId, float headYaw) implements ClientboundPacket {

    @Override
    public PacketType<ClientboundRotateHeadPacket> type() { return PacketTypes.Clientbound.ROTATE_HEAD; }
}
