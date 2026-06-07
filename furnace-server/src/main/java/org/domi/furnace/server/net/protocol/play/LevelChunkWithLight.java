// S→C 청크 데이터+라이트. body는 ChunkSerializer가 채운 ByteBuf를 그대로 싣는다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record LevelChunkWithLight(ByteBuf body) implements Packet {

    public static final int ID = 0x27;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBytes(body);
        body.release();
    }
}
