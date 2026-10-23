// S→C 엔티티 스폰(add_entity 0x01, ClientboundAddEntityPacket(entity, serverEntity, data)): 위치는 추적기의 기준 위치,
// 회전은 추적기가 마지막에 보낸 각(1/256 바퀴 byte), 속도는 ±3.9 × 8000 short.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record AddEntity(int entityId, UUID uuid, int type, double x, double y, double z, int xRot, int yRot, int yHeadRot,
                        int data, double vx, double vy, double vz) implements Packet {

    public static final int ID = 0x01;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeUuid(buf, uuid);
        Types.writeVarInt(buf, type);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeByte(xRot);
        buf.writeByte(yRot);
        buf.writeByte(yHeadRot);
        Types.writeVarInt(buf, data);
        buf.writeShort(SetEntityMotion.encode(vx));
        buf.writeShort(SetEntityMotion.encode(vy));
        buf.writeShort(SetEntityMotion.encode(vz));
    }
}
