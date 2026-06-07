// paletted container 인코딩: 단일/간접/직접 케이스 와이어 바이트 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class PaletteTest {

    @Test
    void single_writesBitsZero_valueAndZeroLongs() {
        int[] all = new int[4096]; // 전부 0
        ByteBuf buf = Unpooled.buffer();
        Palette.write(buf, all, 15, 4, 8);
        assertEquals(0, buf.readByte());          // bitsPerEntry = 0
        assertEquals(0, Types.readVarInt(buf));   // single value
        assertEquals(0, Types.readVarInt(buf));   // long count
        assertFalse(buf.isReadable());
        buf.release();
    }

    @Test
    void indirect_fourDistinct_usesMinFourBits() {
        int[] vals = new int[4096];
        for (int i = 0; i < 4096; i++) vals[i] = i % 4 == 0 ? 79 : (i % 4 == 1 ? 10 : (i % 4 == 2 ? 9 : 0));
        ByteBuf buf = Unpooled.buffer();
        Palette.write(buf, vals, 15, 4, 8);
        assertEquals(4, buf.readByte());          // 고유 4개 → 4비트(최소)
        assertEquals(4, Types.readVarInt(buf));   // 팰릿 크기 4
        for (int i = 0; i < 4; i++) Types.readVarInt(buf); // 팰릿 값들
        int longCount = Types.readVarInt(buf);
        assertEquals(256, longCount);             // 4비트 4096칸 = 256 long
        buf.release();
    }

    @Test
    void direct_whenTooManyDistinct() {
        int[] vals = new int[4096];
        for (int i = 0; i < 4096; i++) vals[i] = i; // 4096 고유 → 직접
        ByteBuf buf = Unpooled.buffer();
        Palette.write(buf, vals, 15, 4, 8);
        assertEquals(15, buf.readByte());         // direct bits
        int longCount = Types.readVarInt(buf);    // 팰릿 없이 바로 long 수
        // 15비트 valuesPerLong=4 → ceil(4096/4)=1024
        assertEquals(1024, longCount);
        buf.release();
    }
}
