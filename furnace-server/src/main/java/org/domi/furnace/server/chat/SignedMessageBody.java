// 바닐라 SignedMessageBody — 내용·시각·솔트·last seen. 서명 범위의 뒷부분.
package org.domi.furnace.server.chat;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Instant;
import org.domi.furnace.server.net.io.Types;

public record SignedMessageBody(String content, Instant timeStamp, long salt, LastSeenMessages lastSeen) {

    public static SignedMessageBody unsigned(String content) { return new SignedMessageBody(content, Instant.now(), 0L, LastSeenMessages.EMPTY); }

    public void updateSignature(Signature out) throws SignatureException {
        out.update(Bytes.ofLong(salt));
        out.update(Bytes.ofLong(timeStamp.getEpochSecond()));
        byte[] b = content.getBytes(StandardCharsets.UTF_8);
        out.update(Bytes.ofInt(b.length));
        out.update(b);
        lastSeen.updateSignature(out);
    }

    /** 수신자 캐시로 압축해 쓴다(바닐라 SignedMessageBody.Packed). */
    public void writePacked(ByteBuf buf, MessageSignatureCache cache) {
        Types.writeString(buf, content);
        buf.writeLong(timeStamp.toEpochMilli());
        buf.writeLong(salt);
        lastSeen.writePacked(buf, cache);
    }
}
