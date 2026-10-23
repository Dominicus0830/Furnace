// 바닐라 MessageSignature — 256바이트 RSA 서명. 네트워크로는 캐시 id(Packed) 또는 전체 서명.
package org.domi.furnace.server.chat;

import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Base64;
import org.domi.furnace.server.net.io.Types;

public final class MessageSignature {

    public static final int BYTES = 256;
    private final byte[] bytes;

    public MessageSignature(byte[] bytes) {
        if (bytes.length != BYTES) throw new IllegalStateException("Invalid message signature size");
        this.bytes = bytes;
    }

    public static MessageSignature read(ByteBuf buf) {
        byte[] b = new byte[BYTES];
        buf.readBytes(b);
        return new MessageSignature(b);
    }

    public void write(ByteBuf buf) { buf.writeBytes(bytes); }

    public byte[] bytes() { return bytes; }

    /** 수신자 캐시에 있으면 id, 없으면 전체 서명(바닐라 MessageSignature.pack). */
    public Packed pack(MessageSignatureCache cache) {
        int id = cache.pack(this);
        return id != -1 ? new Packed(id, null) : new Packed(-1, this);
    }

    public record Packed(int id, MessageSignature full) {
        public void write(ByteBuf buf) {
            Types.writeVarInt(buf, id + 1);
            if (full != null) full.write(buf);
        }
    }

    @Override
    public boolean equals(Object o) { return o instanceof MessageSignature m && Arrays.equals(bytes, m.bytes); }

    @Override
    public int hashCode() { return Arrays.hashCode(bytes); }

    @Override
    public String toString() { return Base64.getEncoder().encodeToString(bytes); }
}
