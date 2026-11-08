// 옛 동기 작업(BukkitScheduler runTask·callSyncMethod)이 블록·엔티티를 Bukkit 의 주 스레드처럼 곧바로 읽고 쓴다 — 전역 스레드가 만진
// region 만 틱 사이에 빌려 온다(RegionBorrow). 같은 박동의 작업은 한 번 빌린 것을 같이 쓰고, 박동이 끝나면 돌려준다.
// 구간 밖(시험 스레드·Furnace 전역 작업)은 예전처럼 ThreadOwnershipException.
package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.server.world.RegionBorrow;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegacySyncRegionAccessTest {

    FurnaceTestServer server;
    CompatTestPlugin p;
    TestPlayer tp;
    World world;
    BlockPos origin;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().plugin(CompatTestPlugin.class).failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        p = server.plugin(CompatTestPlugin.class);
        tp = server.join("Mira");
        server.tick(3);
        var loc = tp.player().location();
        origin = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()) + 6, (int) Math.floor(loc.z()));
        server.loadChunk(origin.chunkX(), origin.chunkZ());
        world = Compat.world(server.overworld());
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(origin, task); }

    Block block(int dx, int dy, int dz) { return world.getBlockAt(origin.x() + dx, origin.y() + dy, origin.z() + dz); }

    @Test
    void syncTaskReadsAndWritesBlocksLikeTheMainThread() {
        Block b = block(1, 0, 0);
        List<Object> seen = new CopyOnWriteArrayList<>();
        p.legacy.runTask(p, () -> {
            try {
                seen.add(b.getType());
                b.setType(Material.GOLD_BLOCK);
                seen.add(b.getType());   // 쓴 값이 곧바로 읽힌다(예약이 아니다)
                b.setType(Material.OAK_STAIRS);
                seen.add(b.getBlockData().getAsString());
            } catch (Throwable t) {
                seen.add(t);
            }
        });
        server.tick();
        assertEquals(List.of(Material.AIR, Material.GOLD_BLOCK, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"), seen);
        assertEquals(Material.OAK_STAIRS, owner(b::getType), "region 스레드에서도 같은 값");
        // 같은 블록을 다음 박동에 다시 만져도 된다(빌린 region 은 박동이 끝나면 돌려줬다)
        AtomicReference<Object> again = new AtomicReference<>();
        p.legacy.runTaskLater(p, () -> {
            b.setType(Material.AIR);
            again.set(b.getType());
        }, 2);
        server.tick(2);
        assertEquals(Material.AIR, again.get());
    }

    @Test
    void tasksOfOneBeatShareOneBorrow_andTheRegionTicksAgainAfterwards() {
        Block a = block(2, 0, 0), c = block(3, 0, 0);
        long before = RegionBorrow.stats()[0];
        List<Object> seen = new CopyOnWriteArrayList<>();
        p.legacy.runTask(p, () -> a.setType(Material.STONE));
        p.legacy.runTask(p, () -> seen.add(a.getType()));
        p.legacy.runTask(p, () -> c.setType(Material.DIRT));
        long gameTime = server.gameTime();
        server.tick();
        assertEquals(List.of(Material.STONE), seen);
        assertEquals(before + 1, RegionBorrow.stats()[0], "한 박동의 작업 셋이 region 하나를 한 번만 빌린다");
        assertEquals(gameTime + 1, server.gameTime(), "빌린 region 도 그 틱의 게임 틱을 돈다");
        assertEquals(Material.DIRT, owner(c::getType));
        owner(() -> {
            a.setType(Material.AIR);
            c.setType(Material.AIR);
            return null;
        });
    }

    @Test
    void callSyncMethodAndEntitiesWork() throws Exception {
        Location at = new Location(world, origin.x() + 0.5, origin.y() + 1, origin.z() + 4.5);
        Future<Entity> spawned = p.legacy.callSyncMethod(p, () -> world.spawnEntity(at, EntityType.PIG));
        server.tick();
        Entity pig = spawned.get(5, TimeUnit.SECONDS);
        assertEquals(EntityType.PIG, pig.getType());
        server.tick();
        Future<Collection<Entity>> near = p.legacy.callSyncMethod(p, () -> world.getNearbyEntities(at, 3, 3, 3));
        server.tick();
        assertTrue(near.get(5, TimeUnit.SECONDS).contains(pig));
        var key = new org.bukkit.NamespacedKey("furnace", "legacy");
        Future<String> pdc = p.legacy.callSyncMethod(p, () -> {
            pig.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.STRING, "sync");
            return pig.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
        });
        server.tick();
        assertEquals("sync", pdc.get(5, TimeUnit.SECONDS), "엔티티 영속 데이터도 곧바로 읽고 쓴다");
        p.legacy.runTask(p, pig::remove);
        server.tick(2);
    }

    @Test
    void outsideLegacyCodeTheOwnershipRuleStays() {
        Block b = block(5, 0, 0);
        assertThrows(ThreadOwnershipException.class, b::getType, "시험 스레드");
        // Furnace 전역 작업(옛 코드 표시가 없는 곳)은 빌리지 않는다 — Furnace 플러그인 규칙 그대로
        RuntimeException e = assertThrows(RuntimeException.class, () -> server.onGlobal(b::getType));
        assertInstanceOf(ThreadOwnershipException.class, e instanceof ThreadOwnershipException ? e : e.getCause());
        // 비동기 작업도 마찬가지(Bukkit 에서도 비동기에서 월드를 만지면 안 된다)
        AtomicReference<Throwable> async = new AtomicReference<>();
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        p.legacy.runTaskAsynchronously(p, () -> {
            try {
                b.getType();
            } catch (Throwable t) {
                async.set(t);
            } finally {
                done.countDown();
            }
        });
        server.tick();
        try {
            assertTrue(done.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException ie) {
            throw new AssertionError(ie);
        }
        assertInstanceOf(ThreadOwnershipException.class, async.get());
    }
}
