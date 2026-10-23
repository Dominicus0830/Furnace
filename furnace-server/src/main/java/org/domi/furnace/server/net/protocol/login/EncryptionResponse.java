// C→S 암호화 응답(0x01) — 서버 공개키로 암호화한 공유 비밀과 검증 토큰.
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.ProtocolException;

public record EncryptionResponse(byte[] sharedSecret, byte[] verifyToken) {

    public static final int ID = 0x01;
    private static final int MAX_LENGTH = 256; // RSA-1024 암호문은 128바이트

    public static EncryptionResponse read(ByteBuf buf) {
        return new EncryptionResponse(readBytes(buf), readBytes(buf));
    }

    private static byte[] readBytes(ByteBuf buf) {
        int n = Types.readVarInt(buf);
        if (n < 0 || n > MAX_LENGTH || n > buf.readableBytes()) throw new ProtocolException("암호화 응답 길이 오류: " + n);
        byte[] b = new byte[n];
        buf.readBytes(b);
        return b;
    }
}
