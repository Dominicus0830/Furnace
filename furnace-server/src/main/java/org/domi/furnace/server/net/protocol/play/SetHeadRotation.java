// S→C 엔티티 머리 yaw(몸통 yaw와 별도).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetHeadRotation(int entityId, float headYaw) implements Packet {

    public static final int ID = 0x48;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeAngle(buf, headYaw);
    }
}
