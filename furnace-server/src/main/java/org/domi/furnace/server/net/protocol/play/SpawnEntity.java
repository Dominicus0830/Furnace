// S→C 엔티티 스폰(M3a는 플레이어, type=128). 헤드yaw·속도 포함.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SpawnEntity(int entityId, UUID uuid, double x, double y, double z,
                          float yaw, float pitch) implements Packet {

    public static final int ID = 0x01;
    private static final int PLAYER_TYPE = 128;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeUuid(buf, uuid);
        Types.writeVarInt(buf, PLAYER_TYPE);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        Types.writeAngle(buf, pitch);
        Types.writeAngle(buf, yaw);
        Types.writeAngle(buf, yaw); // head yaw
        Types.writeVarInt(buf, 0);  // data
        buf.writeShort(0);          // velX
        buf.writeShort(0);          // velY
        buf.writeShort(0);          // velZ
    }
}
