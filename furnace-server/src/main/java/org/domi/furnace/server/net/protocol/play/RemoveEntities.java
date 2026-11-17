// S→C 엔티티 제거(디스폰). entityId 목록.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket")
public record RemoveEntities(List<Integer> entityIds) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_REMOVE_ENTITIES;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityIds.size());
        for (int id : entityIds) Types.writeVarInt(buf, id);
    }
}
