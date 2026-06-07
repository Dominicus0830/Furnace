// S→C 엔티티 이동 델타(±8블록 내). 델타 = (new-old)*4096 short.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record MoveEntityPos(int entityId, short dx, short dy, short dz, boolean onGround)
        implements Packet {

    public static final int ID = 0x2E;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        buf.writeShort(dx);
        buf.writeShort(dy);
        buf.writeShort(dz);
        buf.writeBoolean(onGround);
    }
}
