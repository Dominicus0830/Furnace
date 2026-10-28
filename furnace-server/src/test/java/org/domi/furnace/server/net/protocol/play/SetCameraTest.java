// set_camera(0x52, packets.json 1.21.1 minecraft:set_camera = 82): 본문은 엔티티 id 의 VarInt 하나.
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class SetCameraTest {

    private static byte[] body(int entityId) {
        ByteBuf b = Unpooled.buffer();
        new SetCamera(entityId).write(b);
        byte[] out = new byte[b.readableBytes()];
        b.readBytes(out);
        return out;
    }

    @Test
    void idMatchesVanillaProtocolAndBodyIsAVarInt() {
        assertEquals(82, SetCamera.ID);
        assertEquals(82, new SetCamera(1).id());
        assertEquals(1, body(1).length);
        assertEquals(1, body(1)[0]);
        // 300 = 0b1_0010_1100 → VarInt 0xAC 0x02
        byte[] b300 = body(300);
        assertEquals(2, b300.length);
        assertEquals((byte) 0xAC, b300[0]);
        assertEquals((byte) 0x02, b300[1]);
        // 큰 id 도 5바이트 안
        assertEquals(5, body(Integer.MAX_VALUE).length);
    }
}
