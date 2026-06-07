// S→C 연결 유지 핑. 클라는 같은 id로 Keep Alive(serverbound 0x18)를 되돌려야 한다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record KeepAliveClientbound(long keepAliveId) implements Packet {

    public static final int ID = 0x26;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeLong(keepAliveId);
    }
}
