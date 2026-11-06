// S→C 다른 서버로 옮기기(구성 0x0B): 호스트 문자열 + 포트 VarInt. (play 단계는 play.Transfer 0x73)
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record ConfigTransfer(String host, int port) implements Packet {

    public static final int ID = 0x0B;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, host);
        Types.writeVarInt(buf, port);
    }
}
