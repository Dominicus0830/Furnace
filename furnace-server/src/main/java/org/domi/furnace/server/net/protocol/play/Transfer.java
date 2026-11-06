// S→C 다른 서버로 옮기기(0x73, 1.20.5+): 호스트 문자열 + 포트 varint.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record Transfer(String host, int port) implements Packet {

    public static final int ID = 0x73;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, host);
        Types.writeVarInt(buf, port);
    }
}
