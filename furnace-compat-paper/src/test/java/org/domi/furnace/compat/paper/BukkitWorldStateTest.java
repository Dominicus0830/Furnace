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

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        tp = server.join("Rhea");
        server.tick(3);
        var loc = tp.player().location();
        origin = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()) + 30, (int) Math.floor(loc.z()));
        server.loadChunk(origin.chunkX(), origin.chunkZ());
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
        world.setThundering(true);
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
    }

    @Test
    void dropItemPaperShape() {
        Location at = new Location(world, origin.x() + 0.5, origin.y(), origin.z() + 0.5);
        assertThrows(ThreadOwnershipException.class, () -> world.dropItem(at, new ItemStack(Material.DIAMOND, 3)));
        Item[] seenInConsumer = new Item[1];
        Item item = owner(() -> world.dropItem(at, new ItemStack(Material.DIAMOND, 3), i -> seenInConsumer[0] = i));
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
}
