// S→C 엔티티 회전만.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

<<<<<<< Updated upstream
@VanillaPort("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Rot")
=======
@VanillaPort("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot")
>>>>>>> Stashed changes
public record MoveEntityRot(int entityId, float yaw, float pitch, boolean onGround)
        implements Packet {

    public static final int ID = 0x30;

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
