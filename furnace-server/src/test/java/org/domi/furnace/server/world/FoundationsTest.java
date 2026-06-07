// ChunkPos 동등성, 엔티티 id 발급, Angle 인코딩 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class FoundationsTest {

    @Test
    void chunkPos_equalsAndHashWork_inSet() {
        java.util.Set<ChunkPos> set = new java.util.HashSet<>();
        set.add(new ChunkPos(1, -2));
        assertTrue(set.contains(new ChunkPos(1, -2)));
        assertFalse(set.contains(new ChunkPos(1, 2)));
    }

    @Test
    void entityId_increments_from1() {
        EntityIdAllocator alloc = new EntityIdAllocator();
        assertEquals(1, alloc.next());
        assertEquals(2, alloc.next());
    }

    @Test
    void writeAngle_maps360To256() {
        ByteBuf buf = Unpooled.buffer();
        Types.writeAngle(buf, 0f);
        Types.writeAngle(buf, 90f);
        Types.writeAngle(buf, 180f);
        assertEquals(0, buf.readByte() & 0xFF);
        assertEquals(64, buf.readByte() & 0xFF);   // 90/360*256
        assertEquals(128, buf.readByte() & 0xFF);  // 180/360*256
        buf.release();
    }
}
