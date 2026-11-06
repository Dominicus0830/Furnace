// 레벨 이벤트(clientbound level_event): Int 종류, 위치, Int 값, Boolean 전역.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record LevelEventPacket(int type, int x, int y, int z, int data, boolean global) implements Packet {

    public static final int ID = 0x28;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeInt(type);
        Types.writeBlockPos(buf, x, y, z);
        buf.writeInt(data);
        buf.writeBoolean(global);
    }
}
