package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftArmorStand;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.domi.furnace.api.entity.ArmorStandState;
import org.domi.furnace.api.entity.EntityState;
import org.domi.furnace.api.entity.LivingEntityState;
import org.domi.furnace.api.entity.attribute.Attribute;
import org.domi.furnace.api.entity.attribute.AttributeInstance;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.UnsupportedFeature;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 월드 밖 NMS 엔티티의 Bukkit 엔티티(getBukkitEntity) — 설계 furnace/designs/detached-bukkit-entity-design-2026-11-27.md, 검토 필수 6개.
 * 월드 밖 쓰기는 기록해 두었다가 넣을 때 서버가 재생하고, 넣은 뒤에도 같은 CraftEntity 다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsDetachedBukkitEntityTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    CraftWorld world;
    BlockPos at;
    final List<Object> spawnSeen = new CopyOnWriteArrayList<>();
    volatile boolean cancelSpawns;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Detached");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()) + 2, (int) Math.floor(loc.y()) + 1, (int) Math.floor(loc.z()) + 2);
        world = (CraftWorld) Compat.player(tp.player()).getWorld();
        level = world.getHandle();
        server.api().events().subscribe(server.plugin(CompatTestPlugin.class), org.domi.furnace.api.event.entity.CreatureSpawnEvent.class,
                EventPriority.NORMAL, false, e -> {
                    // 스폰 이벤트 처리기가 Bukkit 엔티티를 얻는 길: 플레이어 이벤트 브리지와 같은 Compat.entity
                    spawnSeen.add(Compat.entity(e.entity()));
                    if (cancelSpawns) e.setCancelled(true);
                });
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void reset() {
        spawnSeen.clear();
        cancelSpawns = false;
    }

    <T> T on(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    Entity create(String id, double dx, double dz) {
        Entity e = EntityType.byString(id).orElseThrow().create(level);
        e.absMoveTo(at.x() + dx, at.y(), at.z() + dz, 0, 0);
        return e;
    }

    org.domi.furnace.server.entity.Entity serverEntity(UUID id) {
        return on(() -> {
            var b = Bukkit.getEntity(id);
            return b == null ? null : org.domi.furnace.api.internal.Internals.get().entityNow(((CraftEntity) b).furnaceHandle(), org.domi.furnace.server.entity.Entity.class);
        });
    }

    // ------------------------------------------------------------------ 모양

    @Test
    void getBukkitEntityOfADetachedEntityHasTheKindOfCraftEntityAndIsNotValid() {
        Entity pig = create("minecraft:pig", 0.5, 20.5);
        CraftEntity b = pig.getBukkitEntity();
        assertInstanceOf(org.bukkit.craftbukkit.entity.CraftPig.class, b);
        assertSame(b, pig.getBukkitEntity(), "같은 엔티티는 같은 객체");
        assertSame(pig, b.getHandle(), "getHandle 은 그 NMS 엔티티 자신");
        assertFalse(b.isValid());
        assertEquals(org.bukkit.entity.EntityType.PIG, b.getType());
        Location l = b.getLocation();
        assertEquals(at.x() + 0.5, l.getX());
        assertEquals(at.z() + 20.5, l.getZ());
        assertEquals(world, l.getWorld());
        assertInstanceOf(CraftLivingEntity.class, create("minecraft:cow", 0, 0).getBukkitEntity());
        assertFalse(create("minecraft:item", 0, 0).getBukkitEntity() instanceof CraftLivingEntity);
        assertInstanceOf(CraftArmorStand.class, create("minecraft:armor_stand", 0, 0).getBukkitEntity());
        assertThrows(UnsupportedFeature.class, b::getEntityId, "번호는 넣을 때 정해진다");
    }

    @Test
    void writesAreReadBackAndReplayedWhenTheEntityIsAdded() {
        Entity stand = create("minecraft:armor_stand", 0.5, 21.5);
        CraftArmorStand b = (CraftArmorStand) stand.getBukkitEntity();
        b.setCustomName("Held");
        b.setInvulnerable(true);
        b.setSmall(true);
        b.setSilent(true);
        assertEquals("Held", b.getCustomName(), "월드 밖 읽기가 쓴 값을 본다");
        assertTrue(b.isInvulnerable());
        assertTrue(b.isSmall());
        assertTrue(on(() -> level.addFreshEntity(stand, SpawnReason.CUSTOM)));
        assertSame(b, stand.getBukkitEntity(), "넣은 뒤에도 같은 CraftEntity");
        assertSame(b, Bukkit.getEntity(b.getUniqueId()), "UUID 로 찾으면 같은 객체");
        var s = serverEntity(b.getUniqueId());
        assertNotNull(s);
        assertEquals("Held", on(b::getCustomName));
        assertTrue(s.isInvulnerable());
        assertTrue(s.isSilent());
        assertTrue(on(b::isSmall));
        assertTrue(on(b::isValid));
        on(() -> { b.remove(); return null; });
    }

    // ------------------------------------------------------------------ 필수 1: 넣기 전 예약

    @Test
    void theSpawnEventSeesTheSameObjectThePluginHeld() {
        Entity pig = create("minecraft:pig", 0.5, 22.5);
        CraftEntity held = pig.getBukkitEntity();
        UUID before = held.getUniqueId();
        assertTrue(on(() -> level.addFreshEntity(pig, SpawnReason.CUSTOM)));
        assertEquals(List.of(held), new ArrayList<>(spawnSeen), "스폰 이벤트의 getEntity() 가 플러그인이 쥔 그 객체");
        assertSame(held, spawnSeen.get(0));
        assertEquals(before, held.getUniqueId(), "UUID 는 넣기 전에 정해져 넣은 엔티티와 같다");
        on(() -> { held.remove(); return null; });
    }

    @Test
    void theUuidChosenBeforeAddingIsTheOneOfTheAddedEntity() {
        Entity cow = create("minecraft:cow", 0.5, 23.5);
        UUID id = cow.getBukkitEntity().getUniqueId();
        assertTrue(on(() -> level.addFreshEntity(cow, SpawnReason.CUSTOM)));
        assertEquals(id, cow.getUUID());
        assertNotNull(serverEntity(id));
        on(() -> { cow.getBukkitEntity().remove(); return null; });
    }

    @Test
    void setUuidAfterTheBukkitEntityWasMadeIsFollowed() {
        Entity cow = create("minecraft:cow", 0.5, 24.5);
        CraftEntity b = cow.getBukkitEntity();
        UUID forced = UUID.randomUUID();
        cow.setUUID(forced);
        assertEquals(forced, b.getUniqueId());
        assertTrue(on(() -> level.addFreshEntity(cow, SpawnReason.CUSTOM)));
        assertSame(b, Bukkit.getEntity(forced));
        on(() -> { b.remove(); return null; });
    }

    // ------------------------------------------------------------------ 필수 2: 넣기 취소

    @Test
    void aCancelledAddKeepsTheDetachedBukkitEntityUsable() {
        Entity pig = create("minecraft:pig", 0.5, 25.5);
        CraftEntity b = pig.getBukkitEntity();
        b.setCustomName("Kept");
        cancelSpawns = true;
        assertFalse(on(() -> level.addFreshEntity(pig, SpawnReason.CUSTOM)), "이벤트 취소");
        assertSame(b, spawnSeen.get(0), "처리기가 본 것도 같은 객체");
        assertFalse(b.isValid());
        assertSame(b, pig.getBukkitEntity());
        assertTrue(b.furnaceHandle() instanceof DetachedEntityHandle, "핸들이 다시 월드 밖 핸들");
        assertNull_(Bukkit.getEntity(b.getUniqueId()), "월드에 없다");
        assertNull_(Compat.cachedEntity(b.getUniqueId()), "취소된 엔티티가 UUID 표에 남지 않는다");
        assertEquals("Kept", b.getCustomName());
        b.setCustomName("Still writable");
        assertEquals("Still writable", b.getCustomName(), "취소 뒤에도 읽고 쓸 수 있다");
    }

    @Test
    void aDuplicateUuidAddKeepsTheExistingEntitiesSlot() {
        CraftEntity existing = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 30.5), org.bukkit.entity.EntityType.COW));
        UUID id = existing.getUniqueId();
        Entity dup = create("minecraft:cow", 1.5, 30.5);
        CraftEntity d = dup.getBukkitEntity();
        dup.setUUID(id);
        assertFalse(on(() -> level.addFreshEntity(dup, SpawnReason.CUSTOM)), "같은 UUID 라 넣기 실패");
        assertSame(existing, Compat.cachedEntity(id), "이미 월드에 있던 엔티티의 표 칸을 잃지 않는다");
        assertSame(existing, Bukkit.getEntity(id));
        assertTrue(d.furnaceHandle() instanceof DetachedEntityHandle);
        on(() -> { existing.remove(); return null; });
    }

    private static void assertNull_(Object o, String msg) { org.junit.jupiter.api.Assertions.assertNull(o, msg); }

    // ------------------------------------------------------------------ 필수 3: 봉인 — 쥐고 있던 속성 객체의 쓰기

    @Test
    void anAttributeInstanceHeldBeforeAddingWritesToTheAddedEntity() {
        Entity pig = create("minecraft:pig", 0.5, 26.5);
        CraftEntity b = pig.getBukkitEntity();
        var state = (LivingEntityState) ((DetachedEntityHandle) b.furnaceHandle()).stateNow().orElseThrow();
        AttributeInstance held = state.attribute(Attribute.MAX_HEALTH).orElseThrow();
        held.setBaseValue(33.0);
        assertEquals(33.0, held.baseValue());
        assertEquals(33.0, state.attribute(Attribute.MAX_HEALTH).orElseThrow().baseValue(), "월드 밖 읽기");
        assertTrue(on(() -> level.addFreshEntity(pig, SpawnReason.CUSTOM)));
        var s = (org.domi.furnace.server.entity.LivingEntity) serverEntity(b.getUniqueId());
        assertEquals(33.0, s.attributes().get(org.domi.furnace.server.attribute.Attribute.MAX_HEALTH).baseValue(), "재생된 값");
        // 넣은 뒤 쥐고 있던 객체의 쓰기는 새 엔티티로 간다(주인 스레드에서)
        on(() -> { held.setBaseValue(41.0); return null; });
        assertEquals(41.0, s.attributes().get(org.domi.furnace.server.attribute.Attribute.MAX_HEALTH).baseValue());
        assertEquals(41.0, on(held::baseValue));
        on(() -> { b.remove(); return null; });
    }

    @Test
    void writesRecordedAreFrozenCopies() {
        Entity stand = create("minecraft:armor_stand", 0.5, 27.5);
        var h = (DetachedEntityHandle) stand.getBukkitEntity().furnaceHandle();
        var state = (LivingEntityState) h.stateNow().orElseThrow();
        var mod = new org.domi.furnace.api.entity.attribute.AttributeModifier("a", 2.0, org.domi.furnace.api.item.AttributeModifiers.Operation.ADD_VALUE);
        state.attribute(Attribute.ARMOR).orElseThrow().addModifier(mod);
        assertEquals(1, state.attribute(Attribute.ARMOR).orElseThrow().modifiers().size());
    }

    // ------------------------------------------------------------------ 필수 5: 서버 입구

    @Test
    void aDetachedEntityPassedToTheServerGivesAClearError() {
        Entity detached = create("minecraft:pig", 0.5, 28.5);
        CraftEntity d = detached.getBukkitEntity();
        CraftEntity vehicle = on(() -> (CraftEntity) world.spawnEntity(new Location(world, at.x() + 0.5, at.y(), at.z() + 29.5), org.bukkit.entity.EntityType.BOAT));
        var ex = assertThrows(UnsupportedOperationException.class, () -> on(() -> vehicle.addPassenger(d)));
        assertTrue(ex.getMessage().contains("not in a world yet"), ex.getMessage());
        on(() -> { vehicle.remove(); return null; });
        // 월드 밖 탈것·승객: 서버에 대응 조작이 없어 분명히 던진다
        Entity other = create("minecraft:cow", 1.5, 28.5);
        assertThrows(UnsupportedFeature.class, () -> d.addPassenger(other.getBukkitEntity()));
        assertEquals(List.of(), d.getPassengers());
    }

    // ------------------------------------------------------------------ 필수 6: 기록 방식·분류표

    @Test
    void everyStateMethodHasExactlyOnePolicy() {
        List<String> missing = new ArrayList<>();
        List<String> known = new ArrayList<>();
        for (Class<?> c : new Class<?>[] {EntityState.class, LivingEntityState.class, ArmorStandState.class, AttributeInstance.class}) {
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                String k = DetachedEntityHandle.key(m);
                known.add(k);
                if (!DetachedEntityHandle.POLICY.containsKey(k)) missing.add(k);
            }
        }
        assertTrue(missing.isEmpty(), "분류표에 없는 상태 메서드(월드 밖에서 조용히 잃을 수 있다): " + missing);
        List<String> stale = new ArrayList<>(DetachedEntityHandle.POLICY.keySet());
        stale.removeAll(known);
        assertTrue(stale.isEmpty(), "없는 메서드의 분류: " + stale);
    }

    @Test
    void callsMustBeApiInterfaceMethods() throws Exception {
        Method implMethod = String.class.getMethod("length");
        var bad = new org.domi.furnace.api.internal.ChunkInternals.EntityOp.StateCall(
                List.of(new org.domi.furnace.api.internal.ChunkInternals.Call(implMethod, List.of())));
        var session = org.domi.furnace.api.internal.Internals.get().chunks().openDetachedEntityData("minecraft:cow", List.of());
        assertThrows(IllegalArgumentException.class, () -> session.apply(bad));
        // 인자는 기록할 때 복사한다
        List<Object> mutable = new ArrayList<>(List.of("x"));
        var c = new org.domi.furnace.api.internal.ChunkInternals.Call(EntityState.class.getMethod("isSilent"), mutable);
        mutable.add("y");
        assertEquals(1, c.args().size());
    }

    // ------------------------------------------------------------------ 필수 3: 넣는 중과 넣은 뒤의 길(가짜 뒷받침)

    @Test
    void writesWhileBeingAddedOnAnotherThreadThrowAndWritesAfterTheTargetIsKnownAreForwarded() {
        var fakeSession = new org.domi.furnace.api.internal.ChunkInternals.DetachedEntityData() {
            @Override public List<org.domi.furnace.api.internal.ChunkInternals.EntityDataSlot> slots() { return List.of(); }

            @Override public List<org.domi.furnace.api.internal.ChunkInternals.EntityDataSlot> apply(org.domi.furnace.api.internal.ChunkInternals.EntityOp op) { return List.of(); }

            @Override public List<Class<?>> stateTypes() { return List.of(EntityState.class); }
        };
        var calls = new ArrayList<String>();
        EntityState recording = (EntityState) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {EntityState.class}, (p, m, a) -> {
            calls.add(m.getName());
            return m.getReturnType() == boolean.class ? Boolean.FALSE : null;
        });
        var target = (org.domi.furnace.api.entity.Entity) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {org.domi.furnace.api.entity.Entity.class}, (p, m, a) -> switch (m.getName()) {
            case "isOwnedByCurrentThread" -> true;
            case "stateNow" -> java.util.Optional.of(recording);
            case "isValid" -> true;
            case "uniqueId" -> UUID.nameUUIDFromBytes(new byte[] {1});
            default -> throw new UnsupportedOperationException(m.getName());
        });
        var fake = new FakeBacking(fakeSession);
        var handle = DetachedEntityHandle.create(fake);
        EntityState st = handle.stateNow().orElseThrow();
        fake.adding = true;   // 조작 목록을 가져간 뒤, 서버 엔티티가 정해지기 전
        assertThrows(UnsupportedFeature.class, () -> st.setSilent(true), "조용히 사라지지 않고 던진다");
        fake.target = target;   // 서버가 넣을 엔티티를 알렸다
        st.setSilent(true);
        assertEquals(List.of("setSilent"), calls, "새 엔티티로 넘긴다");
        fake.adding = false;   // 넣기가 끝난 뒤(봉인)
        st.setNoGravity(true);
        assertEquals(List.of("setSilent", "setNoGravity"), calls, "옛 핸들의 쓰기도 새 엔티티로");
        assertEquals(1, fake.recorded.size(), "넣는 중의 쓰기는 넣기가 취소될 때를 위해 월드 밖 기록에도 남는다");
    }

    private static final class FakeBacking implements DetachedEntityHandle.Backing {
        final org.domi.furnace.api.internal.ChunkInternals.DetachedEntityData session;
        volatile boolean adding;
        volatile org.domi.furnace.api.entity.Entity target;
        final List<Object> recorded = new ArrayList<>();

        FakeBacking(org.domi.furnace.api.internal.ChunkInternals.DetachedEntityData session) { this.session = session; }

        @Override public Object lock() { return this; }

        @Override public UUID uuid() { return UUID.nameUUIDFromBytes(new byte[] {1}); }

        @Override public String kind() { return "minecraft:cow"; }

        @Override public org.domi.furnace.api.world.Location location() { return new org.domi.furnace.api.world.Location("minecraft:overworld", 0, 0, 0, 0, 0); }

        @Override public org.domi.furnace.api.internal.ChunkInternals.DetachedEntityData session() { return session; }

        @Override
        public Object recordForResult(org.domi.furnace.api.internal.ChunkInternals.EntityOp.StateCall op) {
            recorded.add(op);
            return null;
        }

        @Override public org.domi.furnace.api.entity.Entity target() { return target; }

        @Override public boolean adding() { return adding; }

        @Override public boolean removed() { return false; }

        @Override public void markRemoved() {}

        @Override public boolean hasLinks() { return false; }
    }
}
