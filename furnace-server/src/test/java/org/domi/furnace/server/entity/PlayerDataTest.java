// 플레이어 위치 NBT write→read 라운드트립
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDataTest {

    @Test
    void writeRead_position(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        ItemStack[] slots = new ItemStack[46];
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        slots[36] = new ItemStack(123, 64, new byte[]{1, 2, 3});
        PlayerData.save(dir, id, 12.5, -60, -7.25, 90f, 10f, true, slots);
        PlayerData.Snapshot snapshot = PlayerData.load(dir, id);
        assertNotNull(snapshot);
        assertEquals(12.5, snapshot.x());
        assertEquals(-60, snapshot.y());
        assertEquals(-7.25, snapshot.z());
        assertEquals(90f, snapshot.yaw());
        assertEquals(10f, snapshot.pitch());
        assertTrue(snapshot.onGround());
        assertEquals(123, snapshot.slots()[36].itemId());
        assertEquals(64, snapshot.slots()[36].count());
        assertArrayEquals(new byte[]{1, 2, 3}, snapshot.slots()[36].components());
        assertNull(PlayerData.load(dir, UUID.randomUUID())); // 없음
    }
}
