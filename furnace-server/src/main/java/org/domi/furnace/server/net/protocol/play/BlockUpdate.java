// 단일 블록 변경 알림(clientbound). Position + 블록 state id.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record BlockUpdate(int x, int y, int z, int stateId) implements Packet {

    public static final int ID = 0x09; // datagen 확정 대상

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeBlockPos(buf, x, y, z);
        Types.writeVarInt(buf, stateId);
    }
}
