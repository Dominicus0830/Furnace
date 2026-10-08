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

    /** 여러 스레드가 같은 플레이어를 동시에 저장해도 파일이 깨지지 않고(.corrupt 없음) 항상 읽혀야 한다. */
    @Test
    void concurrentSaves_neverCorruptTheFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.util.UUID id = java.util.UUID.randomUUID();
        ItemStack[] slots = new ItemStack[46];
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        PlayerData.save(dir, id, 0, 0, 0, 0f, 0f, true, slots);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.List<java.util.concurrent.Future<?>> fs = new java.util.ArrayList<>();
        for (int t = 0; t < 6; t++) {
            final int tt = t;
            fs.add(pool.submit(() -> { for (int i = 0; i < 150; i++) PlayerData.save(dir, id, tt, i, 0, 0f, 0f, true, slots); }));
        }
        for (int t = 0; t < 2; t++) {
            fs.add(pool.submit(() -> { for (int i = 0; i < 150; i++) assertNotNull(PlayerData.load(dir, id)); }));
        }
        for (var f : fs) f.get();
        pool.shutdown();
        assertNotNull(PlayerData.load(dir, id));
        try (var st = java.nio.file.Files.list(dir.resolve("playerdata"))) {
            assertEquals(1, st.count(), "임시/손상 파일이 남으면 안 된다");
        }
    }

    /** 늦게 도착한(오래된 시퀀스) 저장이 최신 저장을 덮어쓰면 안 된다. */
    @Test
    void staleSequencedSave_doesNotOverwriteNewer(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        java.util.UUID id = java.util.UUID.randomUUID();
        ItemStack[] slots = new ItemStack[46];
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        long older = PlayerData.nextSeq();
        long newer = PlayerData.nextSeq();
        PlayerData.saveSequenced(newer, dir, id, 2, 0, 0, 0f, 0f, true, slots);
        PlayerData.saveSequenced(older, dir, id, 1, 0, 0, 0f, 0f, true, slots);
        assertEquals(2.0, PlayerData.load(dir, id).x());
    }
}
