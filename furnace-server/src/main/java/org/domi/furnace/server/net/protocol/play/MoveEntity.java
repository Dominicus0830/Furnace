// S→C 엔티티 상대 이동(ClientboundMoveEntityPacket.Pos 0x2E·PosRot 0x2F·Rot 0x30): 위치 델타는 1/4096 블록 short,
// 각은 추적기가 계산한 1/256 바퀴 byte 그대로.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record MoveEntity(int kind, int entityId, short dx, short dy, short dz, int yRot, int xRot, boolean onGround) implements Packet {

    public static final int POS = 0x2E, POS_ROT = 0x2F, ROT = 0x30;

    public static MoveEntity pos(int id, short dx, short dy, short dz, boolean onGround) { return new MoveEntity(POS, id, dx, dy, dz, 0, 0, onGround); }

    public static MoveEntity posRot(int id, short dx, short dy, short dz, int yRot, int xRot, boolean onGround) {
        return new MoveEntity(POS_ROT, id, dx, dy, dz, yRot, xRot, onGround);
    }

    public static MoveEntity rot(int id, int yRot, int xRot, boolean onGround) { return new MoveEntity(ROT, id, (short) 0, (short) 0, (short) 0, yRot, xRot, onGround); }

    @Override
    public int id() { return kind; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        if (kind != ROT) {
            buf.writeShort(dx);
            buf.writeShort(dy);
            buf.writeShort(dz);
        }
        if (kind != POS) {
            buf.writeByte(yRot);
            buf.writeByte(xRot);
        }
        buf.writeBoolean(onGround);
    }
}
