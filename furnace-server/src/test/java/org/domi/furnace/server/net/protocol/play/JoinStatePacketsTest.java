// 접속 시 상태 패킷의 와이어 형식 검증(레이아웃은 바닐라 1.21.1 서버 jar 바이트코드에서 확인한 값)
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class JoinStatePacketsTest {

    private static ByteBuf write(org.domi.furnace.server.net.protocol.Packet p) {
        ByteBuf b = Unpooled.buffer();
        p.write(b);
        return b;
    }

    @Test
    void setTime_twoLongs() {
        SetTime p = new SetTime(123L, 456L);
        assertEquals(0x64, p.id());
        ByteBuf b = write(p);
        assertEquals(16, b.readableBytes());
        assertEquals(123L, b.readLong());
        assertEquals(456L, b.readLong());
    }

    @Test
    void changeDifficulty_byteAndBool() {
        ChangeDifficulty p = new ChangeDifficulty(1, false);
        assertEquals(0x0B, p.id());
        ByteBuf b = write(p);
        assertEquals(1, b.readUnsignedByte());
        assertFalse(b.readBoolean());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void playerAbilities_creativeFlagsAndSpeeds() {
        PlayerAbilities p = PlayerAbilities.creative();
        assertEquals(0x38, p.id());
        ByteBuf b = write(p);
        assertEquals(1 | 4 | 8, b.readByte());   // invulnerable | mayfly | instabuild
        assertEquals(0.05f, b.readFloat());
        assertEquals(0.1f, b.readFloat());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void initializeBorder_layout() {
        InitializeBorder p = InitializeBorder.vanillaDefault();
        assertEquals(0x25, p.id());
        ByteBuf b = write(p);
        assertEquals(0.0, b.readDouble());
        assertEquals(0.0, b.readDouble());
        assertEquals(5.9999968E7, b.readDouble());
        assertEquals(5.9999968E7, b.readDouble());
        assertEquals(0, b.readByte());            // VarLong 0
        assertEquals(29999984, Types.readVarInt(b));
        assertEquals(5, Types.readVarInt(b));
        assertEquals(15, Types.readVarInt(b));
        assertEquals(0, b.readableBytes());
    }

    @Test
    void varLong_roundTripsLargeValue() {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarLong(b, 1_700_000_000_000L);
        long v = 0; int shift = 0; byte cur;
        do { cur = b.readByte(); v |= (long) (cur & 0x7F) << shift; shift += 7; } while ((cur & 0x80) != 0);
        assertEquals(1_700_000_000_000L, v);
    }

    @Test
    void setHealthAndExperience_layouts() {
        ByteBuf b = write(new SetHealth(20f, 20, 5f));
        assertEquals(20f, b.readFloat());
        assertEquals(20, Types.readVarInt(b));
        assertEquals(5f, b.readFloat());
        assertEquals(0, b.readableBytes());
        assertEquals(0x5D, new SetHealth(1, 1, 1).id());
        ByteBuf e = write(new SetExperience(0.5f, 3, 40));
        assertEquals(0.5f, e.readFloat());
        assertEquals(3, Types.readVarInt(e));
        assertEquals(40, Types.readVarInt(e));
        assertEquals(0x5C, new SetExperience(0, 0, 0).id());
    }
}
