// 엔티티 제거(클라이언트 화면에서만).
package org.domi.furnace.api.packet.clientbound;

import java.util.List;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;

public record ClientboundRemoveEntitiesPacket(List<Integer> entityIds) implements ClientboundPacket {

    public ClientboundRemoveEntitiesPacket {
        entityIds = List.copyOf(entityIds);
    }

    public static ClientboundRemoveEntitiesPacket of(int... ids) {
        Integer[] boxed = new Integer[ids.length];
        for (int i = 0; i < ids.length; i++) boxed[i] = ids[i];
        return new ClientboundRemoveEntitiesPacket(List.of(boxed));
    }

    @Override
    public PacketType<ClientboundRemoveEntitiesPacket> type() { return PacketTypes.Clientbound.REMOVE_ENTITIES; }
}
