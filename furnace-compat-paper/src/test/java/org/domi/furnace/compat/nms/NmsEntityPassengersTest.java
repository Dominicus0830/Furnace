package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * NMS shim 5e-3: Axiom 의 엔티티 복사·놓기·지우기 길 — {@code Entity#saveAsPassenger} → {@code EntityType.loadEntityRecursive(tag, level, fn)}(fn 이 UUID·자리·머리
 * 방향을 정한다) → {@code ServerLevel#tryAddFreshEntityWithPassengers}, 그리고 {@code Entity#remove(reason)}. 기대값은 Paper 1.21.1 jar(javap)의 동작.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsEntityPassengersTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    CraftWorld world;
    BlockPos at;
    final List<String> spawnEvents = new CopyOnWriteArrayList<>();
    final List<String> mountEvents = new CopyOnWriteArrayList<>();
    volatile boolean cancelSpawns;
    volatile String cancelSpawnType;
    volatile boolean cancelMounts;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Rider");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()) + 3, (int) Math.floor(loc.y()) + 1, (int) Math.floor(loc.z()) + 3);
        world = (CraftWorld) Compat.player(tp.player()).getWorld();
        level = world.getHandle();
        server.api().events().subscribe(server.plugin(CompatTestPlugin.class), org.domi.furnace.api.event.entity.CreatureSpawnEvent.class,
                EventPriority.NORMAL, false, e -> {
                    spawnEvents.add(e.entity().type() + " " + e.reason());
                    if (cancelSpawns || e.entity().type().equals(cancelSpawnType)) e.setCancelled(true);
                });
        server.api().events().subscribe(server.plugin(CompatTestPlugin.class), org.domi.furnace.api.event.entity.EntityMountEvent.class,
                EventPriority.NORMAL, false, e -> {
                    mountEvents.add(e.entity().type() + " on " + e.mount().type());
                    if (cancelMounts) e.setCancelled(true);
                });
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void reset() {
        spawnEvents.clear();
        mountEvents.clear();
        cancelSpawns = false;
        cancelSpawnType = null;
        cancelMounts = false;
    }

    <T> T on(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    /** 좀비가 탄 돼지. */
    CraftEntity[] pigWithZombie() {
        return on(() -> {
            Location l = new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5);
            CraftEntity pig = (CraftEntity) world.spawnEntity(l, org.bukkit.entity.EntityType.PIG);
            CraftEntity zombie = (CraftEntity) world.spawnEntity(l, org.bukkit.entity.EntityType.ZOMBIE);
            assertTrue(pig.addPassenger(zombie));
            spawnEvents.clear();   // 원본을 만든 스폰·탑승은 세지 않는다
            mountEvents.clear();
            return new CraftEntity[] {pig, zombie};
        });
    }

    /** Axiom 의 처리 함수 모양: 뿌리는 정한 UUID, 나머지는 무작위, 모두 같은 자리·방향. */
    java.util.function.Function<Entity, Entity> axiomLike(UUID rootUuid) {
        java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);
        return e -> {
            e.setUUID(first.getAndSet(false) ? rootUuid : UUID.randomUUID());
            e.moveTo(at.x() + 0.5, at.y(), at.z() + 0.5, 90.0F, 0.0F);
            e.setYHeadRot(e.getYRot());
            return e;
        };
    }

    @Test
    void saveAsPassengerSavesEvenARider() {
        CraftEntity[] pz = pigWithZombie();
        on(() -> {
            CompoundTag rider = new CompoundTag();
            assertFalse(pz[1].getHandle().save(rider), "save 는 승객이면 거짓");
            assertTrue(pz[1].getHandle().saveAsPassenger(rider));
            assertEquals("minecraft:zombie", rider.getString("id"));
            CompoundTag vehicle = new CompoundTag();
            assertTrue(pz[0].getHandle().saveAsPassenger(vehicle));
            assertEquals("minecraft:pig", vehicle.getString("id"));
            assertEquals("minecraft:zombie", vehicle.getList("Passengers", Tag.TAG_COMPOUND).getCompound(0).getString("id"));
            pz[0].remove();
            pz[1].remove();
            return null;
        });
    }

    @Test
    void copyAndPlaceAVehicleWithItsRiderLikeAxiom() {
        CraftEntity[] pz = pigWithZombie();
        CompoundTag tag = new CompoundTag();
        on(() -> {
            assertTrue(pz[0].getHandle().saveAsPassenger(tag));
            return null;
        });
        UUID newRoot = UUID.randomUUID();
        Entity root = EntityType.loadEntityRecursive(tag, level, axiomLike(newRoot));
        assertNotNull(root);
        List<Entity> riders = java.util.stream.StreamSupport.stream(root.getIndirectPassengers().spliterator(), false).toList();
        assertEquals(1, riders.size());
        assertSame(root, riders.get(0).getVehicle(), "넣기 전에도 탈것을 안다");
        assertTrue(riders.get(0).isPassenger());
        assertFalse(root.isPassenger());
        assertTrue(on(() -> level.tryAddFreshEntityWithPassengers(root)));
        assertEquals(List.of("minecraft:pig DEFAULT", "minecraft:zombie DEFAULT"), spawnEvents, "Paper 기본 스폰 이유로 둘 다 이벤트");
        on(() -> {
            CraftEntity pig = root.getBukkitEntity();
            assertTrue(pig.isValid());
            assertEquals(newRoot, pig.getUniqueId());
            assertEquals(1, pig.getPassengers().size());
            assertSame(org.bukkit.entity.EntityType.ZOMBIE, pig.getPassengers().get(0).getType());
            assertEquals(90.0F, pig.getLocation().getYaw(), 0.001F);
            for (Entity p : root.getIndirectPassengers()) p.discard();
            root.discard();
            pz[0].remove();
            pz[1].remove();
            return null;
        });
    }

    @Test
    void placingAGroupFiresNoMountEventLikePaper() {
        // Paper: loadEntityRecursive 의 startRiding(vehicle, true) 는 탈것이 아직 월드 밖(valid 거짓)이라 탑승 이벤트를 부르지 않는다 — 리스너가 취소해도 승객이 빠지지 않는다
        CraftEntity[] pz = pigWithZombie();
        CompoundTag tag = new CompoundTag();
        on(() -> {
            assertTrue(pz[0].getHandle().saveAsPassenger(tag));
            return null;
        });
        cancelMounts = true;
        Entity root = EntityType.loadEntityRecursive(tag, level, axiomLike(UUID.randomUUID()));
        assertTrue(on(() -> level.tryAddFreshEntityWithPassengers(root)));
        assertEquals(List.of(), mountEvents, "탑승 이벤트 없음");
        assertEquals(List.of("minecraft:pig DEFAULT", "minecraft:zombie DEFAULT"), spawnEvents);
        on(() -> {
            CraftEntity pig = root.getBukkitEntity();
            assertEquals(1, pig.getPassengers().size(), "취소하는 리스너가 있어도 좀비는 그대로 탔다");
            for (Entity p : root.getIndirectPassengers()) p.discard();
            root.discard();
            pz[0].remove();
            pz[1].remove();
            return null;
        });
        // 이미 월드에 있는 엔티티끼리 태우면 이벤트가 난다(대조)
        CraftEntity[] again = on(() -> {
            Location l = new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5);
            CraftEntity pig = (CraftEntity) world.spawnEntity(l, org.bukkit.entity.EntityType.PIG);
            CraftEntity zombie = (CraftEntity) world.spawnEntity(l, org.bukkit.entity.EntityType.ZOMBIE);
            assertFalse(pig.addPassenger(zombie), "취소됐다");
            return new CraftEntity[] {pig, zombie};
        });
        assertEquals(List.of("minecraft:zombie on minecraft:pig"), mountEvents);
        on(() -> {
            again[0].remove();
            again[1].remove();
            return null;
        });
    }

    @Test
    void anExistingUuidRefusesTheWholeGroup() {
        CraftEntity[] pz = pigWithZombie();
        CompoundTag tag = new CompoundTag();
        on(() -> pz[0].getHandle().saveAsPassenger(tag));
        Entity copy = EntityType.loadEntityRecursive(tag, level, axiomLike(pz[1].getUniqueId()));   // 뿌리 UUID 가 살아 있는 좀비와 같다
        assertFalse(on(() -> level.tryAddFreshEntityWithPassengers(copy)));
        assertTrue(spawnEvents.isEmpty(), "아무것도 넣지 않았다");
        on(() -> {
            pz[0].remove();
            pz[1].remove();
            return null;
        });
    }

    @Test
    void aPassengerUuidInUseRefusesTheWholeGroupToo() {
        // Paper: 뿌리와 모든 승객의 UUID 중 하나라도 월드에 있으면 거짓, 아무것도 넣지 않는다
        CraftEntity[] pz = pigWithZombie();
        CompoundTag tag = new CompoundTag();
        on(() -> pz[0].getHandle().saveAsPassenger(tag));
        java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);
        Entity copy = EntityType.loadEntityRecursive(tag, level, e -> {
            e.setUUID(first.getAndSet(false) ? UUID.randomUUID() : pz[1].getUniqueId());   // 승객만 살아 있는 좀비의 UUID
            e.moveTo(at.x() + 0.5, at.y(), at.z() + 0.5, 0.0F, 0.0F);
            return e;
        });
        assertFalse(on(() -> level.tryAddFreshEntityWithPassengers(copy)));
        assertTrue(spawnEvents.isEmpty(), "아무것도 넣지 않았다");
        on(() -> {
            pz[0].remove();
            pz[1].remove();
            return null;
        });
    }

    @Test
    void aUuidHeldInAnotherRegionRefusesInsteadOfThrowing() {
        // 5e-3b 검토 권고 1: 겹치는 엔티티가 다른 region 소유여도 Paper 처럼 거짓(예외 아님)
        BlockPos far = new BlockPos(at.x() + 4000, at.y(), at.z());
        server.loadChunk(far.x() >> 4, far.z() >> 4);
        CraftEntity held = server.onRegion(far, () -> (CraftEntity) world.spawnEntity(new Location(world, far.x() + 0.5, far.y(), far.z() + 0.5),
                org.bukkit.entity.EntityType.PIG));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:pig");
        Entity copy = EntityType.loadEntityRecursive(tag, level, axiomLike(held.getUniqueId()));
        assertFalse(on(() -> level.tryAddFreshEntityWithPassengers(copy)));
        assertTrue(spawnEvents.stream().noneMatch(e -> e.endsWith("DEFAULT")), "아무것도 넣지 않았다: " + spawnEvents);
        server.onRegion(far, () -> {
            held.remove();
            return null;
        });
    }

    @Test
    void aUuidTakenAfterTheCheckIsReportedByTheServerAsFalse() {
        // 5e-3b 검토 권고 2: 사전 검사 뒤 서버가 UUID 겹침으로 거절하면 true 가 아니라 false. 사전 검사를 건너뛰고 브리지를 바로 부른다
        CraftEntity[] pz = pigWithZombie();
        var spec = new org.domi.furnace.api.internal.ChunkInternals.NmsEntitySpec("minecraft:zombie",
                List.of(new org.domi.furnace.api.internal.ChunkInternals.EntityOp.AbsMoveTo(at.x() + 0.5, at.y(), at.z() + 0.5, 0.0F, 0.0F),
                        new org.domi.furnace.api.internal.ChunkInternals.EntityOp.SetUuid(pz[1].getUniqueId())), List.of());
        var r = on(() -> org.domi.furnace.compat.nms.NmsChunks.bridge().addNmsEntityWithPassengersNow(world.furnaceHandle(), at.x(), at.z(), spec, null));
        assertEquals(org.domi.furnace.api.internal.ChunkInternals.GroupResult.UUID_TAKEN, r.result());
        assertTrue(r.added().stream().allMatch(java.util.Objects::isNull));
        on(() -> {
            pz[0].remove();
            pz[1].remove();
            return null;
        });
    }

    @Test
    void aPassengerSavedFarAwayIsPlacedWithItsVehicle() {
        // 5e-3b 검토 권고 3: 승객의 Pos 가 먼 곳이어도 바닐라는 첫 틱에 탈것이 제자리에 놓는다 — 묶음은 뿌리 region 에 들고 승객은 탈것 곁
        CraftEntity[] pz = pigWithZombie();
        CompoundTag tag = new CompoundTag();
        on(() -> pz[0].getHandle().saveAsPassenger(tag));
        on(() -> {
            pz[0].remove();
            pz[1].remove();
            return null;
        });
        CompoundTag rider = tag.getList("Passengers", Tag.TAG_COMPOUND).getCompound(0);
        var far = new net.minecraft.nbt.ListTag();
        far.add(net.minecraft.nbt.DoubleTag.valueOf(at.x() + 5000.5));
        far.add(net.minecraft.nbt.DoubleTag.valueOf(at.y()));
        far.add(net.minecraft.nbt.DoubleTag.valueOf(at.z() + 0.5));
        rider.put("Pos", far);
        java.util.concurrent.atomic.AtomicBoolean first = new java.util.concurrent.atomic.AtomicBoolean(true);
        Entity root = EntityType.loadEntityRecursive(tag, level, e -> {
            if (first.getAndSet(false)) e.moveTo(at.x() + 0.5, at.y(), at.z() + 0.5, 0.0F, 0.0F);   // 승객은 NBT 의 먼 자리 그대로
            e.setUUID(UUID.randomUUID());
            return e;
        });
        assertTrue(on(() -> level.tryAddFreshEntityWithPassengers(root)));
        on(() -> {
            for (Entity p : root.getIndirectPassengers()) {
                assertTrue(Math.abs(p.getX() - (at.x() + 0.5)) < 2.0, "승객 x=" + p.getX());
                assertNotNull(level.getEntity(p.getBukkitEntity().getUniqueId()), "승객이 월드에 들었다");
            }
            assertEquals(2, root.getSelfAndPassengers().count());
            for (Entity p : root.getSelfAndPassengers().toList()) p.getBukkitEntity().remove();
            return null;
        });
    }

    @Test
    void cancellingOnlyThePassengerSpawnDropsTheWholeGroup() {
        // user-todo 5e-3b ②: 승객 하나의 스폰이 취소돼도 묶음 전체를 넣지 않고 결과는 참(Paper 는 UUID 가 겹치지 않으면 참)
        CraftEntity[] pz = pigWithZombie();
        CompoundTag tag = new CompoundTag();
        on(() -> pz[0].getHandle().saveAsPassenger(tag));
        on(() -> {
            pz[0].remove();
            pz[1].remove();
            return null;
        });
        cancelSpawnType = "minecraft:zombie";
        Entity root = EntityType.loadEntityRecursive(tag, level, axiomLike(UUID.randomUUID()));
        assertTrue(on(() -> level.tryAddFreshEntityWithPassengers(root)));
        assertTrue(spawnEvents.contains("minecraft:zombie DEFAULT"), spawnEvents.toString());
        assertNull(on(() -> level.getEntity(root.furnaceKnownUuid())), "돼지도 들지 않았다");
        for (Entity p : root.getIndirectPassengers()) assertNull(on(() -> level.getEntity(p.furnaceKnownUuid())), "좀비도 들지 않았다");
    }

    @Test
    void cancelledSpawnStillReportsTrueButAddsNothing() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:pig");
        Entity pig = EntityType.loadEntityRecursive(tag, level, axiomLike(UUID.randomUUID()));
        cancelSpawns = true;
        assertTrue(on(() -> level.tryAddFreshEntityWithPassengers(pig)), "Paper 는 UUID 가 겹치지 않으면 참");
        assertEquals(List.of("minecraft:pig DEFAULT"), spawnEvents);
        assertNull(on(() -> level.getEntity(pig.furnaceKnownUuid())), "취소돼 들지 않았다");
    }

    @Test
    void unknownTypeLoadsNothing() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:nope");
        assertNull(EntityType.loadEntityRecursive(tag, level, e -> e));
        CompoundTag pig = new CompoundTag();
        pig.putString("id", "minecraft:pig");
        assertNull(EntityType.loadEntityRecursive(pig, level, e -> null), "처리 함수가 null 이면 null");
    }

    @Test
    void removeWithAReason() {
        CraftEntity[] pz = pigWithZombie();
        on(() -> {
            pz[1].getHandle().remove(Entity.RemovalReason.KILLED);
            assertTrue(pz[1].getHandle().isRemoved());
            assertFalse(pz[1].isValid());
            pz[0].getHandle().remove(Entity.RemovalReason.DISCARDED);
            assertFalse(pz[0].isValid());
            return null;
        });
    }
}
