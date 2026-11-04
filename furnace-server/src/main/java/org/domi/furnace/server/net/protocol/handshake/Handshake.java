// C→S 핸드셰이크 패킷. 프로토콜 버전과 다음 단계(status/login)를 알린다.
package org.domi.furnace.server.net.protocol.handshake;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;

public record Handshake(int protocolVersion, String serverAddress, int serverPort, int nextState) {

    public static final int ID = 0x00;

    /** 바닐라 주소 칸 상한. */
    public static final int MAX_ADDRESS = 255;
    /** BungeeCord 레거시 포워딩을 켰을 때 상한(Spigot 은 IP·UUID·속성 JSON 을 싣기 위해 Short.MAX_VALUE 로 늘린다). */
    public static final int MAX_FORWARDED_ADDRESS = Short.MAX_VALUE;

    public static Handshake read(ByteBuf buf) {
        return read(buf, MAX_ADDRESS);
    }

    public static Handshake read(ByteBuf buf, int maxAddress) {
        int protocol = Types.readVarInt(buf);
        String addr = Types.readString(buf, maxAddress);
        int port = buf.readUnsignedShort();
        int next = Types.readVarInt(buf);
        return new Handshake(protocol, addr, port, next);
    }
}
