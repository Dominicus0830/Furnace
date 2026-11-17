package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.server.plugin.EntityHandle;
import org.domi.furnace.server.plugin.PluginHost;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-040b: Furnace EntityChangeBlockEvent(와 하위 EntityBreakDoorEvent)가 Bukkit 처리기에 닿고 취소가 오간다. 플레이어가 아닌 쏜 쪽(getShooter)·
 * Bukkit.getEntity(UUID) 가 약한 캐시가 아니라 월드의 UUID 색인으로 정해진다.
 */
class EntityChangeBlockBridgeTest {

    private static final String W = "minecraft:overworld";

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { RecorderPlugin.reset(); }

    private Path recorderJar() throws Exception {
        return Jars.bukkit(tmp.resolve("recorder.jar"), """
                name: Recorder
                version: '1'
                main: org.domi.furnace.compat.paper.fixtures.RecorderPlugin
                api-version: '1.21'
                """, Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
    }

    private static <E extends Event> List<E> seen(Class<E> type) {
        List<E> out = new ArrayList<>();
        for (Event e : RecorderPlugin.SEEN) if (e.getClass() == type) out.add(type.cast(e));
        return out;
    }

    private static <E extends Event> E only(Class<E> type) {
        List<E> l = seen(type);
        assertEquals(1, l.size(), type.getSimpleName() + " 이 정확히 한 번 와야 한다: " + RecorderPlugin.SEEN);
        return l.get(0);
    }

    @Test
    void changeBlockReachesBukkitAndCancelGoesBack() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            var dana = server.join("Dana").player();
            server.tick(2);
            var bus = server.api().events();
            var pos = new BlockPos(3, 64, 3);
            server.execute("setblock 3 64 3 minecraft:grass_block");
            server.tick(2);
            var sheep = server.await(server.overworld().spawnEntity("minecraft:sheep", dana.location()));

            var ev = server.onRegion(pos, () -> bus.call(new org.domi.furnace.api.event.entity.EntityChangeBlockEvent(sheep, W, pos,
                    BlockData.of("minecraft:grass_block"), BlockData.of("minecraft:dirt"))));
            assertFalse(ev.isCancelled());
            var b = only(org.bukkit.event.entity.EntityChangeBlockEvent.class);
            assertEquals(org.bukkit.entity.EntityType.SHEEP, b.getEntityType());
            assertEquals(Material.DIRT, b.getTo());
            var data = b.getBlockData();
            assertEquals(Material.DIRT, data.getMaterial());
            assertNotSame(data, b.getBlockData(), "getBlockData 는 사본을 준다(CraftBukkit 과 같다)");
            assertEquals(3, b.getBlock().getX());
            assertEquals(64, b.getBlock().getY());
            assertEquals(Material.GRASS_BLOCK, server.onRegion(pos, () -> b.getBlock().getType()), "getBlock 은 바뀌기 전 칸");
            assertFalse(b.isCancelled());

            // 취소 → 서버도 취소
            RecorderPlugin.reset();
            RecorderPlugin.ACTIONS.put("EntityChangeBlockEvent", e -> ((org.bukkit.event.entity.EntityChangeBlockEvent) e).setCancelled(true));
            assertTrue(server.onRegion(pos, () -> bus.call(new org.domi.furnace.api.event.entity.EntityChangeBlockEvent(sheep, W, pos,
                    BlockData.of("minecraft:grass_block"), BlockData.of("minecraft:dirt")))).isCancelled());

            // 게임 규칙이 막아 취소된 채 온 것(Paper 의 mobGriefing 자리) → Bukkit 에도 취소로 보이고, 처리기가 풀면 서버도 푼다
            RecorderPlugin.reset();
            List<Boolean> sawCancelled = new ArrayList<>();
            RecorderPlugin.ACTIONS.put("EntityChangeBlockEvent", e -> {
                var c = (org.bukkit.event.entity.EntityChangeBlockEvent) e;
                sawCancelled.add(c.isCancelled());
                c.setCancelled(false);
            });
            var pre = server.onRegion(pos, () -> bus.call(new org.domi.furnace.api.event.entity.EntityChangeBlockEvent(sheep, W, pos,
                    BlockData.of("minecraft:grass_block"), BlockData.of("minecraft:dirt"), true)));
            assertEquals(List.of(true), sawCancelled);
            assertFalse(pre.isCancelled());
            assertTrue(seen(org.bukkit.event.entity.EntityBreakDoorEvent.class).isEmpty());
        }
    }

    @Test
    void breakDoorIsSubclassAndReachesChangeBlockHandlers() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            var dana = server.join("Dana").player();
            server.tick(2);
            var bus = server.api().events();
            var pos = new BlockPos(4, 64, 4);
            var zombie = server.await(server.overworld().spawnEntity("minecraft:zombie", dana.location()));
            assertInstanceOf(org.domi.furnace.api.entity.LivingEntity.class, zombie);

            var ev = server.onRegion(pos, () -> bus.call(new org.domi.furnace.api.event.entity.EntityBreakDoorEvent(
                    (org.domi.furnace.api.entity.LivingEntity) zombie, W, pos, BlockData.of("minecraft:oak_door"), BlockData.AIR)));
            assertFalse(ev.isCancelled());
            var door = only(org.bukkit.event.entity.EntityBreakDoorEvent.class);
            assertTrue(seen(org.bukkit.event.entity.EntityChangeBlockEvent.class).isEmpty(), "한 번만 — ECB 처리기 목록으로 하위 클래스가 온다");
            assertInstanceOf(org.bukkit.entity.LivingEntity.class, door.getEntity());
            assertEquals(org.bukkit.entity.EntityType.ZOMBIE, door.getEntity().getType());
            assertEquals(Material.AIR, door.getTo());
            assertSame(org.bukkit.event.entity.EntityChangeBlockEvent.getHandlerList(), door.getHandlers(), "Bukkit 처럼 처리기 목록을 같이 쓴다");

            RecorderPlugin.ACTIONS.put("EntityBreakDoorEvent", e -> ((org.bukkit.event.Cancellable) e).setCancelled(true));
            assertTrue(server.onRegion(pos, () -> bus.call(new org.domi.furnace.api.event.entity.EntityBreakDoorEvent(
                    (org.domi.furnace.api.entity.LivingEntity) zombie, W, pos, BlockData.of("minecraft:oak_door"), BlockData.AIR))).isCancelled());
        }
    }

    @Test
    void nonPlayerShooterAndGetEntityResolveByUuid() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            server.join("Dana");
            server.tick(2);
            var host = (PluginHost) server.api();
            // 해골이 쏜 화살(찾기는 약한 캐시가 아니라 월드 UUID 색인 — 캐시에 있든 없든 같은 답)
            Object[] made = server.internals().world().submitAtChunk(new ChunkPos(0, 0), r -> {
                var level = r.level();
                var skeleton = new org.domi.furnace.server.entity.Skeleton(level, level.nextEntityId());
                skeleton.moveTo(2.5, 65, 2.5, 0, 0);
                level.addFreshEntity(skeleton);
                var arrow = new org.domi.furnace.server.entity.Arrow(level, level.nextEntityId());
                arrow.moveTo(3.5, 66, 2.5, 0, 0);
                arrow.setOwner(skeleton);
                level.addFreshEntity(arrow);
                return new Object[] {skeleton.uuid(), EntityHandle.of(host, arrow)};
            }).get(30, TimeUnit.SECONDS);
            UUID skeletonId = (UUID) made[0];
            var arrowHandle = (org.domi.furnace.api.entity.Entity) made[1];

            var projectile = (org.bukkit.entity.Projectile) Compat.entity(arrowHandle);
            assertEquals(skeletonId, projectile.getOwnerUniqueId());
            // 시험 스레드(틱 스레드 아님) — 소유 region 의 답을 기다린다
            var shooter = projectile.getShooter();
            assertInstanceOf(org.bukkit.entity.LivingEntity.class, shooter);
            assertEquals(org.bukkit.entity.EntityType.SKELETON, ((org.bukkit.entity.Entity) shooter).getType());
            assertEquals(skeletonId, ((org.bukkit.entity.Entity) shooter).getUniqueId());
            // 그 region 스레드 안에서도 같은 답
            var onRegion = server.onRegion(new BlockPos(2, 65, 2), projectile::getShooter);
            assertEquals(org.bukkit.entity.EntityType.SKELETON, ((org.bukkit.entity.Entity) onRegion).getType());

            // Bukkit.getEntity / World.getEntity
            assertEquals(skeletonId, Bukkit.getEntity(skeletonId).getUniqueId());
            var world = Bukkit.getWorld("world");
            assertNotNull(world);
            assertEquals(skeletonId, world.getEntity(skeletonId).getUniqueId());
            assertNull(Bukkit.getEntity(UUID.randomUUID()));
            assertThrows(IllegalArgumentException.class, () -> world.getEntity(null));
            assertEquals(server.players().get(0).player().uniqueId(), Bukkit.getEntity(server.players().get(0).player().uniqueId()).getUniqueId(),
                    "플레이어도 찾는다");

            // 해골이 사라지면 null
            server.internals().world().submitAtChunk(new ChunkPos(0, 0), r -> {
                var e = r.level().getTargetableByUuid(skeletonId);
                ((org.domi.furnace.server.entity.Entity) e).discard();
                return null;
            }).get(30, TimeUnit.SECONDS);
            server.tick(2);
            assertNull(projectile.getShooter());
            assertNull(Bukkit.getEntity(skeletonId));
        }
    }
}
