// S→C 머리 방향(rotate_head 0x48) — 추적기가 계산한 1/256 바퀴 byte.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record RotateHead(int entityId, int yHeadRot) implements Packet {

    public static final int ID = 0x48;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        buf.writeByte(yHeadRot);
    }
}
