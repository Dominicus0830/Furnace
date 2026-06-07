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
}
