package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** Bukkit Block·BlockData·World·Entity 의 H3 보강: 블록 쓰기, 데이터 문자열, 엔티티 만들기·근처 찾기, 소유 스레드 규칙. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockEntityWorldAdapterTest {

    FurnaceTestServer server;
    TestPlayer tp;
    World world;
    BlockPos origin;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CompatServer.INSTANCE);
        tp = server.join("Dana");
        server.tick(3);
        var loc = tp.player().location();
        origin = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()) + 5, (int) Math.floor(loc.z()));
        server.loadChunk(origin.chunkX(), origin.chunkZ());
        world = Compat.world(server.overworld());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(origin, task); }

    Block block(int dx, int dy, int dz) { return world.getBlockAt(origin.x() + dx, origin.y() + dy, origin.z() + dz); }

    // ------------------------------------------------------------------ 블록

    @Test
    void setTypeAndReadBack() {
        owner(() -> {
            Block b = block(0, 0, 0);
            b.setType(Material.STONE);
            assertEquals(Material.STONE, b.getType());
            assertFalse(b.isEmpty());
            b.setType(Material.OAK_STAIRS);
            BlockData d = b.getBlockData();
            assertEquals(Material.OAK_STAIRS, d.getMaterial());
            // 세상에서 읽은 데이터는 모든 속성이 적혀 있다(이름순)
            assertEquals("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", d.getAsString());
            b.setType(Material.AIR);
            assertTrue(b.isEmpty());
            b.setType(Material.WATER);
            assertTrue(b.isLiquid());
            b.setType(Material.AIR);
            return null;
        });
    }

    @Test
    void blockDataStrings() {
        BlockData stairs = Bukkit.createBlockData("oak_stairs[facing=east]");
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", stairs.getAsString());
        assertEquals("minecraft:oak_stairs[facing=east]", stairs.getAsString(true), "적지 않은 속성은 숨긴다");
        assertEquals(stairs, Bukkit.createBlockData(Material.OAK_STAIRS, "facing=east"));
        assertEquals(stairs, Material.OAK_STAIRS.createBlockData("[facing=east]"));
        assertEquals("minecraft:stone", Bukkit.createBlockData(Material.STONE).getAsString());
        // matches 는 적은 속성만 본다
        BlockData any = Bukkit.createBlockData("oak_stairs");
        assertTrue(stairs.matches(any), "any 는 아무 속성도 적지 않았으니 모두 맞다");
        assertFalse(any.matches(stairs), "any 의 기본 방향은 north 라 east 와 다르다");
        assertTrue(Bukkit.createBlockData("oak_stairs[facing=east]").matches(stairs));
        assertFalse(Bukkit.createBlockData("oak_stairs[facing=west]").matches(stairs));
        assertFalse(Bukkit.createBlockData(Material.STONE).matches(stairs));
        assertThrows(IllegalArgumentException.class, () -> Bukkit.createBlockData("oak_stairs[facing=up]"));
        assertThrows(IllegalArgumentException.class, () -> Bukkit.createBlockData("stone[nope=1]"));
        assertThrows(IllegalArgumentException.class, () -> Bukkit.createBlockData("not_a_block"));
        assertThrows(IllegalArgumentException.class, () -> Bukkit.createBlockData(Material.DIAMOND_SWORD));
    }

    @Test
    void setBlockDataWithStateAndTheRejectedPhysicsFlag() {
        owner(() -> {
            Block b = block(2, 0, 0);
            b.setBlockData(Bukkit.createBlockData("oak_stairs[facing=south,half=top]"));
            assertEquals("minecraft:oak_stairs[facing=south,half=top,shape=straight,waterlogged=false]", b.getBlockData().getAsString());
            assertThrows(UnsupportedFeature.class, () -> b.setType(Material.STONE, false));
            assertEquals(Material.OAK_STAIRS, b.getType(), "거절된 호출은 아무것도 바꾸지 않는다");
            assertThrows(IllegalArgumentException.class, () -> b.setType(Material.DIAMOND_SWORD));
            b.setType(Material.AIR);
            return null;
        });
    }

    @Test
    void relativeFaceAndLocation() {
        Block b = block(0, 0, 0);
        assertEquals(origin.y() + 1, b.getRelative(BlockFace.UP).getY());
        assertEquals(origin.z() - 3, b.getRelative(BlockFace.NORTH, 3).getZ());
        assertEquals(BlockFace.EAST, b.getFace(b.getRelative(BlockFace.EAST)));
        assertNull(b.getFace(block(5, 5, 5)));
        Location l = b.getLocation(new Location(null, 0, 0, 0));
        assertEquals(origin.x(), l.getBlockX());
        assertEquals(world, l.getWorld());
        assertEquals(origin.chunkX(), b.getChunk().getX());
        assertEquals(b, world.getBlockAt(b.getLocation()));
    }

    @Test
    void readsNeedTheOwnerThreadAndWritesAreDeferredElsewhere() {
        Block b = block(4, 0, 0);
        assertThrows(ThreadOwnershipException.class, b::getType);
        assertThrows(ThreadOwnershipException.class, b::getBlockData);
        // 소유 스레드가 아닌 쓰기는 예약된다
        b.setType(Material.GOLD_BLOCK);
        server.tick(2);
        assertEquals(Material.GOLD_BLOCK, owner(b::getType));
        owner(() -> {
            b.setType(Material.AIR);
            return null;
        });
        // 청크가 로드돼 있지 않은 곳은 동기로 불러오지 않는다
        Block far = world.getBlockAt(origin.x() + 16 * 40, origin.y(), origin.z());
        assertThrows(IllegalStateException.class, () -> server.onRegion(new BlockPos(far.getX(), far.getY(), far.getZ()), () -> {
            far.setType(Material.STONE);
            return null;
        }));
    }

    // ------------------------------------------------------------------ 엔티티·월드

    @Test
    void spawnAndFindEntities() {
        Location at = new Location(world, origin.x() + 0.5, origin.y() + 2, origin.z() + 0.5);
        Entity zombie = owner(() -> world.spawnEntity(at, EntityType.ZOMBIE));
        assertEquals(EntityType.ZOMBIE, zombie.getType());
        assertEquals("minecraft:zombie", zombie.getTypeKey());
        Entity pig = owner(() -> world.spawnEntity(at.clone().add(3, 0, 0), EntityType.PIG));
        Entity farCow = owner(() -> world.spawnEntity(at.clone().add(30, 0, 0), EntityType.COW));
        server.tick(1);

        Collection<Entity> near = owner(() -> world.getNearbyEntities(at, 5, 5, 5));
        assertTrue(near.contains(zombie));
        assertTrue(near.contains(pig));
        assertFalse(near.contains(farCow));
        // 구가 아니라 상자: x 방향 2 칸까지만
        Collection<Entity> thin = owner(() -> world.getNearbyEntities(at, 2, 5, 5));
        assertTrue(thin.contains(zombie));
        assertFalse(thin.contains(pig));
        // 엔티티 기준 — 자기 자신은 뺀다
        var around = owner(() -> zombie.getNearbyEntities(4, 4, 4));
        assertTrue(around.contains(pig));
        assertFalse(around.contains(zombie));

        assertThrows(ThreadOwnershipException.class, () -> world.getNearbyEntities(at, 5, 5, 5));
        assertThrows(ThreadOwnershipException.class, () -> world.spawnEntity(at, EntityType.COW));
        assertThrows(IllegalArgumentException.class, () -> owner(() -> world.spawnEntity(at, EntityType.PLAYER)));
        assertThrows(IllegalArgumentException.class, () -> owner(() -> world.spawnEntity(at, EntityType.UNKNOWN)));
        owner(() -> {
            zombie.remove();
            pig.remove();
            farCow.remove();
            return null;
        });
    }

    @Test
    void entityPersistentDataIsLiveOnTheOwnerThread() {
        var key = new org.bukkit.NamespacedKey("furnace", "tag");
        var num = new org.bukkit.NamespacedKey("furnace", "n");
        Location at = new Location(world, origin.x() + 0.5, origin.y() + 2, origin.z() + 6.5);
        Entity pig = owner(() -> world.spawnEntity(at, EntityType.PIG));
        var pdc = pig.getPersistentDataContainer();
        owner(() -> {
            assertTrue(pdc.isEmpty());
            pdc.set(key, org.bukkit.persistence.PersistentDataType.STRING, "marked");
            pdc.set(num, org.bukkit.persistence.PersistentDataType.INTEGER, 4);
            // 쓰자마자 읽힌다(Bukkit 처럼) — 같은 핸들이 아니라 지금 값을 읽는다
            assertEquals("marked", pig.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING));
            assertEquals(2, pdc.getKeys().size());
            assertThrows(IllegalArgumentException.class, () -> pdc.get(key, org.bukkit.persistence.PersistentDataType.INTEGER));
            var inner = pdc.getAdapterContext().newPersistentDataContainer();
            inner.set(num, org.bukkit.persistence.PersistentDataType.LONG, 9L);
            pdc.set(new org.bukkit.NamespacedKey("furnace", "box"), org.bukkit.persistence.PersistentDataType.TAG_CONTAINER, inner);
            assertEquals(9L, pdc.get(new org.bukkit.NamespacedKey("furnace", "box"), org.bukkit.persistence.PersistentDataType.TAG_CONTAINER)
                    .get(num, org.bukkit.persistence.PersistentDataType.LONG));
            pdc.remove(num);
            assertFalse(pdc.has(num));
            return null;
        });
        // 소유 스레드가 아니면 읽기는 거절, 쓰기는 예약
        assertThrows(ThreadOwnershipException.class, () -> pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING));
        pdc.set(num, org.bukkit.persistence.PersistentDataType.INTEGER, 11);
        for (int i = 0; i < 10 && !owner(() -> pdc.has(num)); i++) server.tick(1);
        assertEquals(11, owner(() -> pdc.get(num, org.bukkit.persistence.PersistentDataType.INTEGER)));
        owner(() -> {
            pig.remove();
            return null;
        });
        server.tick(1);

        // 플레이어의 영속 데이터는 서버가 잠가 두어 어느 스레드에서나 읽고 쓴다
        var ppdc = Compat.player(tp.player()).getPersistentDataContainer();
        ppdc.set(key, org.bukkit.persistence.PersistentDataType.STRING, "me");
        assertEquals("me", ppdc.get(key, org.bukkit.persistence.PersistentDataType.STRING));
        ppdc.remove(key);
        assertTrue(ppdc.isEmpty());
    }

    @Test
    void unsupportedPartsSayWhatIsMissing() {
        Entity e = Compat.player(tp.player());
        UnsupportedFeature ex = assertThrows(UnsupportedFeature.class, () -> e.setCustomName("x"));
        assertTrue(ex.getMessage().contains("setCustomName"), ex.getMessage());
        assertEquals(EntityType.PLAYER, e.getType());
        assertNotNull(Compat.player(tp.player()).getInventory());
    }
}
