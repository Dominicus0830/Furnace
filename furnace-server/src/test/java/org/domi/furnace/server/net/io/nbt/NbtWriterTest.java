// 루트 무명 compound + long_array + end 바이트 시퀀스 검증
package org.domi.furnace.server.net.io.nbt;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class NbtWriterTest {

    @Test
    void writesRootCompound_withLongArray() {
        ByteBuf buf = Unpooled.buffer();
        NbtWriter w = new NbtWriter(buf);
        w.beginRootCompound();
        w.writeLongArray("MOTION_BLOCKING", new long[]{1L, 2L});
        w.endCompound();

        assertEquals(0x0a, buf.readByte());       // 루트 compound 타입
        // 루트는 이름 없음 → 바로 첫 태그
        assertEquals(0x0c, buf.readByte());       // long_array 타입
        assertEquals(15, buf.readUnsignedShort());// 이름 길이 "MOTION_BLOCKING"=15
        byte[] name = new byte[15];
        buf.readBytes(name);
        assertEquals("MOTION_BLOCKING", new String(name, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(2, buf.readInt());           // 배열 길이
        assertEquals(1L, buf.readLong());
        assertEquals(2L, buf.readLong());
        assertEquals(0x00, buf.readByte());       // end
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    void writeCompound_emptyCompound_isTypeThenEnd() {
        ByteBuf buf = Unpooled.buffer();
        new NbtWriter(buf).writeCompound(new NbtCompound());

        assertEquals(0x0a, buf.readByte()); // TAG_COMPOUND
        assertEquals(0x00, buf.readByte()); // TAG_END
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    void writeCompound_scalarAndNestedFields_matchByteLayout() {
        NbtCompound inner = new NbtCompound();
        inner.putString("Name", "hi");

        NbtCompound root = new NbtCompound();
        root.putByte("Slot", (byte) 3);
        root.putInt("Id", 42);
        root.putByteArray("Comp", new byte[]{9, 8});
        root.putCompound("Nested", inner);

        ByteBuf buf = Unpooled.buffer();
        new NbtWriter(buf).writeCompound(root);

        assertEquals(0x0a, buf.readByte());       // 루트 TAG_COMPOUND

        assertEquals(0x01, buf.readByte());       // Slot: TAG_BYTE
        assertEquals(4, buf.readUnsignedShort());
        assertEquals("Slot", readAscii(buf, 4));
        assertEquals(3, buf.readByte());

        assertEquals(0x03, buf.readByte());       // Id: TAG_INT
        assertEquals(2, buf.readUnsignedShort());
        assertEquals("Id", readAscii(buf, 2));
        assertEquals(42, buf.readInt());

        assertEquals(0x07, buf.readByte());       // Comp: TAG_BYTE_ARRAY
        assertEquals(4, buf.readUnsignedShort());
        assertEquals("Comp", readAscii(buf, 4));
        assertEquals(2, buf.readInt());
        assertEquals(9, buf.readByte());
        assertEquals(8, buf.readByte());

        assertEquals(0x0a, buf.readByte());       // Nested: TAG_COMPOUND
        assertEquals(6, buf.readUnsignedShort());
        assertEquals("Nested", readAscii(buf, 6));
        assertEquals(0x08, buf.readByte());       //   Name: TAG_STRING
        assertEquals(4, buf.readUnsignedShort());
        assertEquals("Name", readAscii(buf, 4));
        assertEquals(2, buf.readUnsignedShort());
        assertEquals("hi", readAscii(buf, 2));
        assertEquals(0x00, buf.readByte());       // Nested compound 종료

        assertEquals(0x00, buf.readByte());       // 루트 compound 종료
        assertFalse(buf.isReadable());
        buf.release();
    }

    private static String readAscii(ByteBuf buf, int len) {
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
