package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 엔티티 공통 설정(FancyNpcs·LagFixer·GSit 가 부르는 바닐라 Entity 의 setter)과 EntityType 상수. 아직 월드에 없는 엔티티는 기록했다가
 * 넣을 때 재생하고, 월드의 엔티티는 주인 스레드에서 서버 엔티티를 바로 바꾼다 — 두 길 모두 서버 엔티티의 값으로 확인한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsEntitySettersTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    CraftWorld world;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Setters");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()) + 2, (int) Math.floor(loc.y()), (int) Math.floor(loc.z()) + 2);
        world = (CraftWorld) Compat.player(tp.player()).getWorld();
        level = world.getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    org.domi.furnace.server.entity.Entity serverEntity(Entity e) {
        return on(() -> org.domi.furnace.api.internal.Internals.get().entityNow(((CraftEntity) e.getBukkitEntity()).furnaceHandle(),
                org.domi.furnace.server.entity.Entity.class));
    }

    @Test
    void detachedSettersAreReplayedWhenAdded() {
        Entity e = EntityType.ARMOR_STAND.create(level);
        e.setPos(new Vec3(at.x() + 0.5, at.y(), at.z() + 0.5));
        e.setRot(450.0F, 30.0F);
        e.setYRot(Float.NaN);   // 바닐라: 유한하지 않은 값은 버린다
        e.setInvisible(true);
        e.setGlowingTag(true);
        e.setCustomName(Component.literal("Npc"));
        e.setCustomNameVisible(true);
        e.setSilent(true);
        e.setTicksFrozen(40);
        assertEquals(at.x() + 0.5, e.getX());
        assertEquals(90.0F, e.getYRot(), "setRot 은 360 으로 나눈 나머지");
        assertEquals(30.0F, e.getXRot());
        assertTrue(e.isInvisible(), "월드에 넣기 전에도 바닐라처럼 바뀐 값");
        assertEquals(140, e.getTicksRequiredToFreeze());
        assertTrue(on(() -> level.addFreshEntity(e, SpawnReason.CUSTOM)));
        try {
            var s = serverEntity(e);
            assertEquals(at.x() + 0.5, (double) on(s::x));
            assertEquals(90.0F, (float) on(s::yRot));
            assertEquals(30.0F, (float) on(s::xRot));
            assertTrue(on(s::isInvisible));
            assertTrue(on(s::hasGlowingTag));
            assertEquals("Npc", on(() -> s.getCustomName().plainText()));
            assertTrue(on(s::isCustomNameVisible));
            assertTrue(on(s::isSilent));
            assertEquals(40, (int) on(s::getTicksFrozen));
            assertTrue(on(e::isInvisible), "월드에 들어간 뒤는 서버 값");
        } finally {
            on(() -> { e.getBukkitEntity().remove(); return null; });
        }
    }

    /** 월드 밖 기록은 섞어 불러도 부른 차례대로 재생된다: 나중 호출이 이긴다(바닐라와 같은 마지막 값). */
    @Test
    void detachedSettersAndMovesReplayInCallOrder() {
        Entity a = EntityType.ARMOR_STAND.create(level);
        a.setPos(at.x() + 0.5, at.y(), at.z() + 5.5);
        a.moveTo(at.x() + 1.5, at.y(), at.z() + 6.5, 10.0F, 0.0F);
        Entity b = EntityType.ARMOR_STAND.create(level);
        b.moveTo(at.x() + 1.5, at.y(), at.z() + 7.5, 20.0F, 0.0F);
        b.setPos(at.x() + 2.5, at.y(), at.z() + 7.5);
        b.setYRot(30.0F);
        assertEquals(at.x() + 2.5, b.getX());
        assertEquals(30.0F, b.getYRot());
        assertTrue(on(() -> level.addFreshEntity(a, SpawnReason.CUSTOM)));
        assertTrue(on(() -> level.addFreshEntity(b, SpawnReason.CUSTOM)));
        try {
            var sa = serverEntity(a);
            assertEquals(at.x() + 1.5, (double) on(sa::x), "setPos 뒤 moveTo: moveTo 자리");
            assertEquals(10.0F, (float) on(sa::yRot));
            var sb = serverEntity(b);
            assertEquals(at.x() + 2.5, (double) on(sb::x), "moveTo 뒤 setPos: setPos 자리");
            assertEquals(30.0F, (float) on(sb::yRot), "그 뒤 setYRot");
        } finally {
            on(() -> { a.getBukkitEntity().remove(); b.getBukkitEntity().remove(); return null; });
        }
    }

    @Test
    void liveSettersChangeTheServerEntityOnItsOwner() {
        Entity e = EntityType.ARMOR_STAND.create(level);
        e.moveTo(at.x() + 3.5, at.y(), at.z() + 0.5, 0.0F, 0.0F);
        assertTrue(on(() -> level.addFreshEntity(e, SpawnReason.CUSTOM)));
        try {
            var s = serverEntity(e);
            on(() -> {
                e.setYRot(45.0F);
                e.setXRot(-10.0F);
                e.setInvisible(true);
                e.setSharedFlagOnFire(true);
                e.setCustomName(Component.literal("Live"));
                e.setSilent(true);
                e.setTicksFrozen(7);
                return null;
            });
            assertEquals(45.0F, (float) on(s::yRot));
            assertEquals(-10.0F, (float) on(s::xRot));
            assertTrue(on(s::isInvisible));
            assertTrue(on(e::isInvisible));
            assertEquals("Live", on(() -> s.getCustomName().plainText()));
            assertTrue(on(s::isSilent));
            assertEquals(7, (int) on(s::getTicksFrozen));
            on(() -> { e.setCustomName(null); e.setInvisible(false); return null; });
            assertNull(on(s::getCustomName), "null 은 이름을 지운다");
            assertFalse(on(s::isInvisible));
            on(() -> { e.teleportTo(at.x() + 4.5, at.y(), at.z() + 1.5); return null; });
            assertEquals(at.x() + 4.5, (double) on(s::x));
            assertEquals(45.0F, (float) on(s::yRot), "teleportTo(x, y, z) 는 지금 각도를 둔다");
            on(() -> { e.setPos(at.x() + 3.5, at.y(), at.z() + 0.5); return null; });
            assertEquals(at.x() + 3.5, (double) on(s::x));
        } finally {
            on(() -> { e.getBukkitEntity().remove(); return null; });
        }
    }

    @Test
    void liveSetterFromAWorkerThreadWaitsForTheOwner() {
        Entity e = EntityType.ARMOR_STAND.create(level);
        e.moveTo(at.x() + 6.5, at.y(), at.z() + 0.5, 0.0F, 0.0F);
        assertTrue(on(() -> level.addFreshEntity(e, SpawnReason.CUSTOM)));
        try {
            e.setInvisible(true);   // 틱 스레드가 아닌 작업 스레드: 주인 region 에 맡겨 기다린다(다른 쓰기와 같은 규칙)
            assertTrue(on(serverEntity(e)::isInvisible));
        } finally {
            on(() -> { e.getBukkitEntity().remove(); return null; });
        }
    }

    /** EntityType 상수: 바닐라 130개(종류 표 129 + PLAYER), 형식 인자는 표의 클래스, byString 과 같은 객체. */
    @Test
    void entityTypeConstantsMatchTheKindTable() throws Exception {
        int rows = 0;
        try (var in = new java.io.BufferedReader(new java.io.InputStreamReader(
                java.nio.file.Files.newInputStream(java.nio.file.Path.of("src/shimspec/kind-classes-1.21.1.txt")), java.nio.charset.StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                if (!line.startsWith("E ")) continue;
                String[] p = line.split(" ");
                String path = p[1].substring("minecraft:".length());
                Field f = EntityType.class.getField(path.toUpperCase(java.util.Locale.ROOT));
                assertTrue(Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers()), f.getName());
                var arg = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
                assertEquals(p[2], ((Class<?>) arg).getName(), f.getName());
                assertSame(f.get(null), EntityType.byString(path).orElseThrow(), f.getName());
                rows++;
            }
        }
        assertEquals(129, rows);
        long constants = java.util.Arrays.stream(EntityType.class.getFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == EntityType.class).count();
        assertEquals(130, constants, "바닐라 1.21.1 EntityType 상수 수(PLAYER 포함)");
    }

    /** 장비 칸: Paper 1.21.1 정적 초기화의 값(종류·번호·필터 번호·이름)과 byName. */
    @Test
    void equipmentSlotsCarryVanillaValues() {
        Object[][] want = {   // 칸, 종류, 번호, 필터 번호, 이름, 갑옷
            {net.minecraft.world.entity.EquipmentSlot.MAINHAND, "HAND", 0, 0, "mainhand", false},
            {net.minecraft.world.entity.EquipmentSlot.OFFHAND, "HAND", 1, 5, "offhand", false},
            {net.minecraft.world.entity.EquipmentSlot.FEET, "HUMANOID_ARMOR", 0, 1, "feet", true},
            {net.minecraft.world.entity.EquipmentSlot.LEGS, "HUMANOID_ARMOR", 1, 2, "legs", true},
            {net.minecraft.world.entity.EquipmentSlot.CHEST, "HUMANOID_ARMOR", 2, 3, "chest", true},
            {net.minecraft.world.entity.EquipmentSlot.HEAD, "HUMANOID_ARMOR", 3, 4, "head", true},
            {net.minecraft.world.entity.EquipmentSlot.BODY, "ANIMAL_ARMOR", 0, 6, "body", true},
        };
        assertEquals(want.length, net.minecraft.world.entity.EquipmentSlot.values().length);
        for (Object[] w : want) {
            var s = (net.minecraft.world.entity.EquipmentSlot) w[0];
            assertEquals(w[1], s.getType().name(), s.name());
            assertEquals(w[2], s.getIndex(), s.name());
            assertEquals((int) w[2] + 10, s.getIndex(10), s.name());
            assertEquals(w[3], s.getFilterFlag(), s.name());
            assertEquals(w[4], s.getName());
            assertEquals(w[4], s.getSerializedName());
            assertEquals(w[5], s.isArmor(), s.name());
            assertSame(s, net.minecraft.world.entity.EquipmentSlot.byName((String) w[4]));
        }
        var e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> net.minecraft.world.entity.EquipmentSlot.byName("Head"));
        assertEquals("Invalid slot 'Head'", e.getMessage());
    }
}
