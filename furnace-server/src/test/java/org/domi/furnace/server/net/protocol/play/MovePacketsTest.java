// 이동/회전/텔레포트/언로드 패킷 id·와이어 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class MovePacketsTest {

    @Test
    void moveEntityPos_writesDeltas() {
        MoveEntityPos p = new MoveEntityPos(5, (short) 100, (short) 0, (short) -50, true);
        assertEquals(0x2E, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(5, Types.readVarInt(buf));
        assertEquals(100, buf.readShort());
        assertEquals(0, buf.readShort());
        assertEquals(-50, buf.readShort());
        assertEquals(1, buf.readByte());
        buf.release();
    }

    @Test
    void teleportEntity_writesAbsolute() {
        TeleportEntity p = new TeleportEntity(5, 10.0, -60.0, 20.0, 90f, 0f, true);
        assertEquals(0x70, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(5, Types.readVarInt(buf));
        assertEquals(10.0, buf.readDouble());
        buf.release();
    }

    @Test
    void unloadChunk_writesPackedLong() {
        UnloadChunk p = new UnloadChunk(3, -5);
        assertEquals(0x21, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        long packed = buf.readLong();
        assertEquals(3, (int) (packed & 0xFFFFFFFFL)); // x = 하위 32비트
        assertEquals(-5, (int) (packed >> 32));         // z = 상위 32비트
        buf.release();
    }

    @Test
    void headRotation_andRotOnly_ids() {
        assertEquals(0x48, new SetHeadRotation(5, 0f).id());
        assertEquals(0x30, new MoveEntityRot(5, 0f, 0f, true).id());
        assertEquals(0x2F, new MoveEntityPosRot(5, (short)1,(short)1,(short)1, 0f,0f, true).id());
    }
}
