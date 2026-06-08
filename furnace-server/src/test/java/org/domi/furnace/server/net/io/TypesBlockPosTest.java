// Position 패킹/언패킹 라운드트립(음수 좌표 포함) 검증
package org.domi.furnace.server.net.io;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class TypesBlockPosTest {

    @Test
    void blockPos_roundTrips() {
        ByteBuf buf = Unpooled.buffer();
        Types.writeBlockPos(buf, 100, -60, -200);
        int[] p = Types.readBlockPos(buf);
        assertArrayEquals(new int[]{100, -60, -200}, p);
        buf.release();
    }

    @Test
    void blockPos_handlesZeroAndMaxY() {
        ByteBuf buf = Unpooled.buffer();
        Types.writeBlockPos(buf, 0, 319, 0);
        int[] p = Types.readBlockPos(buf);
        assertArrayEquals(new int[]{0, 319, 0}, p);
        buf.release();
    }
}
