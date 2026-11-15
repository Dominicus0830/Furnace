package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.block.Lockable;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/** T-141 E7a: Lockable(용기·신호기 열쇠) — 상태에서 바꾼 열쇠가 update 로 블록 NBT Lock 에 실리고, 풀면 키가 빠진다(바닐라 LockCode). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitLockableTest {

    FurnaceTestServer server;
    BlockPos pos;
    final String dim = "minecraft:overworld";

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        server.tick(3);
        pos = new BlockPos(29, -20, 29);
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void load() { server.loadChunk(pos.chunkX(), pos.chunkZ()); }

    private void lockRoundTrip(String block, Map<String, String> props) { lockRoundTrip(block, props, ""); }

    /** unlockWith: 풀 때 쓰는 값("" 또는 null — Paper setLock(null)=LockCode.NO_LOCK). */
    private void lockRoundTrip(String block, Map<String, String> props, String unlockWith) {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData(block, props));
            try {
                Lockable s = (Lockable) Compat.block(dim, pos).getState();
                assertFalse(s.isLocked(), block);
                assertEquals("", s.getLock());
                s.setLock("key");
                assertTrue(s.isLocked());
                assertTrue(((org.bukkit.block.BlockState) s).update());
                assertEquals("key", w.blockEntityNbtNow(pos).orElseThrow().getString("Lock"), block + ": 블록에 실렸다");
                Lockable again = (Lockable) Compat.block(dim, pos).getState();
                assertEquals("key", again.getLock());
                again.setLock(unlockWith);
                assertTrue(((org.bukkit.block.BlockState) again).update());
                assertFalse(w.blockEntityNbtNow(pos).orElseThrow().contains("Lock"), "빈 열쇠는 쓰지 않는다");
                assertFalse(((Lockable) Compat.block(dim, pos).getState()).isLocked());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    @Test
    void barrelLock() { lockRoundTrip("minecraft:barrel", Map.of()); }

    @Test
    void barrelUnlockWithNull() { lockRoundTrip("minecraft:barrel", Map.of(), null); }

    @Test
    void chestLock() { lockRoundTrip("minecraft:chest", Map.of("facing", "north", "type", "single")); }

    @Test
    void beaconLock() { lockRoundTrip("minecraft:beacon", Map.of()); }

    @Test
    void lockableTypes() {
        assertTrue(Lockable.class.isAssignableFrom(org.bukkit.block.Container.class));
        assertTrue(Lockable.class.isAssignableFrom(org.bukkit.block.Beacon.class));
        assertFalse(Lockable.class.isAssignableFrom(org.bukkit.block.Sign.class));
    }
}
