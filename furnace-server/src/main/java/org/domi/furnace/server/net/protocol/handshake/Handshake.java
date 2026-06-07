// C→S 핸드셰이크 패킷. 프로토콜 버전과 다음 단계(status/login)를 알린다.
package org.domi.furnace.server.net.protocol.handshake;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;

public record Handshake(int protocolVersion, String serverAddress, int serverPort, int nextState) {

    public static final int ID = 0x00;

    public static Handshake read(ByteBuf buf) {
        int protocol = Types.readVarInt(buf);
        String addr = Types.readString(buf, 255);
        int port = buf.readUnsignedShort();
        int next = Types.readVarInt(buf);
        return new Handshake(protocol, addr, port, next);
    }
}
