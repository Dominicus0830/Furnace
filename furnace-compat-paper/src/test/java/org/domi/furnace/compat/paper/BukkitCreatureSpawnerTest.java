package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/** T-141 E2c: 생성기 상태(Paper CraftCreatureSpawner) — 바닐라 BaseSpawner NBT 위의 종류·지연·횟수·범위. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitCreatureSpawnerTest {

    FurnaceTestServer server;
    BlockPos pos;
    final String dim = "minecraft:overworld";

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        server.tick(3);
        pos = new BlockPos(9, -20, 9);
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void load() { server.loadChunk(pos.chunkX(), pos.chunkZ()); }

    @Test
    void readsVanillaDefaultsAndWritesBack() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:spawner", Map.of()));
            try {
                CreatureSpawner s = (CreatureSpawner) Compat.block(dim, pos).getState();
                assertNull(s.getSpawnedType(), "새 생성기는 다음 스폰 데이터가 없다");
                assertNull(s.getCreatureTypeName());
                assertEquals(20, s.getDelay());
                assertEquals(200, s.getMinSpawnDelay());
                assertEquals(800, s.getMaxSpawnDelay());
                assertEquals(4, s.getSpawnCount());
                assertEquals(6, s.getMaxNearbyEntities());
                assertEquals(16, s.getRequiredPlayerRange());
                assertEquals(4, s.getSpawnRange());

                s.setSpawnedType(EntityType.ZOMBIE);
                s.setDelay(5);
                s.setMaxSpawnDelay(1000);
                s.setMinSpawnDelay(300);
                s.setSpawnCount(2);
                s.setRequiredPlayerRange(24);
                assertEquals("Minimum Spawn Delay must be less than or equal to Maximum Spawn Delay",
                        assertThrows(IllegalArgumentException.class, () -> s.setMinSpawnDelay(2000)).getMessage());
                assertEquals("Maximum Spawn Delay must be greater than 0.", assertThrows(IllegalArgumentException.class, () -> s.setMaxSpawnDelay(0)).getMessage());
                assertEquals("Maximum Spawn Delay must be greater than or equal to Minimum Spawn Delay",
                        assertThrows(IllegalArgumentException.class, () -> s.setMaxSpawnDelay(100)).getMessage());
                assertThrows(IllegalArgumentException.class, () -> s.setSpawnedType(EntityType.UNKNOWN));
                assertTrue(s.update());

                NbtCompound nbt = w.blockEntityNbtNow(pos).orElseThrow();
                assertEquals("minecraft:zombie", nbt.getCompound("SpawnData").getCompound("entity").getString("id"));
                assertEquals(5, nbt.getShort("Delay"));
                assertEquals(300, nbt.getShort("MinSpawnDelay"));
                assertEquals(1000, nbt.getShort("MaxSpawnDelay"));

                CreatureSpawner again = (CreatureSpawner) Compat.block(dim, pos).getState();
                assertEquals(EntityType.ZOMBIE, again.getSpawnedType());
                assertEquals("zombie", again.getCreatureTypeName());
                assertEquals(2, again.getSpawnCount());
                assertEquals(24, again.getRequiredPlayerRange());

                again.setCreatureTypeByName("skeleton");
                assertEquals(EntityType.SKELETON, again.getSpawnedType());
                again.setSpawnedType(null);
                assertNull(again.getSpawnedType(), "빈 SpawnData");
                assertTrue(again.update());
                NbtCompound cleared = w.blockEntityNbtNow(pos).orElseThrow();
                assertEquals(0, cleared.getList("SpawnPotentials").size());
                assertTrue(cleared.getCompound("SpawnData").getCompound("entity").isEmpty());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    @Test
    void unplacedSpawnerUsesVanillaDefaults() {
        CreatureSpawner s = (CreatureSpawner) Material.SPAWNER.createBlockData().createBlockState();
        assertNull(s.getSpawnedType());
        assertEquals(800, s.getMaxSpawnDelay());
        s.setSpawnedType(EntityType.PIG);
        assertEquals(EntityType.PIG, s.getSpawnedType());
    }
}
