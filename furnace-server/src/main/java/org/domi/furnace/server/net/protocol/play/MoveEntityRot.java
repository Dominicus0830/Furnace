// S→C 엔티티 회전만.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot")
public record MoveEntityRot(int entityId, float yaw, float pitch, boolean onGround)
        implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_MOVE_ENTITY_ROT;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeAngle(buf, yaw);
        Types.writeAngle(buf, pitch);
        buf.writeBoolean(onGround);
    }
}
