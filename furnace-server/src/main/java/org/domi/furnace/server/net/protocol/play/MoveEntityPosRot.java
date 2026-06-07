// S→C 엔티티 이동+회전 델타.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record MoveEntityPosRot(int entityId, short dx, short dy, short dz,
                               float yaw, float pitch, boolean onGround) implements Packet {

    public static final int ID = 0x2F;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        buf.writeShort(dx);
        buf.writeShort(dy);
        buf.writeShort(dz);
        Types.writeAngle(buf, yaw);
        Types.writeAngle(buf, pitch);
        buf.writeBoolean(onGround);
    }
}
