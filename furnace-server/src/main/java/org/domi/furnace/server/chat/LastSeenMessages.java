// 바닐라 LastSeenMessages — 메시지가 서명 범위에 포함하는 "마지막으로 본" 서명들(최대 20).
package org.domi.furnace.server.chat;

import io.netty.buffer.ByteBuf;
import java.security.Signature;
import java.security.SignatureException;
import java.util.BitSet;
import java.util.List;
import org.domi.furnace.server.net.io.Types;

public record LastSeenMessages(List<MessageSignature> entries) {

    public static final LastSeenMessages EMPTY = new LastSeenMessages(List.of());
    public static final int LAST_SEEN_MESSAGES_MAX_LENGTH = 20;

    public void updateSignature(Signature out) throws SignatureException {
        out.update(Bytes.ofInt(entries.size()));
        for (MessageSignature s : entries) out.update(s.bytes());
    }

    public void writePacked(ByteBuf buf, MessageSignatureCache cache) {
        Types.writeVarInt(buf, entries.size());
        for (MessageSignature s : entries) s.pack(cache).write(buf);
    }

    /** 클라이언트가 보내는 갱신: 창을 앞으로 민 개수와 20비트 확인 비트셋. */
    public record Update(int offset, BitSet acknowledged) {
        public static Update read(ByteBuf buf) {
            int offset = Types.readVarInt(buf);
            byte[] bits = new byte[3]; // FixedBitSet(20) = ceil(20/8)
            buf.readBytes(bits);
            return new Update(offset, BitSet.valueOf(bits));
        }
    }
}
