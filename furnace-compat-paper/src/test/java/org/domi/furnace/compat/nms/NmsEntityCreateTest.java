package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
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
 * T-212 1-나: FAWE 의 엔티티 붙여 넣기 길({@code PaperweightGetBlocks} 동기 작업 — {@code EntityType.byString(id).create(level)} → {@code load(tag)} →
 * {@code absMoveTo} → {@code setUUID} → 이벤트를 켰으면 {@code addFreshEntity(e, CUSTOM)}, 껐으면 {@code spawnReason}·{@code generation} 을 정하고
 * {@code moonrise$getEntityLookup().addNewEntity(e)}), 그리고 WorldEdit {@code createEntity} 의 {@code addFreshEntityWithPassengers}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsEntityCreateTest {

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
        tp = server.join("Paste");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()) + 2, (int) Math.floor(loc.y()) + 1, (int) Math.floor(loc.z()) + 2);
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

    /** 복사할 원본: 이름 붙인 돼지의 저장 NBT(FAWE 는 붙여 넣기 전에 UUID 키를 지운다 — Constants.NO_COPY_ENTITY_NBT_FIELDS). */
    CompoundTag copiedPig() {
        CraftEntity pig = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 0.5), org.bukkit.entity.EntityType.PIG));
        CompoundTag tag = new CompoundTag();
        on(() -> {
            pig.setCustomName("Copied");
            pig.getHandle().save(tag);
            pig.remove();
            return null;
        });
        tag.remove("UUID");
        return tag;
    }

    /** 서버의 그 엔티티(주인 스레드에서). */
    org.domi.furnace.server.entity.Entity serverEntity(UUID id) {
        return on(() -> {
            var b = Bukkit.getEntity(id);
            return b == null ? null : org.domi.furnace.api.internal.Internals.get().entityNow(((CraftEntity) b).furnaceHandle(), org.domi.furnace.server.entity.Entity.class);
        });
    }

    @Test
    void createGivesADetachedEntityOfTheRightShape() {
        Entity pig = EntityType.byString("minecraft:pig").orElseThrow().create(level);
        assertInstanceOf(LivingEntity.class, pig, "살아 있는 종류는 CraftLivingEntity#getHandle 과 같은 종류");
        assertFalse(EntityType.byString("minecraft:item").orElseThrow().create(level) instanceof LivingEntity);
        assertNull(EntityType.byString("minecraft:player").orElseThrow().create(level), "바닐라 공장이 null");
        assertNull(EntityType.byString("minecraft:fishing_bobber").orElseThrow().create(level));
        assertEquals(0.0, pig.getX());
        assertSame(level, pig.level());
        assertFalse(pig.isRemoved());
        assertThrows(UnsupportedNmsCall.class, pig::getBukkitEntity);
        assertThrows(UnsupportedNmsCall.class, pig::getUUID, "UUID 는 넣을 때 정해진다");
        assertThrows(UnsupportedNmsCall.class, () -> pig.save(new CompoundTag()));
    }

    @Test
    void fawePasteWithoutEventsAddsTheEntityQuietly() {
        CompoundTag tag = copiedPig();
        spawnEvents.clear();   // 원본 돼지를 Bukkit 으로 낳은 이벤트
        UUID id = UUID.randomUUID();
        double x = at.x() + 3.5, y = at.y(), z = at.z() + 0.5;
        Entity e = on(() -> {
            Entity n = EntityType.byString("minecraft:pig").orElseThrow().create(level);
            n.load(tag);
            n.absMoveTo(x, y, z, 90.0F, 10.0F);
            n.setUUID(id);
            n.spawnReason = SpawnReason.CUSTOM;
            n.generation = false;
            assertTrue(level.moonrise$getEntityLookup().addNewEntity(n));
            return n;
        });
        assertTrue(spawnEvents.isEmpty(), "addNewEntity 는 이벤트가 없다: " + spawnEvents);
        assertEquals(id, e.getUUID());
        CraftEntity bukkit = e.getBukkitEntity();
        assertEquals(id, bukkit.getUniqueId());
        assertSame(e, on(bukkit::getHandle), "넣은 shim 이 그 엔티티의 getHandle 이다");
        var s = serverEntity(id);
        assertNotNull(s);
        assertEquals(x, s.x());
        assertEquals(z, s.z());
        assertEquals(90.0F, s.yRot());
        assertEquals("CUSTOM", s.spawnReason());
        assertEquals("Copied", on(bukkit::getCustomName));
        // 같은 UUID 를 또 넣으면 거짓
        Entity dup = EntityType.byString("minecraft:pig").orElseThrow().create(level);
        dup.setUUID(id);
        dup.absMoveTo(x, y, z, 0, 0);
        assertFalse(on(() -> level.moonrise$getEntityLookup().addNewEntity(dup)));
        assertFalse(on(() -> level.moonrise$getEntityLookup().addNewEntity(e)), "이미 월드에 있다");
        on(() -> { bukkit.remove(); return null; });
    }

    @Test
    void addFreshEntityFiresTheSpawnEventAndHonoursCancel() {
        CompoundTag tag = copiedPig();
        spawnEvents.clear();
        Entity e = EntityType.byString("minecraft:pig").orElseThrow().create(level);
        e.load(tag);
        e.absMoveTo(at.x() + 0.5, at.y(), at.z() + 4.5, 0, 0);
        assertTrue(on(() -> level.addFreshEntity(e, SpawnReason.CUSTOM)));
        assertEquals(List.of("minecraft:pig CUSTOM"), spawnEvents);
        assertEquals(SpawnReason.CUSTOM, e.spawnReason);
        assertTrue(on(() -> level.addFreshEntity(e, SpawnReason.CUSTOM)), "Paper: 두 번 넣기는 오류 기록만 남기고 참");
        on(() -> { e.getBukkitEntity().remove(); return null; });

        cancelSpawns = true;
        Entity c = EntityType.byString("minecraft:pig").orElseThrow().create(level);
        c.absMoveTo(at.x() + 0.5, at.y(), at.z() + 5.5, 0, 0);
        assertFalse(on(() -> level.addFreshEntity(c, SpawnReason.CUSTOM)), "이벤트 취소");
        assertThrows(UnsupportedNmsCall.class, c::getBukkitEntity, "넣지 못했으니 아직 월드에 없다");
        assertTrue(c.isRemoved(), "Paper doEntityAddEventCalling: 취소된 엔티티는 discard");
        cancelSpawns = false;
        assertFalse(on(() -> level.addFreshEntity(c, SpawnReason.CUSTOM)), "지운 엔티티는 다시 넣지 못한다");
        assertEquals(List.of("minecraft:pig CUSTOM", "minecraft:pig CUSTOM"), spawnEvents, "다시 넣기는 이벤트를 내지 않는다");
    }

    @Test
    void sameUuidIsRefusedWithoutDiscarding() {
        CraftEntity cow = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 10.5), org.bukkit.entity.EntityType.COW));
        spawnEvents.clear();
        Entity e = EntityType.byString("minecraft:cow").orElseThrow().create(level);
        e.absMoveTo(at.x() + 1.5, at.y(), at.z() + 10.5, 0, 0);
        e.setUUID(cow.getUniqueId());
        assertFalse(on(() -> level.addFreshEntity(e, SpawnReason.CUSTOM)), "같은 UUID");
        assertFalse(e.isRemoved(), "UUID 겹침은 Paper 도 지우지 않는다");
        on(() -> { cow.remove(); return null; });
    }

    @Test
    void workerThreadAddsThroughTheOwner() throws Exception {
        CompoundTag tag = copiedPig();
        spawnEvents.clear();
        // FAWE 작업 스레드(틱 스레드가 아님): 만들기·기록은 그 스레드에서, 넣기는 주인 region 에 보내 기다린다
        Entity withEvents = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            Entity n = EntityType.byString("minecraft:pig").orElseThrow().create(level);
            n.load(tag);
            n.absMoveTo(at.x() + 2.5, at.y(), at.z() + 11.5, 45.0F, 0.0F);
            assertTrue(level.addFreshEntity(n, SpawnReason.CUSTOM));
            return n;
        }).get(20, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(List.of("minecraft:pig CUSTOM"), spawnEvents);
        UUID quietId = UUID.randomUUID();
        Entity quiet = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            Entity n = EntityType.byString("minecraft:pig").orElseThrow().create(level);
            n.load(tag);
            n.absMoveTo(at.x() + 3.5, at.y(), at.z() + 11.5, 0.0F, 0.0F);
            n.setUUID(quietId);
            assertTrue(level.moonrise$getEntityLookup().addNewEntity(n));
            return n;
        }).get(20, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(1, spawnEvents.size(), "addNewEntity 는 이벤트가 없다");
        for (Entity e : List.of(withEvents, quiet)) {
            CraftEntity b = e.getBukkitEntity();
            assertSame(e, on(b::getHandle), "작업 스레드에서 넣은 shim 도 그 엔티티의 getHandle");
            assertEquals("Copied", on(b::getCustomName));
        }
        assertEquals(45.0F, serverEntity(withEvents.getUUID()).yRot());
        assertEquals(quietId, quiet.getUUID());
        on(() -> { withEvents.getBukkitEntity().remove(); quiet.getBukkitEntity().remove(); return null; });
    }

    @Test
    void globalThreadAddsInsideABorrow() {
        // FAWE 동기 작업: 전역 스레드가 region 을 빌린 구간에서 넣는다
        Entity e = EntityType.byString("minecraft:sheep").orElseThrow().create(level);
        e.absMoveTo(at.x() + 4.5, at.y(), at.z() + 11.5, 0, 0);
        assertTrue(server.onGlobal(() -> {
            try (var a = server.api().scheduler().global().openRegionAccess()) {
                return level.addFreshEntity(e, SpawnReason.CUSTOM);
            }
        }));
        assertEquals(List.of("minecraft:sheep CUSTOM"), spawnEvents);
        assertNotNull(serverEntity(e.getUUID()));
        on(() -> { e.getBukkitEntity().remove(); return null; });
    }

    @Test
    void discardedBeforeAddingIsNotAdded() {
        Entity e = EntityType.byString("minecraft:cow").orElseThrow().create(level);
        e.absMoveTo(at.x() + 0.5, at.y(), at.z() + 6.5, 0, 0);
        e.discard();
        assertTrue(e.isRemoved());
        assertFalse(on(() -> level.addFreshEntity(e, SpawnReason.CUSTOM)));
        assertTrue(spawnEvents.isEmpty());
    }

    @Test
    void emptyItemEntityIsRejectedOnlyWithEvents() {
        Entity item = EntityType.byString("minecraft:item").orElseThrow().create(level);
        item.absMoveTo(at.x() + 0.5, at.y(), at.z() + 7.5, 0, 0);
        assertFalse(on(() -> level.addFreshEntity(item, SpawnReason.CUSTOM)), "Paper addEntity: 빈 아이템 엔티티는 거짓");
    }

    @Test
    void worldEditCreateEntityWithPassengers() {
        Entity e = EntityType.byString("minecraft:chicken").orElseThrow().create(level);
        // WorldEdit createEntity 는 자리를 정하지 않는다 — 바닐라 생성자의 0,0,0 이 그 자리다. 이 시험은 로드된 칸으로 옮긴다
        e.absMoveTo(at.x() + 0.5, at.y(), at.z() + 8.5, 0, 0);
        on(() -> { level.addFreshEntityWithPassengers(e, SpawnReason.CUSTOM); return null; });
        assertEquals(List.of("minecraft:chicken CUSTOM"), spawnEvents);
        on(() -> { e.getBukkitEntity().remove(); return null; });
    }

    @Test
    void worldEntitiesCannotBeReloadedThroughTheShim() {
        CraftEntity cow = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 9.5), org.bukkit.entity.EntityType.COW));
        Entity h = on(cow::getHandle);
        assertThrows(UnsupportedNmsCall.class, () -> h.load(new CompoundTag()));
        assertThrows(UnsupportedNmsCall.class, () -> h.absMoveTo(0, 0, 0, 0, 0));
        assertThrows(UnsupportedNmsCall.class, () -> h.setUUID(UUID.randomUUID()));
        on(() -> { cow.remove(); return null; });
    }
}
