// T-040b: EntityChangeBlockEvent 발화 지점(떨어지는 블록·미리 취소된 게임 규칙 자리·문 부수기)과 UUID 로 엔티티 찾기.
// 구독자가 없으면 예전(바닐라) 동작 그대로인지도 같이 본다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.event.entity.EntityBreakDoorEvent;
import org.domi.furnace.api.event.entity.EntityChangeBlockEvent;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.FallingBlockEntity;
import org.domi.furnace.server.entity.Sheep;
import org.domi.furnace.server.entity.Zombie;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginApiEntityChangeBlockTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final Blocks BL = Blocks.get();
    private static final int AIR = B.defaultState("minecraft:air"), STONE = B.defaultState("minecraft:stone"), SAND = B.defaultState("minecraft:sand");

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
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

    private <T> T at(java.util.function.Function<Region, T> f) throws Exception {
        return core.world().submitAtChunk(C00, f).get(30, TimeUnit.SECONDS);
    }

    private <E extends org.domi.furnace.api.event.Event> void on(Class<E> type, java.util.function.Consumer<? super E> h) {
        host.eventsImpl().subscribe(plugin, type, h);
    }

    // ------------------------------------------------------------------ 떨어지는 블록

    @Test
    void fallingBlockStartCancelKeepsBlockAndAddsNoEntity() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(EntityChangeBlockEvent.class, e -> {
            log.add(e.entity().type() + " " + e.oldData().type() + "->" + e.newData().type() + "@" + e.pos());
            e.setCancelled(true);
        });
        Pos p = new Pos(4, -45, 4);
        String[] r = at(rg -> {
            var l = rg.level();
            l.setBlock(p, SAND, 2);
            var e = FallingBlockEntity.fall(l, p, SAND);
            return new String[] {BL.describe(l.getBlockState(p)), String.valueOf(l.entities().contains(e))};
        });
        assertEquals("minecraft:sand", r[0], "막으면 칸을 그대로 둔다");
        assertEquals("false", r[1], "막으면 엔티티를 월드에 넣지 않는다");
        assertEquals(List.of("minecraft:falling_block minecraft:sand->minecraft:air@" + new BlockPos(4, -45, 4)), log);
    }

    @Test
    void fallingBlockLandingCancelDiscardsWithoutPlacing() throws Exception {
        List<BlockPos> asked = new CopyOnWriteArrayList<>();
        on(EntityChangeBlockEvent.class, e -> {
            asked.add(e.pos());
            if (e.newData().type().equals("minecraft:sand")) e.setCancelled(true);   // 내려앉기만 막는다
        });
        Pos top = new Pos(5, -45, 5), floor = new Pos(5, -50, 5);
        String[] r = at(rg -> {
            var l = rg.level();
            l.setBlock(floor, STONE, 2);
            l.setBlock(top, SAND, 2);
            var e = FallingBlockEntity.fall(l, top, SAND);
            for (int i = 0; i < 200 && !e.isRemoved(); i++) e.tick();
            return new String[] {BL.describe(l.getBlockState(top)), BL.describe(l.getBlockState(floor.above())), String.valueOf(e.isRemoved())};
        });
        assertEquals("minecraft:air", r[0], "떨어지기 시작은 허락됐다");
        assertEquals("minecraft:air", r[1], "내려앉기를 막으면 놓지 않는다");
        assertEquals("true", r[2], "엔티티는 사라진다(Paper)");
        assertEquals(List.of(new BlockPos(5, -45, 5), new BlockPos(5, -49, 5)), asked);
    }

    @Test
    void fallingBlockWithoutSubscriberLandsAsVanilla() throws Exception {
        Pos top = new Pos(6, -45, 6), floor = new Pos(6, -50, 6);
        String landed = at(rg -> {
            var l = rg.level();
            l.setBlock(floor, STONE, 2);
            l.setBlock(top, SAND, 2);
            var e = FallingBlockEntity.fall(l, top, SAND);
            for (int i = 0; i < 200 && !e.isRemoved(); i++) e.tick();
            return BL.describe(l.getBlockState(floor.above()));
        });
        assertEquals("minecraft:sand", landed);
    }

    // ------------------------------------------------------------------ 미리 취소(게임 규칙) 자리

    @Test
    void preCancelledAskWithoutSubscriberDoesNotReadAndKeepsRuleResult() throws Exception {
        boolean[] r = at(rg -> {
            var l = rg.level();
            var sheep = new Sheep(l, l.nextEntityId());
            sheep.moveTo(1.5, -50, 1.5, 0, 0);
            Pos p = new Pos(1, -51, 1);
            return new boolean[] {B.entityChange(l, sheep, p, AIR, true), B.entityChange(l, sheep, p, AIR, false), l.gameEvents().wantsEntityChangeBlock()};
        });
        assertFalse(r[0]);
        assertTrue(r[1]);
        assertFalse(r[2]);
    }

    @Test
    void preCancelledAskShowsCancelledAndHandlerCanAllow() throws Exception {
        List<Boolean> saw = new CopyOnWriteArrayList<>();
        on(EntityChangeBlockEvent.class, e -> {
            saw.add(e.isCancelled());
            e.setCancelled(false);
        });
        String[] r = at(rg -> {
            var l = rg.level();
            Pos p = new Pos(2, -50, 2);
            l.setBlock(p, B.defaultState("minecraft:grass_block"), 2);
            var sheep = new Sheep(l, l.nextEntityId());
            sheep.moveTo(2.5, -49, 2.5, 0, 0);
            l.addFreshEntity(sheep);
            boolean allowed = B.entityChange(l, sheep, p, B.defaultState("minecraft:dirt"), true);
            return new String[] {String.valueOf(allowed), BL.describe(l.getBlockState(p))};
        });
        assertEquals(List.of(true), saw);
        assertEquals("true", r[0], "처리기가 풀면 허락");
        assertTrue(r[1].startsWith("minecraft:grass_block"), "묻기만 하고 바꾸지 않는다: " + r[1]);
    }

    // ------------------------------------------------------------------ 문 부수기

    @Test
    void breakDoorReachesChangeBlockSubscriberAndCancels() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(EntityChangeBlockEvent.class, e -> {
            log.add(e.getClass().getSimpleName() + ":" + e.newData().type());
            e.setCancelled(true);
        });
        boolean allowed = at(rg -> {
            var l = rg.level();
            Pos p = new Pos(3, -50, 3);
            l.setBlock(p, B.defaultState("minecraft:oak_door"), 2);
            var z = new Zombie(l, l.nextEntityId());
            z.moveTo(3.5, -50, 4.5, 0, 0);
            l.addFreshEntity(z);
            return l.gameEvents().entityBreakDoor(l.dimension(), z, p, l.getBlockState(p), AIR);
        });
        assertFalse(allowed);
        assertEquals(List.of("EntityBreakDoorEvent:minecraft:air"), log, "ECB 구독만 있어도 하위 이벤트가 켜지고 닿는다");
    }

    @Test
    void breakDoorOnlySubscriberDoesNotTurnOnGenericChange() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(EntityBreakDoorEvent.class, e -> log.add("door"));
        boolean[] r = at(rg -> {
            var l = rg.level();
            Pos p = new Pos(3, -50, 3);
            var z = new Zombie(l, l.nextEntityId());
            z.moveTo(3.5, -50, 4.5, 0, 0);
            l.addFreshEntity(z);
            return new boolean[] {l.gameEvents().wantsEntityChangeBlock(), l.gameEvents().entityBreakDoor(l.dimension(), z, p, l.getBlockState(p), AIR)};
        });
        assertFalse(r[0]);
        assertTrue(r[1]);
        assertEquals(List.of("door"), log);
    }

    // ------------------------------------------------------------------ 밭 밟기(검토 R1)

    /** 좀비가 밭에 떨어진다 — 피해량과 밭이 흙이 됐는지. isLiving 은 기존 버그(R2, 별도 PR)를 피해 시험 안에서만 참으로 둔다. */
    private String[] fallOnFarmland() throws Exception {
        return at(rg -> {
            var l = rg.level();
            Pos p = new Pos(9, -51, 9);
            int farm = B.defaultState("minecraft:farmland");
            l.setBlock(p, farm, 2);
            var z = new Zombie(l, l.nextEntityId()) { @Override public boolean isLiving() { return true; } };
            z.moveTo(9.5, -50, 9.5, 0, 0);
            l.addFreshEntity(z);
            float before = z.health();
            B.behavior(farm).fallOn(l, farm, p, z, 10.0F);
            return new String[] {String.valueOf(before - z.health()), BL.describe(l.getBlockState(p))};
        });
    }

    @Test
    void farmlandTrampleCancelKeepsFallDamage() throws Exception {
        String[] free = fallOnFarmland();
        assertTrue(Float.parseFloat(free[0]) > 0, "바닐라: 밭에 떨어지면 낙하 피해");
        assertEquals("minecraft:dirt", free[1]);
        List<String> asked = new CopyOnWriteArrayList<>();
        on(EntityChangeBlockEvent.class, e -> {
            asked.add(e.newData().type());
            e.setCancelled(true);
        });
        String[] blocked = fallOnFarmland();
        assertEquals(List.of("minecraft:dirt"), asked);
        assertEquals(free[0], blocked[0], "막아도 낙하 피해는 같다(CraftBukkit 이 super.fallOn 을 맨 위로 옮겼다)");
        assertTrue(blocked[1].startsWith("minecraft:farmland"), "막으면 밭 그대로: " + blocked[1]);
    }

    // ------------------------------------------------------------------ UUID 로 찾기

    @Test
    void entityByUuidFollowsOwnerRulesAndRemoval() throws Exception {
        var w = host.world("minecraft:overworld");
        Object[] made = at(rg -> {
            var l = rg.level();
            var sheep = new Sheep(l, l.nextEntityId());
            sheep.moveTo(7.5, -50, 7.5, 0, 0);
            l.addFreshEntity(sheep);
            var here = w.entityByUuidNow(sheep.uuid());
            return new Object[] {sheep.uuid(), sheep.id(), here.map(h -> h.type()).orElse("none")};
        });
        UUID id = (UUID) made[0];
        assertEquals("minecraft:sheep", made[2], "소유 region 에서는 곧바로 찾는다");
        assertEquals((int) made[1], w.entityIdByUuid(id).orElseThrow(), "id 는 어느 스레드에서나");
        assertThrows(ThreadOwnershipException.class, () -> w.entityByUuidNow(id), "소유 스레드가 아니면 Now 판은 던진다");
        Optional<org.domi.furnace.api.entity.Entity> async = w.entityByUuid(id).get(30, TimeUnit.SECONDS);
        assertEquals(id, async.orElseThrow().uniqueId());

        UUID none = UUID.randomUUID();
        assertTrue(w.entityByUuid(none).get(30, TimeUnit.SECONDS).isEmpty());
        assertTrue(w.entityIdByUuid(none).isEmpty());
        assertTrue(at(rg -> w.entityByUuidNow(none)).isEmpty());

        at(rg -> {
            ((org.domi.furnace.server.entity.Entity) rg.level().entities().get((int) made[1])).discard();
            return null;
        });
        assertTrue(w.entityIdByUuid(id).isEmpty(), "사라지면 못 찾는다");
        assertTrue(w.entityByUuid(id).get(30, TimeUnit.SECONDS).isEmpty());
        assertTrue(at(rg -> w.entityByUuidNow(id)).isEmpty());
        assertNull(core.world().entityUuidIndex().get(id), "색인에서도 빠진다");
    }

    private static final ChunkPos FAR = new ChunkPos(160, 0);

    /** 바닐라 "UUID of added entity already exists": 레벨(월드) 전체에서 같은 UUID 를 거부한다 — region 이 달라도. */
    @Test
    void sameUuidInAnotherRegionIsRejectedAndKeepsTheLiveOne() throws Exception {
        core.world().submitAtChunk(FAR, r -> r.chunkAtBlocking(FAR)).get(30, TimeUnit.SECONDS);
        UUID id = UUID.randomUUID();
        int first = at(rg -> {
            var l = rg.level();
            var sheep = new Sheep(l, l.nextEntityId());
            sheep.setUuid(id);
            sheep.moveTo(7.5, -50, 7.5, 0, 0);
            assertTrue(l.addFreshEntity(sheep));
            return sheep.id();
        });
        boolean[] second = core.world().submitAtChunk(FAR, rg -> {
            var l = rg.level();
            var dup = new Sheep(l, l.nextEntityId());
            dup.setUuid(id);
            dup.moveTo(160 * 16 + 0.5, -50, 0.5, 0, 0);
            boolean added = l.addFreshEntity(dup);
            boolean passengers = l.addFreshEntityWithPassengers(dup);
            dup.discard();   // 못 들어간 쪽이 지워져도 살아 있는 쪽의 색인을 지우지 않는다
            return new boolean[] {added, passengers};
        }).get(30, TimeUnit.SECONDS);
        assertFalse(second[0], "다른 region 의 같은 UUID 는 거부");
        assertFalse(second[1], "승객 묶음 경로도 거부");
        var held = core.world().entityUuidIndex().get(id);
        assertNotNull(held, "살아 있는 쪽이 색인에 그대로");
        assertEquals(first, held.id());
        assertEquals(first, host.world("minecraft:overworld").entityIdByUuid(id).orElseThrow());

        // 살아 있는 쪽이 지워지면 같은 UUID 를 다시 받을 수 있다
        at(rg -> {
            ((org.domi.furnace.server.entity.Entity) rg.level().entities().get(first)).discard();
            return null;
        });
        assertNull(core.world().entityUuidIndex().get(id));
        boolean again = core.world().submitAtChunk(FAR, rg -> {
            var l = rg.level();
            var sheep = new Sheep(l, l.nextEntityId());
            sheep.setUuid(id);
            sheep.moveTo(160 * 16 + 0.5, -50, 0.5, 0, 0);
            return l.addFreshEntity(sheep);
        }).get(30, TimeUnit.SECONDS);
        assertTrue(again);
        assertNotNull(core.world().entityUuidIndex().get(id));
    }

    /** 검토 R1: 이미 살아 있는 같은 객체를 다른 region 이 addFreshEntity 로 다시 넣으려 해도 거부(넘기기 adoptEntity 만 같은 객체를 받는다). */
    @Test
    void sameLiveObjectCannotBeAddedFreshFromAnotherRegion() throws Exception {
        core.world().submitAtChunk(FAR, r -> r.chunkAtBlocking(FAR)).get(30, TimeUnit.SECONDS);
        Sheep sheep = at(rg -> {
            var l = rg.level();
            var s = new Sheep(l, l.nextEntityId());
            s.moveTo(7.5, -50, 7.5, 0, 0);
            assertTrue(l.addFreshEntity(s));
            return s;
        });
        boolean[] r = core.world().submitAtChunk(FAR, rg -> new boolean[] {rg.level().addFreshEntity(sheep), rg.level().entities().contains(sheep)})
                .get(30, TimeUnit.SECONDS);
        assertFalse(r[0], "바닐라 knownUuids: 이미 있는 UUID 는 거부");
        assertFalse(r[1], "다른 저장소에 들어가지 않는다");
        assertSame(sheep, core.world().entityUuidIndex().get(sheep.uuid()));
    }

    /** 검토 R2: 스폰 이벤트 처리기 안에서는 아직 레벨에 없다 — UUID 로 id 를 찾아도 비어 있다(Paper 와 같음). */
    @Test
    void uuidLookupInsideSpawnEventIsEmpty() throws Exception {
        UUID id = UUID.randomUUID();
        var seen = new java.util.concurrent.atomic.AtomicReference<String>("not fired");
        on(org.domi.furnace.api.event.entity.EntitySpawnEvent.class,
                e -> seen.set(String.valueOf(host.world("minecraft:overworld").entityIdByUuid(id))));
        at(rg -> {
            var l = rg.level();
            var s = new Sheep(l, l.nextEntityId());
            s.setUuid(id);
            s.moveTo(7.5, -50, 7.5, 0, 0);
            return l.addFreshEntityWithPassengers(s);
        });
        assertEquals("OptionalInt.empty", seen.get());
        assertTrue(host.world("minecraft:overworld").entityIdByUuid(id).isPresent(), "이벤트가 끝나면 찾는다");
    }

    /** 검토 R3: 승객 묶음은 하나라도 UUID 를 못 잡으면 전부 놓는다 — 앞쪽만 들어가거나 색인에 남지 않는다. */
    @Test
    void passengerBundleIsAllOrNothingAcrossRegions() throws Exception {
        core.world().submitAtChunk(FAR, r -> r.chunkAtBlocking(FAR)).get(30, TimeUnit.SECONDS);
        UUID rider = UUID.randomUUID();
        core.world().submitAtChunk(FAR, rg -> {
            var l = rg.level();
            var held = new Sheep(l, l.nextEntityId());
            held.setUuid(rider);
            held.moveTo(160 * 16 + 0.5, -50, 0.5, 0, 0);
            return l.addFreshEntity(held);
        }).get(30, TimeUnit.SECONDS);
        UUID mountId = UUID.randomUUID();
        boolean added = at(rg -> {
            var l = rg.level();
            var mount = new Sheep(l, l.nextEntityId());
            mount.setUuid(mountId);
            mount.moveTo(7.5, -50, 7.5, 0, 0);
            var dup = new Sheep(l, l.nextEntityId());
            dup.setUuid(rider);
            dup.moveTo(7.5, -50, 7.5, 0, 0);
            dup.startRiding(mount, true);
            return l.addFreshEntityWithPassengers(mount);
        });
        assertFalse(added, "겹치는 승객이 있으면 묶음 전체 거부");
        assertNull(core.world().entityUuidIndex().get(mountId), "앞쪽 탈것의 UUID 도 색인에 남지 않는다");
    }
}
