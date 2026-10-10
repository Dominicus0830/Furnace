// S→C 암호화 요청(0x01) — serverId(빈 문자열), 서버 공개키(DER), 검증 토큰 4바이트, 세션 인증 여부.
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record EncryptionRequest(String serverId, byte[] publicKey, byte[] verifyToken, boolean shouldAuthenticate) implements Packet {

    public static final int ID = 0x01;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, serverId);
        Types.writeVarInt(buf, publicKey.length);
        buf.writeBytes(publicKey);
        Types.writeVarInt(buf, verifyToken.length);
        buf.writeBytes(verifyToken);
        buf.writeBoolean(shouldAuthenticate);
    }
}
