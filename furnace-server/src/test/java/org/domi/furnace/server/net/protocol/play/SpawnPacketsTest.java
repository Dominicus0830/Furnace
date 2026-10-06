// 스폰/디스폰 패킷 id·와이어 검증
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.junit.jupiter.api.Test;

class SpawnPacketsTest {

    @Test
    void playerInfoUpdate_addPlayer() {
        UUID id = UUID.randomUUID();
        PlayerInfoUpdate p = new PlayerInfoUpdate(id, "Dom");
        assertEquals(0x3E, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(0x09, buf.readByte());        // actions add_player|update_listed
        assertEquals(1, Types.readVarInt(buf));     // count
        assertEquals(id, Types.readUuid(buf));
        assertEquals("Dom", Types.readString(buf, 16));
        assertEquals(0, Types.readVarInt(buf));     // props
        assertEquals(1, buf.readByte());            // listed = true
        buf.release();
    }

    @Test
    void spawnEntity_writesTypeAndPos() {
        UUID id = UUID.randomUUID();
        SpawnEntity p = new SpawnEntity(5, id, 1.0, -60.0, 2.0, 0f, 90f);
        assertEquals(0x01, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(5, Types.readVarInt(buf));     // entityId
        assertEquals(id, Types.readUuid(buf));
        assertEquals(128, Types.readVarInt(buf));   // player type
        assertEquals(1.0, buf.readDouble());
        buf.release();
    }

    @Test
    void removeEntities_writesIds() {
        RemoveEntities p = new RemoveEntities(java.util.List.of(5, 9));
        assertEquals(0x42, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(2, Types.readVarInt(buf));
        assertEquals(5, Types.readVarInt(buf));
        assertEquals(9, Types.readVarInt(buf));
        buf.release();
    }

    @Test
    void playerInfoRemove_listsUuids() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PlayerInfoRemove p = new PlayerInfoRemove(java.util.List.of(a, b));
        assertEquals(0x3D, p.id());               // 바닐라 datagen player_info_remove = 61
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        assertEquals(2, Types.readVarInt(buf));    // count
        assertEquals(a, Types.readUuid(buf));
        assertEquals(b, Types.readUuid(buf));
        assertFalse(buf.isReadable());
        buf.release();
    }
}
