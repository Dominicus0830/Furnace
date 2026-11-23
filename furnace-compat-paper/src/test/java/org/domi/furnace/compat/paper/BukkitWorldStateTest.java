package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.world.WorldClock;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.*;

/**
 * T-141 물결 1-B: World 시간·날씨·스폰·폴더·엔티티 목록·아이템 떨구기, Chunk#getEntities, Server#getCurrentTick.
 * 기대값은 Paper 1.21.1 CraftWorld 본문(setTime 은 앞으로만, setStorm 은 비 시간·맑음 붙잡기 0, dropItem 줍기 지연 10,
 * dropItemNaturally 는 축마다 0.25~0.75)에서 옮겼다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitWorldStateTest {

    FurnaceTestServer server;
    TestPlayer tp;
    Player player;
    World world;
    BlockPos origin;
    BlockPos far;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        tp = server.join("Rhea");
        server.tick(3);
        var loc = tp.player().location();
        origin = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()) + 30, (int) Math.floor(loc.z()));
        server.loadChunk(origin.chunkX(), origin.chunkZ());
        far = new BlockPos(origin.x() + 16 * 200, origin.y(), origin.z());   // 다른 region
        server.loadChunk(far.chunkX(), far.chunkZ());
        world = Compat.world(server.overworld());
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(origin, task); }

    @Test
    void timeFollowsPaperArithmetic() {
        world.setFullTime(30_000);
        assertEquals(30_000, world.getFullTime());
        assertEquals(6_000, world.getTime());
        // setTime 은 하루 안에서 앞으로만 간다: 6000 → 1000 이면 다음 날 1000
        world.setTime(1_000);
        assertEquals(49_000, world.getFullTime());
        assertEquals(1_000, world.getTime());
        world.setTime(13_000);
        assertEquals(61_000, world.getFullTime());
        assertEquals(server.internals().clock().gameTime(), world.getGameTime());
    }

    @Test
    void setFullTimeSendsTimeAtOnce() {
        tp.clearReceived();
        world.setFullTime(12_345);
        var pkt = tp.awaitPacket(PacketTypes.Clientbound.SET_TIME, p -> Math.abs(p.dayTime()) == 12_345);
        assertNotNull(pkt);
    }

    @Test
    void weatherSettersMatchPaper() {
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(500);
        assertEquals(500, world.getClearWeatherDuration());
        world.setStorm(true);
        assertTrue(world.hasStorm());
        assertEquals(0, world.getWeatherDuration(), "setStorm 은 비 시간을 0 으로");
        assertEquals(0, world.getClearWeatherDuration(), "setStorm 은 맑음 붙잡기를 0 으로");
        assertFalse(world.isClearWeather());
        world.setWeatherDuration(777);
        assertEquals(777, world.getWeatherDuration());
        world.setClearWeatherDuration(500);
        world.setThundering(true);
        assertEquals(0, world.getClearWeatherDuration(), "setThundering 도 맑음 붙잡기를 0 으로");
        assertTrue(world.isThundering());
        assertEquals(0, world.getThunderDuration());
        world.setThunderDuration(888);
        assertEquals(888, world.getThunderDuration());
        world.setStorm(false);
        world.setThundering(false);
        assertTrue(world.isClearWeather());
        // 남은 시간 0 이면 다음 틱 날씨 순환이 바닐라 구간에서 새로 뽑는다(맑음 → 비 지연 12000~180000)
        server.tick(1);
        int next = world.getWeatherDuration();
        assertTrue(next >= WorldClock.RAIN_DELAY[0] - 1 && next <= WorldClock.RAIN_DELAY[1], "rainTime " + next);
    }

    @Test
    void spawnLocation() {
        Location before = world.getSpawnLocation();
        assertSame(world, before.getWorld());
        assertEquals(Math.floor(before.getX()), before.getX(), "블록 좌표 그대로(가운데 아님)");
        tp.clearReceived();
        assertTrue(world.setSpawnLocation(origin.x(), origin.y(), origin.z(), 45.0F));
        Location now = world.getSpawnLocation();
        assertEquals(origin.x(), now.getX());
        assertEquals(origin.y(), now.getY());
        assertEquals(origin.z(), now.getZ());
        assertEquals(45.0F, now.getYaw());
        assertEquals(0.0F, now.getPitch());
        assertNotNull(tp.awaitPacket(PacketTypes.Clientbound.SET_DEFAULT_SPAWN_POSITION, p -> true));
        assertFalse(world.setSpawnLocation(new Location(null, 0, 64, 0)), "다른(없는) 월드의 위치는 false");
        assertTrue(world.setSpawnLocation(before));
        assertEquals(before.getBlockX(), world.getSpawnLocation().getBlockX());
    }

    @Test
    void worldFolderIsLevelRoot() {
        assertEquals(server.internals().world().worldDir().toAbsolutePath().normalize(), world.getWorldFolder().toPath().toAbsolutePath().normalize());
    }

    @Test
    void entitiesFromAnyThreadAndDropItem() {
        Pig pig = owner(() -> (Pig) world.spawnEntity(new Location(world, origin.x() + 0.5, origin.y(), origin.z() + 0.5), EntityType.PIG));
        try {
            List<Entity> all = world.getEntities();   // 시험 스레드(어느 region 도 아님)에서
            assertTrue(all.stream().anyMatch(e -> e.getUniqueId().equals(pig.getUniqueId())));
            assertTrue(all.stream().anyMatch(e -> e.getUniqueId().equals(player.getUniqueId())), "플레이어도 든다");
            List<LivingEntity> living = world.getLivingEntities();
            assertTrue(living.stream().anyMatch(e -> e.getUniqueId().equals(pig.getUniqueId())));
            assertTrue(world.getEntitiesByClass(Pig.class).stream().anyMatch(e -> e.getUniqueId().equals(pig.getUniqueId())));
            assertTrue(world.getEntitiesByClass(Item.class).stream().noneMatch(e -> e.getUniqueId().equals(pig.getUniqueId())));
            assertTrue(world.getEntitiesByClasses(Item.class, Pig.class).stream().anyMatch(e -> e.getUniqueId().equals(pig.getUniqueId())));

            Chunk chunk = world.getChunkAt(origin.chunkX(), origin.chunkZ());
            assertThrows(ThreadOwnershipException.class, chunk::getEntities, "청크 안 엔티티는 소유 스레드에서만");
            Entity[] inChunk = owner(chunk::getEntities);
            assertTrue(Arrays.stream(inChunk).anyMatch(e -> e.getUniqueId().equals(pig.getUniqueId())));
            Entity[] other = owner(() -> world.getChunkAt(origin.chunkX() + 1, origin.chunkZ()).getEntities());
            assertTrue(Arrays.stream(other).noneMatch(e -> e.getUniqueId().equals(pig.getUniqueId())));
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
        assertTrue(world.getEntities().stream().noneMatch(e -> e.getUniqueId().equals(pig.getUniqueId())), "지운 엔티티는 빠진다");
    }

    @Test
    void chunkEntitiesStayInsideChunk() {
        // 이웃 청크 경계 바로 안쪽(0.3 블록)의 돼지: 상자는 겹치지만 이 청크 것이 아니다
        int edgeX = (origin.chunkX() << 4) + 16;
        Pig pig = owner(() -> (Pig) world.spawnEntity(new Location(world, edgeX + 0.3, origin.y(), origin.z() + 0.5), EntityType.PIG));
        try {
            Entity[] here = owner(() -> world.getChunkAt(origin.chunkX(), origin.chunkZ()).getEntities());
            assertTrue(Arrays.stream(here).noneMatch(e -> e.getUniqueId().equals(pig.getUniqueId())));
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
    }

    @Test
    void chunkEntitiesNeedThatChunksRegion() {
        Pig pig = server.onRegion(far, () -> (Pig) world.spawnEntity(new Location(world, far.x() + 0.5, far.y(), far.z() + 0.5), EntityType.PIG));
        try {
            Chunk farChunk = world.getChunkAt(far.chunkX(), far.chunkZ());
            Entity[] onOwner = server.onRegion(far, farChunk::getEntities);
            assertTrue(Arrays.stream(onOwner).anyMatch(e -> e.getUniqueId().equals(pig.getUniqueId())));
            assertThrows(ThreadOwnershipException.class, () -> owner(farChunk::getEntities), "다른 region 스레드면 빈 배열이 아니라 예외");
        } finally {
            server.onRegion(far, () -> { pig.remove(); return null; });
        }
    }

    @Test
    void dyingMobsAreNotListed() {
        Pig pig = owner(() -> (Pig) world.spawnEntity(new Location(world, origin.x() + 0.5, origin.y(), origin.z() + 0.5), EntityType.PIG));
        try {
            owner(() -> { pig.setHealth(0); return null; });
            assertFalse(owner(pig::isValid));
            assertTrue(world.getEntities().stream().noneMatch(e -> e.getUniqueId().equals(pig.getUniqueId())), "Paper 는 isValid 로 거른다");
            assertTrue(world.getLivingEntities().stream().noneMatch(e -> e.getUniqueId().equals(pig.getUniqueId())));
            Entity[] inChunk = owner(() -> world.getChunkAt(origin.chunkX(), origin.chunkZ()).getEntities());
            assertTrue(Arrays.stream(inChunk).noneMatch(e -> e.getUniqueId().equals(pig.getUniqueId())));
        } finally {
            owner(() -> { pig.remove(); return null; });
        }
    }

    @Test
    void dropItemPaperShape() {
        Location at = new Location(world, origin.x() + 0.5, origin.y(), origin.z() + 0.5);
        assertThrows(ThreadOwnershipException.class, () -> world.dropItem(at, new ItemStack(Material.DIAMOND, 3)));
        Item[] seenInConsumer = new Item[1];
        boolean[] listedInConsumer = new boolean[1];
        Item item = owner(() -> world.dropItem(at, new ItemStack(Material.DIAMOND, 3), i -> {
            seenInConsumer[0] = i;
            listedInConsumer[0] = world.getEntities().stream().anyMatch(e -> e.getUniqueId().equals(i.getUniqueId()));
        }));
        assertFalse(listedInConsumer[0], "consumer 는 월드에 넣기 전에 돈다");
        assertTrue(world.getEntities().stream().anyMatch(e -> e.getUniqueId().equals(item.getUniqueId())));
        assertNotNull(item);
        assertSame(item, seenInConsumer[0], "넣기 전 consumer 가 같은 Item 을 받는다");
        assertEquals(Material.DIAMOND, owner(item::getItemStack).getType());
        assertEquals(3, owner(item::getItemStack).getAmount());
        assertEquals(10, owner(item::getPickupDelay), "Paper dropItem: 줍기 지연 10");
        Location l = owner(item::getLocation);
        assertEquals(at.getX(), l.getX(), 1e-9);
        assertEquals(at.getY(), l.getY(), 1e-9);

        for (int n = 0; n < 20; n++) {
            Item nat = owner(() -> world.dropItemNaturally(at, new ItemStack(Material.STICK)));
            Location p = owner(nat::getLocation);
            for (double d : new double[] {p.getX() - at.getX(), p.getY() - at.getY(), p.getZ() - at.getZ()})
                assertTrue(d >= 0.25 && d <= 0.75, "naturally 오프셋 " + d);
            owner(() -> { nat.remove(); return null; });
        }
        owner(() -> { item.remove(); return null; });
        assertThrows(IllegalArgumentException.class, () -> owner(() -> world.dropItem(null, new ItemStack(Material.STICK))));
        assertThrows(IllegalArgumentException.class, () -> owner(() -> world.dropItem(at, null)));
    }

    @Test
    void currentTickAdvances() {
        int a = Bukkit.getCurrentTick();
        server.tick(3);
        int b = Bukkit.getServer().getCurrentTick();
        assertTrue(b >= a + 3, a + " → " + b);
        assertEquals(server.internals().serverTickCount(), (long) Bukkit.getCurrentTick());
    }

    /** T-264 P2c-2: CraftWorld 는 Paper 처럼 CraftRegionAccessor 아래 — RegionAccessor 의 칸 읽기·쓰기·높이 지도가 월드에서 돈다. */
    @Test
    void regionAccessorMembersOnWorld() {
        assertEquals("org.bukkit.craftbukkit.CraftRegionAccessor", world.getClass().getSuperclass().getName());
        org.bukkit.RegionAccessor ra = (org.bukkit.RegionAccessor) world;
        int x = origin.x(), y = origin.y(), z = origin.z();
        try {
            owner(() -> { ra.setBlockData(x, y, z, Material.STONE.createBlockData()); return null; });
            assertEquals(Material.STONE, owner(() -> ra.getType(x, y, z)));
            assertEquals(Material.STONE, owner(() -> ra.getBlockData(x, y, z).getMaterial()));
            assertEquals(Material.STONE, owner(() -> world.getBlockAt(x, y, z).getType()), "같은 칸을 CraftBlock 으로 봐도 같다");
            // 하늘이 트인 칸 위에 놓은 돌: 동작 막는 높이 지도의 맨 위 블록이다(Paper: 첫 빈 칸 - 1)
            assertEquals(y, owner(() -> ra.getHighestBlockYAt(x, z)));
            assertEquals(y, owner(() -> ra.getHighestBlockYAt(x, z, org.bukkit.HeightMap.WORLD_SURFACE)));
            // 생성 단계 지도: 바닐라는 그 자리에서 지금 블록으로 만들어 답한다 — 술어가 같은 살아 있는 지도와 같은 값(바닐라는 그 뒤 갱신하지 않아 낡을 수 있다)
            assertEquals(y, owner(() -> ra.getHighestBlockYAt(x, z, org.bukkit.HeightMap.WORLD_SURFACE_WG)));
            assertEquals(owner(() -> ra.getHighestBlockYAt(x, z, org.bukkit.HeightMap.OCEAN_FLOOR)),
                    owner(() -> ra.getHighestBlockYAt(x, z, org.bukkit.HeightMap.OCEAN_FLOOR_WG)));
            assertEquals(y, owner(() -> ra.getHighestBlockYAt(x, z, org.bukkit.HeightMap.OCEAN_FLOOR_WG)), "돌은 움직임을 막는 재질");
            int ux = origin.x() + 16 * 400;
            assertThrows(UnsupportedFeature.class, () -> owner(() -> ra.getHighestBlockYAt(ux, z)), "로드 안 된 청크는 동기 로드하지 않는다");
        } finally {
            owner(() -> { ra.setType(x, y, z, Material.AIR); return null; });
        }
    }
}
