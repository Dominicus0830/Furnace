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
                assertEquals("Can't spawn EntityType UNKNOWN from mob spawners!",
                        assertThrows(IllegalArgumentException.class, () -> s.setSpawnedType(EntityType.UNKNOWN)).getMessage());
                s.setMinSpawnDelay(s.getMaxSpawnDelay());   // 경계: 같으면 된다
                s.setMinSpawnDelay(300);
                assertTrue(s.update());

                NbtCompound nbt = w.blockEntityNbtNow(pos).orElseThrow();
                assertEquals("minecraft:zombie", nbt.getCompound("SpawnData").getCompound("entity").getString("id"));
                assertEquals(5, nbt.getShort("Delay"));
                assertEquals(300, nbt.getShort("MinSpawnDelay"));
                assertEquals(1000, nbt.getShort("MaxSpawnDelay"));

                CreatureSpawner again = (CreatureSpawner) Compat.block(dim, pos).getState();
                assertEquals(EntityType.ZOMBIE, again.getSpawnedType());
                assertEquals("minecraft:zombie", again.getCreatureTypeName(), "Paper bukkitToString = 키 전체");
                assertEquals(2, again.getSpawnCount());
                assertEquals(24, again.getRequiredPlayerRange());

                again.setCreatureTypeByName("skeleton");
                assertEquals(EntityType.SKELETON, again.getSpawnedType());
                again.setCreatureTypeByName("minecraft:Zombie");
                assertEquals(EntityType.ZOMBIE, again.getSpawnedType());
                again.setCreatureTypeByName(again.getCreatureTypeName());
                assertEquals(EntityType.ZOMBIE, again.getSpawnedType(), "이름 왕복");
                assertThrows(IllegalArgumentException.class, () -> again.setCreatureTypeByName(null));
                again.setCreatureTypeByName("없는_몹");
                assertNull(again.getSpawnedType(), "모르는 이름은 생성기를 비운다(Paper)");
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
        assertEquals(20, s.getDelay());
        assertEquals(200, s.getMinSpawnDelay());
        assertEquals(800, s.getMaxSpawnDelay());
        assertEquals(4, s.getSpawnCount());
        assertEquals(6, s.getMaxNearbyEntities());
        assertEquals(16, s.getRequiredPlayerRange());
        assertEquals(4, s.getSpawnRange());
        s.setSpawnedType(EntityType.PIG);
        assertEquals(EntityType.PIG, s.getSpawnedType());
    }

    /** Paper 는 사본 BaseSpawner 의 int 필드 — short 를 넘는 지연도 그대로이고, 저장은 Short.MAX 로 자른 바닐라 키 + Paper.* int 키. */
    @Test
    void delaysAboveShortRangeFollowPaper() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:spawner", Map.of()));
            try {
                CreatureSpawner s = (CreatureSpawner) Compat.block(dim, pos).getState();
                s.setDelay(40000);
                s.setMaxSpawnDelay(50000);
                assertEquals(40000, s.getDelay());
                assertEquals(50000, s.getMaxSpawnDelay());
                assertTrue(s.update());
                NbtCompound nbt = w.blockEntityNbtNow(pos).orElseThrow();
                assertEquals(Short.MAX_VALUE, nbt.getShort("Delay"));
                assertEquals(40000, nbt.getInt("Paper.Delay"));
                assertEquals(Short.MAX_VALUE, nbt.getShort("MaxSpawnDelay"));
                assertEquals(50000, nbt.getInt("Paper.MaxSpawnDelay"));
                assertEquals(200, nbt.getInt("Paper.MinSpawnDelay"));

                CreatureSpawner again = (CreatureSpawner) Compat.block(dim, pos).getState();
                assertEquals(40000, again.getDelay(), "서버 생성기도 Paper.* 키를 읽는다");
                assertEquals(50000, again.getMaxSpawnDelay());
                again.setDelay(10);
                again.setMaxSpawnDelay(900);
                assertTrue(again.update());
                NbtCompound back = w.blockEntityNbtNow(pos).orElseThrow();
                assertFalse(back.contains("Paper.Delay"), "범위 안이면 바닐라와 같은 저장");
                assertFalse(back.contains("Paper.MaxSpawnDelay"));
                assertEquals(10, back.getShort("Delay"));
                assertEquals(10, ((CreatureSpawner) Compat.block(dim, pos).getState()).getDelay());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** setSpawnedType(종류) 는 다음 SpawnData 의 id 만 바꾸고 후보는 그대로, setSpawnedType(null) 은 후보를 비운다. */
    @Test
    void potentialsKeptBySetTypeAndClearedByNull() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:spawner", Map.of()));
            try {
                NbtCompound start = w.blockEntityNbtNow(pos).orElseThrow();
                var potentials = new org.domi.furnace.api.nbt.NbtList();
                for (String id : new String[] {"minecraft:pig", "minecraft:cow"}) {
                    NbtCompound entity = new NbtCompound().putString("id", id);
                    NbtCompound data = new NbtCompound();
                    data.putCompound("entity", entity);
                    NbtCompound entry = new NbtCompound().putInt("weight", 1);
                    entry.putCompound("data", data);
                    potentials.add(entry);
                }
                start.putList("SpawnPotentials", potentials);
                NbtCompound sd = new NbtCompound();
                sd.putCompound("entity", new NbtCompound().putString("id", "minecraft:pig"));
                start.putCompound("SpawnData", sd);
                assertTrue(w.setBlockEntityNbtNow(pos, start));

                CreatureSpawner s = (CreatureSpawner) Compat.block(dim, pos).getState();
                assertEquals(EntityType.PIG, s.getSpawnedType());
                s.setSpawnedType(EntityType.ZOMBIE);
                assertTrue(s.update());
                NbtCompound nbt = w.blockEntityNbtNow(pos).orElseThrow();
                assertEquals("minecraft:zombie", nbt.getCompound("SpawnData").getCompound("entity").getString("id"), "있는 SpawnData 를 바꾼다");
                assertEquals(2, nbt.getList("SpawnPotentials").size(), "후보는 그대로");

                CreatureSpawner again = (CreatureSpawner) Compat.block(dim, pos).getState();
                again.setSpawnedType(null);
                assertTrue(again.update());
                assertEquals(0, w.blockEntityNbtNow(pos).orElseThrow().getList("SpawnPotentials").size(), "null 은 후보를 비운다");
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }
}
