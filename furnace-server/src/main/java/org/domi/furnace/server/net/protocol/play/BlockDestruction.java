// 블록 금 가는 단계(clientbound block_destruction): 부수는 엔티티 id, 위치, 단계 u8(0..9, 그 밖이면 지움).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record BlockDestruction(int entityId, int x, int y, int z, int progress) implements Packet {

    public static final int ID = 0x06; // datagen: play.clientbound.block_destruction

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeBlockPos(buf, x, y, z);
        buf.writeByte(progress);
    }
}
