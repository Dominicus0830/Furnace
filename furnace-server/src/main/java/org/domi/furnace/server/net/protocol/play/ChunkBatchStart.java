// S→C 청크 배치 시작 표시. 이후 청크 패킷들을 한 배치로 묶는다(body 없음).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record ChunkBatchStart() implements Packet {

    public static final int ID = 0x0D;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        // body 없음
    }
}
