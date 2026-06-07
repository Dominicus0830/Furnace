// 와이어 데이터타입(VarInt/String/UUID) 라운드트립 테스트
package org.domi.furnace.server.net.io;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.domi.furnace.server.net.protocol.ProtocolException;
import org.junit.jupiter.api.Test;

class TypesTest {

    @Test
    void varInt_roundTrips_boundaryValues() {
        int[] values = {0, 1, 127, 128, 255, 2_097_151, Integer.MAX_VALUE, -1};
        for (int v : values) {
            ByteBuf buf = Unpooled.buffer();
            Types.writeVarInt(buf, v);
            assertEquals(v, Types.readVarInt(buf), "value=" + v);
            assertFalse(buf.isReadable(), "leftover bytes for " + v);
            buf.release();
        }
    }

    @Test
    void varInt_minValue_uses5Bytes() {
        ByteBuf buf = Unpooled.buffer();
        Types.writeVarInt(buf, -1);
        assertEquals(5, buf.readableBytes());
        buf.release();
    }

    @Test
    void varInt_throws_whenTooLong() {
        ByteBuf buf = Unpooled.buffer();
        for (int i = 0; i < 6; i++) buf.writeByte(0x80);
        assertThrows(ProtocolException.class, () -> Types.readVarInt(buf));
        buf.release();
    }

    @Test
    void string_roundTrips() {
        ByteBuf buf = Unpooled.buffer();
        Types.writeString(buf, "Furnace ⛏ 한글");
        assertEquals("Furnace ⛏ 한글", Types.readString(buf, 256));
        buf.release();
    }

    @Test
    void uuid_roundTrips() {
        UUID id = UUID.randomUUID();
        ByteBuf buf = Unpooled.buffer();
        Types.writeUuid(buf, id);
        assertEquals(id, Types.readUuid(buf));
        buf.release();
    }
}
