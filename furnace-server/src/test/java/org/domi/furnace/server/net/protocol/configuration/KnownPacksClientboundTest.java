// Clientbound Known Packs 패킷이 minecraft:core/1.21.1 팩 1개를 정확히 직렬화하는지 검증
package org.domi.furnace.server.net.protocol.configuration;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class KnownPacksClientboundTest {

    @Test
    void writes_singleCorePack() {
        KnownPacksClientbound p = new KnownPacksClientbound();
        assertEquals(0x0E, p.id());

        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(1, Types.readVarInt(buf));            // pack count
        assertEquals("minecraft", Types.readString(buf, 256)); // namespace
        assertEquals("core", Types.readString(buf, 256));      // id
        assertEquals("1.21.1", Types.readString(buf, 256));    // version
        assertFalse(buf.isReadable());
        buf.release();
    }
}
