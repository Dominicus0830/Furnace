// 블록 이벤트(clientbound block_event): 위치, 종류 u8, 값 u8, 블록 레지스트리 id.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record BlockEventPacket(int x, int y, int z, int type, int data, int blockId) implements Packet {

    public static final int ID = 0x08;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeBlockPos(buf, x, y, z);
        buf.writeByte(type);
        buf.writeByte(data);
        Types.writeVarInt(buf, blockId);
    }
}
