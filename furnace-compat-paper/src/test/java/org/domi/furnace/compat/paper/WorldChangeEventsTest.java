package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.inventory.EquipmentSlot;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * O12 H1: 서버가 이미 내던 여섯 이벤트(BlockPlace·BlockGrow·BlockFade·BlockExplode·EntitySpawn·PlayerDropItem)가 Bukkit 처리기에 닿고,
 * CraftBukkit/Paper 와 같은 취소 규칙으로 서버에 돌아가는지 본다. 앞의 넷은 Furnace 이벤트를 직접 발행하고, 설치·스폰·버리기는 실제 서버 경로로 부른다.
 */
class WorldChangeEventsTest {

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
        for (Event e : RecorderPlugin.SEEN) if (type.isInstance(e)) out.add(type.cast(e));
        return out;
    }

    private static <E extends Event> E only(Class<E> type) {
        List<E> l = seen(type);
        assertEquals(1, l.size(), type.getSimpleName() + " 이 정확히 한 번 와야 한다: " + RecorderPlugin.SEEN);
        return l.get(0);
    }

    // ------------------------------------------------------------------ 블록 설치

    @Test
    void blockPlaceShowsPlacedStateReplacedStateItemAndHand() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            var p = server.join("Dana").player();
            server.tick(2);
            var bus = server.api().events();
            var pos = new BlockPos(1, 64, 2);
            var stairs = BlockData.parse("minecraft:oak_stairs[facing=north]");
            var item = ItemStack.of("minecraft:oak_stairs").withAmount(3);

            var ev = bus.call(new org.domi.furnace.api.event.block.BlockPlaceEvent(W, pos, stairs, BlockData.of("minecraft:grass_block"), new BlockPos(1, 63, 2), p, item, true));
            assertFalse(ev.isCancelled());
            var b = only(org.bukkit.event.block.BlockPlaceEvent.class);
            assertEquals(Material.OAK_STAIRS, b.getBlockPlaced().getType(), "CraftBukkit 은 놓은 뒤에 부르므로 getBlockPlaced 는 놓일 블록이다");
            assertTrue(b.getBlockPlaced().getBlockData().getAsString().contains("facing=north"), b.getBlockPlaced().getBlockData().getAsString());
            assertEquals(Material.GRASS_BLOCK, b.getBlockReplacedState().getType());
            assertEquals(Material.OAK_STAIRS, b.getBlockPlaced().getState().getType());
            assertEquals(63, b.getBlockAgainst().getY());
            assertEquals(Material.OAK_STAIRS, b.getItemInHand().getType());
            assertEquals(3, b.getItemInHand().getAmount());
            assertEquals(EquipmentSlot.HAND, b.getHand());
            assertEquals("Dana", b.getPlayer().getName());
            assertTrue(b.canBuild());

            // 왼손
            RecorderPlugin.SEEN.clear();
            bus.call(new org.domi.furnace.api.event.block.BlockPlaceEvent(W, pos, stairs, BlockData.AIR, new BlockPos(1, 63, 2), p, item, false));
            assertEquals(EquipmentSlot.OFF_HAND, only(org.bukkit.event.block.BlockPlaceEvent.class).getHand());

            // 취소와 setBuild(false) 는 모두 막는다(CraftEventFactory.callBlockPlaceEvent)
            RecorderPlugin.ACTIONS.put("BlockPlaceEvent", e -> ((org.bukkit.event.block.BlockPlaceEvent) e).setCancelled(true));
            assertTrue(bus.call(new org.domi.furnace.api.event.block.BlockPlaceEvent(W, pos, stairs, BlockData.AIR, new BlockPos(1, 63, 2), p, item, true)).isCancelled());
            RecorderPlugin.ACTIONS.put("BlockPlaceEvent", e -> ((org.bukkit.event.block.BlockPlaceEvent) e).setBuild(false));
            assertTrue(bus.call(new org.domi.furnace.api.event.block.BlockPlaceEvent(W, pos, stairs, BlockData.AIR, new BlockPos(1, 63, 2), p, item, true)).isCancelled(),
                    "canBuild 가 거짓이면 막는다");
            RecorderPlugin.ACTIONS.clear();
            assertFalse(bus.call(new org.domi.furnace.api.event.block.BlockPlaceEvent(W, pos, stairs, BlockData.AIR, new BlockPos(1, 63, 2), p, item, true)).isCancelled());
        }
    }

    @Test
    void blockPlaceThroughTheRealPlacementPath() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).serverProperty("spawn-protection", "0").pluginJar(recorderJar()).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(3);
            tp.sync();
            var sp = server.internals().playerByName("Dana");   // 접속 위치가 정해질 때까지(그전엔 y 가 0) 돌린다
            for (int w = 0; w < 100 && sp.y() == 0.0; w++) {
                server.tick(1);
                tp.sync();
            }
            server.tick(3);
            tp.moveTo(sp.x(), sp.y(), sp.z(), true);   // 클라이언트 위치 패킷 — 서버가 접속 직후의 위치 확인 대기를 끝낸다
            server.tick(3);
            tp.sync();
            int x = (int) Math.floor(sp.x()) + 2, y = (int) Math.floor(sp.y()), z = (int) Math.floor(sp.z());   // 플레이어 옆 바닥(y-1)의 윗면에 놓는다
            server.execute("give Dana minecraft:oak_planks 5");
            server.tick(2);
            var target = new BlockPos(x, y, z);

            // 처리기는 그 칸의 region 스레드에서 돌므로 블록을 바로 읽을 수 있다 — 거기서 값을 모은다
            List<String> info = new java.util.concurrent.CopyOnWriteArrayList<>();
            RecorderPlugin.ACTIONS.put("BlockPlaceEvent", e -> {
                var b = (org.bukkit.event.block.BlockPlaceEvent) e;
                info.add(b.getBlockPlaced().getType() + "|" + b.getBlockReplacedState().getType() + "|" + b.getBlockAgainst().getType().isAir() + "|" + b.getHand()
                        + "|" + b.getItemInHand().getAmount() + "|" + b.getBlockPlaced().getWorld().getBlockAt(x, y, z).getType());
                b.setCancelled(true);
            });
            useItemOn(tp, x, y - 1, z, 1, 1);
            server.tick(3);
            only(org.bukkit.event.block.BlockPlaceEvent.class);
            assertEquals(List.of("OAK_PLANKS|AIR|false|HAND|5|AIR"), info,
                    "이벤트의 놓일 블록·교체될 상태·맞닿은 바닥·손·손 아이템. 월드에서 그 칸을 다시 읽으면 아직 놓기 전이라 공기다(CraftBukkit 과 다른 점)");
            assertEquals("minecraft:air", server.onRegion(target, () -> server.overworld().block(target).data().type()), "취소하면 놓이지 않는다");
            assertEquals(5, server.execute("clear Dana minecraft:oak_planks 0").result(), "취소하면 아이템이 줄지 않는다");

            RecorderPlugin.reset();
            useItemOn(tp, x, y - 1, z, 1, 2);
            server.tick(3);
            only(org.bukkit.event.block.BlockPlaceEvent.class);
            assertEquals("minecraft:oak_planks", server.onRegion(target, () -> server.overworld().block(target).data().type()), "허용하면 놓인다");
            assertEquals(4, server.execute("clear Dana minecraft:oak_planks 0").result());
        }
    }

    private static void useItemOn(TestPlayer tp, int x, int y, int z, int face, int seq) {
        tp.sendPacket(0x38, b -> {
            Types.writeVarInt(b, 0);
            Types.writeBlockPos(b, x, y, z);
            Types.writeVarInt(b, face);
            b.writeFloat(0.5f);
            b.writeFloat(1.0f);
            b.writeFloat(0.5f);
            b.writeBoolean(false);
            Types.writeVarInt(b, seq);
        });
    }

    // ------------------------------------------------------------------ 자람·사라짐

    @Test
    void blockGrowAndFadeShowTheNewStateAndCancel() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            server.join("Dana");
            server.tick(2);
            var bus = server.api().events();
            var pos = new BlockPos(1, 64, 2);
            var grown = BlockData.parse("minecraft:wheat[age=7]");

            assertFalse(bus.call(new org.domi.furnace.api.event.block.BlockGrowEvent(W, pos, BlockData.parse("minecraft:wheat[age=6]"), grown)).isCancelled());
            var g = only(org.bukkit.event.block.BlockGrowEvent.class);
            assertEquals(Material.WHEAT, g.getNewState().getType());
            assertTrue(g.getNewState().getBlockData().getAsString().contains("age=7"), g.getNewState().getBlockData().getAsString());
            assertEquals(2, g.getBlock().getZ());
            RecorderPlugin.ACTIONS.put("BlockGrowEvent", e -> ((org.bukkit.event.block.BlockGrowEvent) e).setCancelled(true));
            assertTrue(bus.call(new org.domi.furnace.api.event.block.BlockGrowEvent(W, pos, BlockData.parse("minecraft:wheat[age=6]"), grown)).isCancelled());

            RecorderPlugin.reset();
            assertFalse(bus.call(new org.domi.furnace.api.event.block.BlockFadeEvent(W, pos, BlockData.of("minecraft:ice"), BlockData.of("minecraft:water"))).isCancelled());
            assertEquals(Material.WATER, only(org.bukkit.event.block.BlockFadeEvent.class).getNewState().getType());
            RecorderPlugin.ACTIONS.put("BlockFadeEvent", e -> ((org.bukkit.event.block.BlockFadeEvent) e).setCancelled(true));
            assertTrue(bus.call(new org.domi.furnace.api.event.block.BlockFadeEvent(W, pos, BlockData.of("minecraft:snow"), BlockData.AIR)).isCancelled());
        }
    }

    // ------------------------------------------------------------------ 폭발

    @Test
    void blockExplodeBlockListEditsAndCancelReachTheServer() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            server.join("Dana");
            server.tick(2);
            var bus = server.api().events();
            var center = new BlockPos(5, 64, 5);
            java.util.function.Supplier<org.domi.furnace.api.event.block.BlockExplodeEvent> fresh = () -> new org.domi.furnace.api.event.block.BlockExplodeEvent(W, center,
                    "minecraft:red_bed", new ArrayList<>(List.of(new BlockPos(5, 63, 5), new BlockPos(6, 64, 5), new BlockPos(7, 64, 5))),
                    org.domi.furnace.api.event.cause.CauseChain.NONE, 0.25F, "DESTROY_WITH_DECAY");

            assertFalse(bus.call(fresh.get()).isCancelled());
            var b = only(org.bukkit.event.block.BlockExplodeEvent.class);
            assertEquals(3, b.blockList().size());
            assertEquals(0.25F, b.getYield());
            assertEquals(org.bukkit.ExplosionResult.DESTROY_WITH_DECAY, b.getExplosionResult());
            assertEquals(Material.RED_BED, b.getExplodedBlockState().getType());
            assertEquals(5, b.getBlock().getX());

            // blockList 를 고치면 서버가 따른다: 하나 빼고 중심 칸을 넣는다
            RecorderPlugin.ACTIONS.put("BlockExplodeEvent", e -> {
                var x = (org.bukkit.event.block.BlockExplodeEvent) e;
                x.blockList().remove(0);
                x.blockList().add(x.getBlock());
            });
            var edited = bus.call(fresh.get());
            assertFalse(edited.isCancelled());
            assertEquals(List.of(new BlockPos(6, 64, 5), new BlockPos(7, 64, 5), center), edited.blocks());

            RecorderPlugin.ACTIONS.put("BlockExplodeEvent", e -> ((org.bukkit.event.block.BlockExplodeEvent) e).setCancelled(true));
            assertTrue(bus.call(fresh.get()).isCancelled());
        }
    }

    // ------------------------------------------------------------------ 스폰

    @Test
    void entitySpawnArrivesAsCreatureAndItemSpawnEventsAndCancels() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            var p = server.join("Dana").player();
            server.tick(2);
            var at = p.location();

            RecorderPlugin.SEEN.clear();
            var pig = server.await(server.overworld().spawnEntity("minecraft:pig", at));
            assertNotNull(pig);
            var creature = seen(org.bukkit.event.entity.CreatureSpawnEvent.class).stream().filter(e -> e.getEntity().getUniqueId().equals(pig.uniqueId())).toList();
            assertEquals(1, creature.size(), RecorderPlugin.SEEN.toString());
            assertEquals(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, creature.get(0).getSpawnReason());
            assertEquals(org.bukkit.entity.EntityType.PIG, creature.get(0).getEntityType());
            assertTrue(RecorderPlugin.SEEN.contains(creature.get(0)), "EntitySpawnEvent 처리기도 같은 이벤트를 받는다");

            // 취소하면 들어가지 않는다
            RecorderPlugin.ACTIONS.put("CreatureSpawnEvent", e -> ((org.bukkit.event.Cancellable) e).setCancelled(true));
            assertNull(server.await(server.overworld().spawnEntity("minecraft:pig", at)), "스폰 이벤트를 취소하면 spawnEntity 는 null");
            RecorderPlugin.reset();

            // 떨어진 아이템: ItemSpawnEvent, getEntity() 는 Item
            server.execute("summon minecraft:item " + at.x() + " " + at.y() + " " + at.z() + " {Item:{id:\"minecraft:stone\",count:3}}");
            var items = seen(org.bukkit.event.entity.ItemSpawnEvent.class);
            assertEquals(1, items.size(), RecorderPlugin.SEEN.toString());
            var item = items.get(0).getEntity();
            assertInstanceOf(org.bukkit.craftbukkit.entity.CraftItem.class, item);
            assertEquals(Material.STONE, item.getItemStack().getType());
            assertEquals(3, item.getItemStack().getAmount());
            assertEquals(org.bukkit.entity.EntityType.ITEM, items.get(0).getEntityType());
            assertEquals(0, item.getPickupDelay() < 0 ? 1 : 0);
            assertThrows(UnsupportedFeature.class, () -> item.setItemStack(new org.bukkit.inventory.ItemStack(Material.DIRT)));
            // 같은 UUID 는 같은 CraftEntity (캐시)
            assertEquals(item, org.domi.furnace.compat.paper.Compat.entity(items.get(0).getEntity() instanceof org.bukkit.craftbukkit.entity.CraftEntity c ? c.furnaceHandle() : null));
        }
    }

    // ------------------------------------------------------------------ 아이템 버리기

    @Test
    void playerDropItemShowsTheItemAndCancelReturnsIt() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(recorderJar()).start()) {
            TestPlayer tp = server.join("Dana");
            server.tick(2);
            server.execute("give Dana minecraft:stick 3");
            server.tick(2);

            RecorderPlugin.ACTIONS.put("PlayerDropItemEvent", e -> ((org.bukkit.event.Cancellable) e).setCancelled(true));
            dropOne(tp);
            server.tick(3);
            var denied = only(org.bukkit.event.player.PlayerDropItemEvent.class);
            assertEquals(Material.STICK, denied.getItemDrop().getItemStack().getType());
            assertEquals(1, denied.getItemDrop().getItemStack().getAmount());
            assertEquals(40, denied.getItemDrop().getPickupDelay(), "CraftBukkit 이 던지는 아이템의 줍기 지연");
            assertEquals("Dana", denied.getPlayer().getName());
            assertEquals(3, server.execute("clear Dana minecraft:stick 0").result(), "취소하면 인벤토리로 돌아온다");

            RecorderPlugin.reset();
            dropOne(tp);
            server.tick(3);
            only(org.bukkit.event.player.PlayerDropItemEvent.class);
            assertEquals(2, server.execute("clear Dana minecraft:stick 0").result(), "허용하면 하나가 줄어 던져진다");
        }
    }

    private static void dropOne(TestPlayer tp) {
        tp.sendPacket(0x24, b -> {
            Types.writeVarInt(b, 4);   // DROP_ITEM(Q)
            Types.writeBlockPos(b, 0, 0, 0);
            b.writeByte(0);
            Types.writeVarInt(b, 0);
        });
    }
}
