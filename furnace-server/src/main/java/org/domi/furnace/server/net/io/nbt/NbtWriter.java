// 네트워크 NBT(1.20.2+, 루트 무명) write. heightmap 전송용 최소 태그만.
package org.domi.furnace.server.net.io.nbt;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

public final class NbtWriter {

    private static final byte TAG_END = 0x00;
    private static final byte TAG_COMPOUND = 0x0a;
    private static final byte TAG_LONG_ARRAY = 0x0c;

    private final ByteBuf buf;

    public NbtWriter(ByteBuf buf) {
        this.buf = buf;
    }

    /** 루트 compound 시작(이름 없음). */
    public void beginRootCompound() {
        buf.writeByte(TAG_COMPOUND);
    }

    public void writeLongArray(String name, long[] values) {
        buf.writeByte(TAG_LONG_ARRAY);
        writeName(name);
        buf.writeInt(values.length);
        for (long v : values) buf.writeLong(v);
    }

    public void endCompound() {
        buf.writeByte(TAG_END);
    }

    private void writeName(String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }
}
