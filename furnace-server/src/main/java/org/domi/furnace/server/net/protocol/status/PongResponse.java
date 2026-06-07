// S→C 핑 응답. 클라가 보낸 페이로드(Long)를 그대로 되돌려 지연 측정에 쓰게 한다.
package org.domi.furnace.server.net.protocol.status;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record PongResponse(long payload) implements Packet {

    public static final int ID = 0x01;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeLong(payload);
    }
}
