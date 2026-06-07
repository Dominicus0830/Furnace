// Registry Data 패킷이 [registryId][count][entry(id, hasData=false)...] 구조로 쓰이는지 검증
package org.domi.furnace.server.net.protocol.configuration;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class RegistryDataTest {

    @Test
    void writes_registryId_count_andEntriesWithoutData() {
        RegistryData p = new RegistryData(
                "minecraft:dimension_type",
                List.of("minecraft:overworld", "minecraft:the_nether"));
        assertEquals(0x07, p.id());

        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals("minecraft:dimension_type", Types.readString(buf, 256)); // registry id
        assertEquals(2, Types.readVarInt(buf));                                // entry count
        assertEquals("minecraft:overworld", Types.readString(buf, 256));
        assertFalse(buf.readBoolean());                                        // hasData=false
        assertEquals("minecraft:the_nether", Types.readString(buf, 256));
        assertFalse(buf.readBoolean());
        assertFalse(buf.isReadable());
        buf.release();
    }
}
