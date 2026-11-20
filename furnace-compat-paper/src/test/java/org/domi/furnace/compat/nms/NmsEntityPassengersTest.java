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
    volatile boolean cancelSpawns;

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
                    if (cancelSpawns) e.setCancelled(true);
                });
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void reset() {
        spawnEvents.clear();
        cancelSpawns = false;
    }

    <T> T on(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    /** 좀비가 탄 돼지. */
    CraftEntity[] pigWithZombie() {
        return on(() -> {
            Location l = new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5);
            CraftEntity pig = (CraftEntity) world.spawnEntity(l, org.bukkit.entity.EntityType.PIG);
            CraftEntity zombie = (CraftEntity) world.spawnEntity(l, org.bukkit.entity.EntityType.ZOMBIE);
            assertTrue(pig.addPassenger(zombie));
            spawnEvents.clear();   // 원본을 만든 스폰은 세지 않는다
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
