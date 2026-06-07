// S→C 엔티티 절대좌표 텔레포트(델타 범위 초과 시).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record TeleportEntity(int entityId, double x, double y, double z,
                             float yaw, float pitch, boolean onGround) implements Packet {

    public static final int ID = 0x70;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        Types.writeAngle(buf, yaw);
        Types.writeAngle(buf, pitch);
        buf.writeBoolean(onGround);
    }
}
