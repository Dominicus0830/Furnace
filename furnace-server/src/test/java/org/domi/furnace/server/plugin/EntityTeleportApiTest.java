// 플러그인이 플레이어가 아닌 엔티티를 다른 월드로 teleport 한다(Paper 1.21.1 CraftEntity#teleport): EntityTeleportEvent 를 부르고(다른 월드면 두 번 —
// 요청 때, Entity#changeDimension 안), 탈것에서 내린 뒤 목적 월드에 같은 종류를 새로 만든다(UUID·저장 데이터 유지, 속도 0, 승객 있는 탈것은 거절).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.event.entity.EntityTeleportEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Boat;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.DimensionType;
import org.domi.furnace.server.world.Dimensions;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityTeleportApiTest {

    static final DimensionType TEST_NETHER = new DimensionType("minecraft:the_nether", "minecraft:the_nether", -64, 384, 384,
            false, true, true, false, 8.0, 18000L, false, true, true, NoiseWorldSource.Dimension.NETHER);
    static final String NETHER = "minecraft:the_nether";
    static final String OVERWORLD = "minecraft:overworld";
    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private World nether;
    private PluginHost host;
    private final FurnacePlugin plugin = new FurnacePlugin() {};
    private final List<Location[]> events = new CopyOnWriteArrayList<>();   // {from, to}

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        BlockRegistry reg = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
        core.world().setRegistries(reg, org.domi.furnace.server.world.ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt"));
        var io = TestServers.ioPool();
        nether = new World(TEST_NETHER, new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2, reg.directBits(), 7, io, io);
        nether.worldDir(tmp);
        Dimensions dims = new Dimensions();
        dims.register(core.world());
        dims.register(nether);
        core.dimensions(dims);
        core.world().startTicking();
        nether.startTicking();
        org.domi.furnace.server.TestPluginContexts.init(plugin, host, "etp", tmp);
        plugin.setEnabled(true);
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.eventsImpl().unregisterAll(plugin);
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private void record(java.util.function.Predicate<EntityTeleportEvent> cancelWhen) {
        host.eventsImpl().subscribe(plugin, EntityTeleportEvent.class, e -> {
            events.add(new Location[] {e.from(), e.to()});
            if (cancelWhen.test(e)) e.setCancelled(true);
        });
    }

    /** 오버월드 (8,-50,8) 에 소를 만든다. */
    private Object[] spawnCow() throws Exception {
        return core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -50, 8, 10, 20);
            cow.persistentData(true).putString("marker", "kept");
            level.addFreshEntity(cow);
            return new Object[] {cow.uuid(), EntityHandle.of(host, cow), cow};
        }).get(30, TimeUnit.SECONDS);
    }

    private Entity find(World w, UUID id, double x, double z) throws Exception {
        return w.submitAtChunk(ChunkPos.ofBlock(x, z), r -> {
            Entity e = r.level().entities().get(id);
            return e == null || e.isRemoved() ? null : e;
        }).get(30, TimeUnit.SECONDS);
    }

    @Test
    void crossWorldMovesTheEntityKeepingIdentityAndFiresTwoEvents() throws Exception {
        record(e -> false);
        Object[] spawned = spawnCow();
        UUID id = (UUID) spawned[0];
        var h = (org.domi.furnace.api.entity.Entity) spawned[1];
        boolean ok = h.teleport(new Location(NETHER, 40.5, -50, 40.5, 90f, 30f)).get(30, TimeUnit.SECONDS);
        assertTrue(ok);
        assertNull(find(core.world(), id, 8, 8), "원래 월드에서는 빠졌다");
        Entity moved = find(nether, id, 40.5, 40.5);
        assertNotNull(moved, "같은 UUID 로 네더에");
        assertEquals("kept", moved.persistentData(false).getString("marker"), "저장 데이터 유지");
        assertEquals(40.5, moved.x(), 1e-9);
        assertEquals(-50.0, moved.y(), 1e-9);
        assertEquals(0.0, moved.vx() + moved.vy() + moved.vz(), 1e-9, "속도 0");
        assertEquals(30f, moved.yRot(), "CraftBukkit 1.21.1 은 DimensionTransition 에 (pitch, yaw) 순으로 넘겨 yRot 에 요청한 pitch 가 들어간다");
        assertEquals(20f, moved.xRot(), "pitch 는 지금 xRot 그대로");
        assertEquals(2, events.size(), "다른 월드는 EntityTeleportEvent 두 번");
        assertEquals(new Location(OVERWORLD, 8, -50, 8, 10f, 20f), events.get(0)[0]);
        assertEquals(new Location(NETHER, 40.5, -50, 40.5, 90f, 30f), events.get(0)[1], "첫 번째는 요청한 위치");
        assertEquals(new Location(NETHER, 40.5, -50, 40.5, 30f, 20f), events.get(1)[1], "두 번째는 changeDimension 의 도착 위치");
    }

    @Test
    void cancelKeepsEntityWhereItIs() throws Exception {
        record(e -> true);
        Object[] spawned = spawnCow();
        var h = (org.domi.furnace.api.entity.Entity) spawned[1];
        assertFalse(h.teleport(new Location(NETHER, 40.5, -50, 40.5)).get(30, TimeUnit.SECONDS));
        assertNotNull(find(core.world(), (UUID) spawned[0], 8, 8));
        assertNull(find(nether, (UUID) spawned[0], 40.5, 40.5));
        assertEquals(1, events.size(), "첫 번째에서 취소하면 두 번째는 없다");
    }

    @Test
    void cancellingTheSecondEventStillReturnsTrueButMovesNothingAndDismounts() throws Exception {
        record(e -> events.size() >= 2);   // 두 번째 이벤트부터 취소
        Object[] out = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Boat boat = new Boat(level, level.nextEntityId());
            boat.moveTo(6, -50, 6, 0, 0);
            level.addFreshEntity(boat);
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(6, -50, 6, 0, 0);
            level.addFreshEntity(cow);
            assertTrue(cow.startRiding(boat, true));
            return new Object[] {cow.uuid(), EntityHandle.of(host, cow), cow};
        }).get(30, TimeUnit.SECONDS);
        var h = (org.domi.furnace.api.entity.Entity) out[1];
        assertTrue(h.teleport(new Location(NETHER, 40.5, -50, 40.5)).get(30, TimeUnit.SECONDS), "Paper 는 changeDimension 이 null 이어도 true");
        assertEquals(2, events.size());
        Entity cow = (Entity) out[2];
        assertFalse(cow.isRemoved());
        assertNull(core.world().submitAtChunk(C00, r -> cow.vehicle()).get(30, TimeUnit.SECONDS), "탈것에서는 내렸다(Paper 는 이벤트 뒤 stopRiding)");
        assertNull(find(nether, (UUID) out[0], 40.5, 40.5));
    }

    @Test
    void cancelledFirstEventKeepsTheRidersVehicle() throws Exception {
        record(e -> true);
        Object[] out = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Boat boat = new Boat(level, level.nextEntityId());
            boat.moveTo(6, -50, 6, 0, 0);
            level.addFreshEntity(boat);
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(6, -50, 6, 0, 0);
            level.addFreshEntity(cow);
            assertTrue(cow.startRiding(boat, true));
            return new Object[] {EntityHandle.of(host, cow), cow, boat};
        }).get(30, TimeUnit.SECONDS);
        assertFalse(((org.domi.furnace.api.entity.Entity) out[0]).teleport(new Location(NETHER, 40.5, -50, 40.5)).get(30, TimeUnit.SECONDS));
        assertSame(out[2], core.world().submitAtChunk(C00, r -> ((Entity) out[1]).vehicle()).get(30, TimeUnit.SECONDS));
    }

    @Test
    void vehicleWithPassengerIsRefusedWithoutEvents() throws Exception {
        record(e -> false);
        Object[] out = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Boat boat = new Boat(level, level.nextEntityId());
            boat.moveTo(6, -50, 6, 0, 0);
            level.addFreshEntity(boat);
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(6, -50, 6, 0, 0);
            level.addFreshEntity(cow);
            assertTrue(cow.startRiding(boat, true));
            return new Object[] {EntityHandle.of(host, boat), boat.uuid()};
        }).get(30, TimeUnit.SECONDS);
        assertFalse(((org.domi.furnace.api.entity.Entity) out[0]).teleport(new Location(NETHER, 40.5, -50, 40.5)).get(30, TimeUnit.SECONDS));
        assertTrue(events.isEmpty());
        assertNotNull(find(core.world(), (UUID) out[1], 6, 6));
    }

    @Test
    void handlerCanRedirectTheDestinationAndSameWorldFiresOneEvent() throws Exception {
        host.eventsImpl().subscribe(plugin, EntityTeleportEvent.class, e -> {
            events.add(new Location[] {e.from(), e.to()});
            e.to(new Location(OVERWORLD, 4, -50, 4, 0f, 0f));
        });
        Object[] spawned = spawnCow();
        var h = (org.domi.furnace.api.entity.Entity) spawned[1];
        // 같은 월드 요청을 같은 월드로 바꿔도(이번엔 네더로 요청해 같은 월드로 돌린다) 한 번 불리고 그 자리로 옮긴다
        assertTrue(h.teleport(new Location(NETHER, 40.5, -50, 40.5)).get(30, TimeUnit.SECONDS));
        assertEquals(1, events.size());
        Entity cow = find(core.world(), (UUID) spawned[0], 4, 4);
        assertNotNull(cow);
        assertEquals(4.0, cow.x(), 1e-9);
        assertNull(find(nether, (UUID) spawned[0], 40.5, 40.5));
    }

    @Test
    void sameWorldTeleportFiresOneEventAndSyncsHeadRotation() throws Exception {
        record(e -> false);
        Object[] spawned = spawnCow();
        var h = (org.domi.furnace.api.entity.Entity) spawned[1];
        assertTrue(h.teleport(new Location(OVERWORLD, 5.5, -50, 5.5, 77f, 5f)).get(30, TimeUnit.SECONDS));
        assertEquals(1, events.size());
        Entity cow = find(core.world(), (UUID) spawned[0], 5.5, 5.5);
        assertEquals(77f, cow.yRot());
        assertEquals(77f, ((org.domi.furnace.server.entity.LivingEntity) cow).yHeadRot(), "SPIGOT-619: 머리 방향도 맞춘다");
    }

    @Test
    void dyingEntityIsNotMovedButStillReportsTrueAndFiresOnlyTheFirstEvent() throws Exception {
        record(e -> false);
        Object[] spawned = spawnCow();
        core.world().submitAtChunk(C00, r -> {   // 체력 0 = 죽는 중(아직 레벨에서 빠지지 않았다)
            ((org.domi.furnace.server.entity.LivingEntity) spawned[2]).setHealth(0f);
            return null;
        }).get(30, TimeUnit.SECONDS);
        var h = (org.domi.furnace.api.entity.Entity) spawned[1];
        assertTrue(h.teleport(new Location(NETHER, 40.5, -50, 40.5)).get(30, TimeUnit.SECONDS), "Paper changeDimension 은 죽는 중이면 null 이어도 teleport 는 true");
        assertEquals(1, events.size(), "changeDimension 앞에서 끝나 두 번째 이벤트는 없다");
        assertNull(find(nether, (UUID) spawned[0], 40.5, 40.5));
    }

    @Test
    void entityRemovedByTheSecondEventHandlerIsNotMovedAndTheFutureStillCompletes() throws Exception {
        Object[] spawned = spawnCow();
        host.eventsImpl().subscribe(plugin, EntityTeleportEvent.class, e -> {
            events.add(new Location[] {e.from(), e.to()});
            if (events.size() == 2) ((Entity) spawned[2]).discard();   // 두 번째 처리기가 엔티티를 지운다(소유 region 스레드)
        });
        var h = (org.domi.furnace.api.entity.Entity) spawned[1];
        assertTrue(h.teleport(new Location(NETHER, 40.5, -50, 40.5)).get(30, TimeUnit.SECONDS));
        assertEquals(2, events.size());
        assertNull(find(nether, (UUID) spawned[0], 40.5, 40.5), "지워진 엔티티는 목적 월드에 만들지 않는다");
        assertNull(find(core.world(), (UUID) spawned[0], 8, 8));
    }

    /** 그 칸 주인 region 에서 그 UUID 엔티티를 기다린다(같은 월드 다른 region 이동은 원천 region 틱 끝에 넘어간다). */
    private Entity await(World w, UUID id, double x, double z) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < until) {
            Entity e = find(w, id, x, z);
            if (e != null) return e;
            Thread.sleep(20);
        }
        return null;
    }

    @Test
    void sameWorldOtherRegionHandsOffTheSameEntityObject() throws Exception {
        record(e -> false);
        Object[] spawned = spawnCow();
        UUID id = (UUID) spawned[0];
        Entity cow = (Entity) spawned[2];
        var h = (org.domi.furnace.api.entity.Entity) spawned[1];
        int idBefore = cow.id();
        // Paper teleportAsync 처럼 목적지 청크를 먼저 올린다 — 멀리 떨어진, 다른 region 의 칸
        assertTrue(h.teleport(new Location(OVERWORLD, 2000.5, -50, 2000.5, 45f, 10f)).get(30, TimeUnit.SECONDS));
        assertEquals(1, events.size(), "같은 월드는 한 번");
        Entity there = await(core.world(), id, 2000.5, 2000.5);
        assertSame(cow, there, "다른 region 으로 같은 객체째 넘어간다(Paper 는 같은 엔티티를 moveTo)");
        assertEquals(idBefore, there.id(), "엔티티 번호 그대로");
        assertNotSame(core.world().regionizer().ownerOf(0, 0), core.world().regionizer().ownerOf(125, 125), "시험 전제: 서로 다른 region");
        assertNull(find(core.world(), id, 8, 8));
        assertEquals(45f, there.yRot());
        assertEquals("kept", core.world().submitAtChunk(new ChunkPos(125, 125), r -> there.persistentData(false).getString("marker")).get(30, TimeUnit.SECONDS));
        // 옛 핸들(위치는 떠나온 곳)로도 찾아간다 — 핸들 시점 region 에 없으면 엔티티를 따라가는 대기열로
        var fresh = h.refresh().get(30, TimeUnit.SECONDS);
        assertNotNull(fresh);
        assertEquals(2000.5, fresh.location().x(), 1e-9);
        assertTrue(h.teleport(new Location(OVERWORLD, 2001.5, -50, 2000.5)).get(30, TimeUnit.SECONDS), "옛 핸들로 한 번 더 옮길 수 있다");
        assertEquals(2001.5, await(core.world(), id, 2001.5, 2000.5).x(), 1e-9);
    }

    @Test
    void sameWorldTeleportNowToAnotherRegionReturnsTrueAndHandsOff() throws Exception {
        record(e -> false);
        Object[] spawned = spawnCow();
        UUID id = (UUID) spawned[0];
        var h = (org.domi.furnace.api.entity.Entity) spawned[1];
        core.world().submitAtChunk(new ChunkPos(125, 125), r -> r.chunkAtBlocking(new ChunkPos(125, 125))).get(30, TimeUnit.SECONDS);
        boolean ok = core.world().submitAtChunk(C00, r -> h.teleportNow(new Location(OVERWORLD, 2000.5, -50, 2000.5))).get(30, TimeUnit.SECONDS);
        assertTrue(ok, "Paper teleport 는 같은 월드면 어느 칸이든 true");
        assertSame(spawned[2], await(core.world(), id, 2000.5, 2000.5));
    }

    @Test
    void crossWorldKeepsThePluginHandleAndItsScheduledTasks() throws Exception {
        Object[] spawned = spawnCow();
        UUID id = (UUID) spawned[0];
        var h = (org.domi.furnace.api.entity.Entity) spawned[1];
        java.util.concurrent.CompletableFuture<String> ran = new java.util.concurrent.CompletableFuture<>();
        // 옮기기 전에 걸어 둔 작업이 도착한 새 엔티티에서 돈다(Paper: CHANGED_DIMENSION 은 EntityScheduler 를 retire 하지 않고 restoreFrom 이 핸들을 넘긴다)
        assertNotNull(h.entityScheduler().runDelayed(() -> ran.complete(h.stateNow().map(st -> st.location().world()).orElse("gone")),
                () -> ran.complete("retired"), 40));
        assertTrue(h.teleport(new Location(NETHER, 40.5, -50, 40.5)).get(30, TimeUnit.SECONDS));
        Entity moved = find(nether, id, 40.5, 40.5);
        assertNotNull(moved);
        assertEquals(NETHER, ran.get(30, TimeUnit.SECONDS), "작업이 retire 되지 않고 새 엔티티(네더)에서 돈다");
        assertFalse(h.entityScheduler().isRetired());
        assertEquals(moved.id(), h.entityId(), "엔티티 번호는 새 엔티티의 것(Paper getEntityId 도 새 번호)");
        assertEquals(NETHER, h.editState(st -> st.location().world()).get(30, TimeUnit.SECONDS), "쓰기·읽기 작업이 새 엔티티로 간다");
        assertEquals(NETHER, h.refresh().get(30, TimeUnit.SECONDS).location().world(), "옛 핸들의 refresh 가 새 월드에서 찾는다");
        assertTrue(h.remove().get(30, TimeUnit.SECONDS), "옛 핸들로 지울 수 있다");
        assertNull(find(nether, id, 40.5, 40.5));
        assertTrue(h.entityScheduler().isRetired(), "지우면 그때 retire");
    }

    @Test
    void entityThatCannotArriveRetiresItsTasks() throws Exception {
        Object[] spawned = spawnCow();
        UUID id = (UUID) spawned[0];
        var h = (org.domi.furnace.api.entity.Entity) spawned[1];
        java.util.concurrent.CompletableFuture<String> ran = new java.util.concurrent.CompletableFuture<>();
        h.entityScheduler().runDelayed(() -> ran.complete("ran"), () -> ran.complete("retired"), 40);
        // 목적 월드에 같은 UUID 가 이미 있으면 새 엔티티를 넣지 못한다 — 이어 받은 대기열도 끝난다
        nether.submitAtChunk(new ChunkPos(2, 2), r -> {
            r.chunkAtBlocking(new ChunkPos(2, 2));
            Cow twin = new Cow(r.level(), r.level().nextEntityId());
            twin.setUuid(id);
            twin.moveTo(40.5, -50, 40.5, 0, 0);
            return r.level().addFreshEntity(twin);
        }).get(30, TimeUnit.SECONDS);
        assertTrue(h.teleport(new Location(NETHER, 40.5, -50, 40.5)).get(30, TimeUnit.SECONDS));
        assertEquals("retired", ran.get(30, TimeUnit.SECONDS));
    }

    /** 소유 region 스레드에서 서버 이벤트 다리(PluginGameEvents)의 changeDimension 이벤트를 직접 부른다(포털 안이라고 친다). */
    private org.domi.furnace.server.plugin.GameEvents.Arrival changeDimensionEvents(Entity cow, org.domi.furnace.server.block.Pos gateway) throws Exception {
        return changeDimensionEvents(cow, gateway, true);
    }

    private org.domi.furnace.server.plugin.GameEvents.Arrival changeDimensionEvents(Entity cow, org.domi.furnace.server.block.Pos gateway, boolean inPortal) throws Exception {
        var to = new org.domi.furnace.server.plugin.GameEvents.Arrival(NETHER, 40.5, -50, 40.5, 90f, 0f, 0.0, 0.25, 0.0);
        return core.world().submitAtChunk(C00, r -> cow.level().gameEvents().entityChangeDimension(cow, to, gateway, inPortal)).get(30, TimeUnit.SECONDS);
    }

    @Test
    void outsideAPortalTheChangedDestinationIsIgnored() throws Exception {
        Object[] spawned = spawnCow();
        List<String> order = new CopyOnWriteArrayList<>();
        host.eventsImpl().subscribe(plugin, EntityTeleportEvent.class, e -> { order.add("teleport"); e.to(new Location(OVERWORLD, 3.5, -50, 3.5)); });
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.entity.EntityPortalExitEvent.class, e -> order.add("exit"));
        var a = changeDimensionEvents((Entity) spawned[2], null, false);
        assertEquals(List.of("teleport"), order, "포털 처리 중이 아니면 EntityPortalExitEvent 없음");
        assertNotNull(a);
        assertEquals(NETHER, a.dimension(), "Paper: portalProcess 가 null 이면 바꾼 to 를 버린다(취소·null 만 효과)");
        assertEquals(40.5, a.x(), 1e-9);
    }

    @Test
    void portalExitEventChangesTheArrivalAndVelocity() throws Exception {
        Object[] spawned = spawnCow();
        List<String> order = new CopyOnWriteArrayList<>();
        host.eventsImpl().subscribe(plugin, EntityTeleportEvent.class, e -> order.add("teleport:" + e.gateway()));
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.entity.EntityPortalExitEvent.class, e -> {
            order.add("exit");
            assertEquals(0.25, e.after().y(), 1e-9, "after = 전이 속도");
            assertEquals(40.5, e.to().x(), 1e-9);
            e.to(new Location(NETHER, 12.5, -50, 12.5, 5f, 0f));
            e.after(new org.domi.furnace.api.math.Vector3d(0, 1, 0));
        });
        var a = changeDimensionEvents((Entity) spawned[2], new org.domi.furnace.server.block.Pos(8, -50, 8));
        assertEquals(List.of("teleport:BlockPos[x=8, y=-50, z=8]", "exit"), order, "Paper 순서: (관문) EntityTeleportEvent 뒤 EntityPortalExitEvent");
        assertEquals(12.5, a.x(), 1e-9);
        assertEquals(5f, a.yaw());
        assertEquals(1.0, a.vy(), 1e-9);
    }

    @Test
    void cancelledPortalExitKeepsTheDestinationAndStillMoves() throws Exception {
        Object[] spawned = spawnCow();
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.entity.EntityPortalExitEvent.class, e -> {
            e.to(new Location(NETHER, 12.5, -50, 12.5));
            e.after(new org.domi.furnace.api.math.Vector3d(0, 1, 0));
            e.setCancelled(true);
        });
        var a = changeDimensionEvents((Entity) spawned[2], null);
        assertNotNull(a, "EntityPortalExitEvent 를 취소해도 이동은 막지 않는다(Paper)");
        assertEquals(40.5, a.x(), 1e-9, "취소하면 바꾼 도착 위치를 버린다");
        assertEquals(0.25, a.vy(), 1e-9, "바꾼 속도도 버린다");
    }

    @Test
    void cancelledChangeDimensionTeleportEventStopsBeforeTheExitEvent() throws Exception {
        Object[] spawned = spawnCow();
        List<String> order = new CopyOnWriteArrayList<>();
        host.eventsImpl().subscribe(plugin, EntityTeleportEvent.class, e -> { order.add("teleport"); e.setCancelled(true); });
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.entity.EntityPortalExitEvent.class, e -> order.add("exit"));
        assertNull(changeDimensionEvents((Entity) spawned[2], null));
        assertEquals(List.of("teleport"), order);
    }
}
