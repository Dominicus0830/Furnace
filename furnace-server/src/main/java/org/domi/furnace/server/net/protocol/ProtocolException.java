// 프로토콜 규약 위반(잘못된 VarInt, 미지원 패킷 등)을 나타내는 예외
package org.domi.furnace.server.net.protocol;

public final class ProtocolException extends RuntimeException {
    public ProtocolException(String message) {
        super(message);
    }
}
