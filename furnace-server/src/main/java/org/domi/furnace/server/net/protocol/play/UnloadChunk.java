// S→C 청크 언로드(플레이어 시야 벗어남).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record UnloadChunk(int chunkX, int chunkZ) implements Packet {

    public static final int ID = 0x21;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeInt(chunkX);
        buf.writeInt(chunkZ);
    }
}
