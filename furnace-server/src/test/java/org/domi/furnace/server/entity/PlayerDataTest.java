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
        PlayerData.save(dir, id, 12.5, -60, -7.25, 90f, 10f, true);
        double[] pos = PlayerData.load(dir, id);
        assertNotNull(pos);
        assertEquals(12.5, pos[0]);
        assertEquals(-60, pos[1]);
        assertEquals(-7.25, pos[2]);
        assertEquals(90f, (float) pos[3]);
        assertEquals(10f, (float) pos[4]);
        assertEquals(1.0, pos[5]); // onGround
        assertNull(PlayerData.load(dir, UUID.randomUUID())); // 없음
    }
}
