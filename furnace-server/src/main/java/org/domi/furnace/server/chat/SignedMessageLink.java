// 바닐라 SignedMessageLink — (index, sender, session) 사슬 고리. 서명 범위의 앞부분.
package org.domi.furnace.server.chat;

import java.security.Signature;
import java.security.SignatureException;
import java.util.UUID;

public record SignedMessageLink(int index, UUID sender, UUID sessionId) {

    public static final UUID NIL_UUID = new UUID(0L, 0L);

    public static SignedMessageLink unsigned(UUID sender) { return root(sender, NIL_UUID); }

    public static SignedMessageLink root(UUID sender, UUID sessionId) { return new SignedMessageLink(0, sender, sessionId); }

    public void updateSignature(Signature out) throws SignatureException {
        out.update(Bytes.ofUuid(sender));
        out.update(Bytes.ofUuid(sessionId));
        out.update(Bytes.ofInt(index));
    }

    public SignedMessageLink advance() { return index == Integer.MAX_VALUE ? null : new SignedMessageLink(index + 1, sender, sessionId); }
}
