// BlockUpdate가 Position + stateId를 와이어에 쓰는지 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class BlockUpdateTest {

    @Test
    void write_emitsPosAndState() {
        BlockUpdate p = new BlockUpdate(10, -60, 20, 0);
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        int[] pos = Types.readBlockPos(buf);
        assertArrayEquals(new int[]{10, -60, 20}, pos);
        assertEquals(0, Types.readVarInt(buf)); // air
        buf.release();
    }
}
