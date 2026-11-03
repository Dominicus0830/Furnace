// 플러그인 API 1단계: 월드 블록 접근(비동기·소유 스레드 위반 거부)·엔티티 핸들·ItemStack 변환·서버 발화 지점(취소 포함).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.event.entity.EntityDamageEvent;
import org.domi.furnace.api.event.entity.EntityDeathEvent;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.api.event.player.PlayerDropItemEvent;
import org.domi.furnace.api.event.player.PlayerMoveEvent;
import org.domi.furnace.api.event.player.PlayerPickupItemEvent;
import org.domi.furnace.api.event.world.ChunkLoadEvent;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityWorldApiTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

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
        plugin.setEnabled(true);   // 켜진 플러그인의 처리기만 불린다
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.eventsImpl().unregisterAll(plugin);
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private org.domi.furnace.api.world.World api() { return host.world("minecraft:overworld"); }

    // ------------------------------------------------------------------ 월드

    @Test
    void worldConstantsAndRules() {
        var w = api();
        assertEquals("minecraft:overworld", w.dimension());
        assertEquals("world", w.name());
        assertEquals(1234L, w.seed());
        assertEquals("true", w.gameRule("doDaylightCycle"));
        assertNull(w.gameRule("noSuchRule"));
        assertSame(w, host.world("minecraft:overworld"), "핸들은 캐시된다");
        assertNull(host.world("minecraft:nowhere"));
        assertEquals("minecraft:overworld", host.worlds().get(0).dimension());
    }

    @Test
    void blockAtAndSetBlockRoundTripThroughRegionThread() throws Exception {
        var w = api();
        BlockPos pos = new BlockPos(3, 10, 4);
        assertEquals(BlockData.AIR, w.blockAt(pos).get(10, TimeUnit.SECONDS).orElseThrow().data());
        assertTrue(w.setBlock(pos, BlockData.parse("oak_stairs[facing=north,half=top]")).get(10, TimeUnit.SECONDS));
        var b = w.blockAt(pos).get(10, TimeUnit.SECONDS).orElseThrow();
        assertEquals("minecraft:oak_stairs", b.type());
        assertEquals("north", b.data().properties().get("facing"));
        assertEquals("top", b.data().properties().get("half"));
        assertEquals("minecraft:overworld", b.world());
        assertTrue(w.setBlock(pos, BlockData.of("air")).get(10, TimeUnit.SECONDS));
        assertTrue(w.blockAt(pos).get().orElseThrow().data().isAir());
    }

    @Test
    void unknownBlockIsRejectedAndUnloadedChunkIsNotCreated() throws Exception {
        var w = api();
        var ex = assertThrows(ExecutionException.class, () -> w.setBlock(new BlockPos(1, 10, 1), BlockData.of("minecraft:not_a_block")).get());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        var badProp = assertThrows(ExecutionException.class, () -> w.setBlock(new BlockPos(1, 10, 1), BlockData.parse("oak_stairs[facing=up]")).get());
        assertInstanceOf(IllegalArgumentException.class, badProp.getCause(), "없는 속성값은 근사하지 않고 거부한다");
        BlockPos far = new BlockPos(16 * 500, 10, 16 * 500);
        int before = core.world().loadedChunkCount();
        assertEquals(Optional.empty(), w.blockAt(far).get(10, TimeUnit.SECONDS));
        assertFalse(w.setBlock(far, BlockData.of("stone")).get(10, TimeUnit.SECONDS));
        assertEquals(before, core.world().loadedChunkCount(), "읽기·쓰기가 청크를 만들면 안 된다");
        assertEquals(Optional.empty(), w.blockAt(new BlockPos(1, 5000, 1)).get(10, TimeUnit.SECONDS), "높이 밖");
    }

    @Test
    void blockNowIsRejectedOffOwnerThreadAndAllowedOnIt() throws Exception {
        var w = api();
        BlockPos pos = new BlockPos(1, 10, 1);
        assertThrows(ThreadOwnershipException.class, () -> w.blockNow(pos), "테스트(비소유) 스레드에서는 거부");
        assertTrue(w.scheduler(pos).submit(() -> w.blockNow(pos)).get(10, TimeUnit.SECONDS).isPresent(), "소유 region 스레드에서는 읽는다");
        // 다른 region 의 칸은 소유 스레드가 아니다
        BlockPos farOwned = new BlockPos(16 * 40, 10, 0);
        var ex = assertThrows(ExecutionException.class, () -> w.scheduler(pos).submit(() -> w.blockNow(farOwned)).get(10, TimeUnit.SECONDS));
        assertInstanceOf(ThreadOwnershipException.class, ex.getCause(), "region 안에서도 남의 칸을 동기로 읽을 수 없다");
    }

    // ------------------------------------------------------------------ 아이템 변환

    @Test
    void itemStackConvertsBothWaysWithoutNumericIds() {
        var items = core.world().itemRegistry();
        var api = new org.domi.furnace.api.item.ItemStack("diamond_sword", 1).withComponent("minecraft:damage", "5");
        var server = ApiConvert.item(api, items);
        assertEquals(1, server.count());
        var back = ApiConvert.item(server, items);
        assertEquals("minecraft:diamond_sword", back.type());
        assertEquals(1, back.amount());
        assertEquals(5, back.damage());
        assertTrue(ApiConvert.item(org.domi.furnace.server.entity.ItemStack.EMPTY, items).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ApiConvert.item(new org.domi.furnace.api.item.ItemStack("minecraft:no_item", 1), items));
    }

    // ------------------------------------------------------------------ 엔티티·이벤트

    @Test
    void spawnDamageDeathEventsAndCancellation() throws Exception {
        List<String> log = new ArrayList<>();
        var bus = host.eventsImpl();
        bus.subscribe(plugin, EntitySpawnEvent.class, e -> {
            log.add("spawn:" + e.entity().type());
            if (e.entity().customName() != null) e.setCancelled(true);
        });
        bus.subscribe(plugin, EntityDamageEvent.class, e -> {
            log.add("damage:" + e.cause() + ":" + e.damage());
            if (e.damage() >= 100f) e.setCancelled(true);
        });
        bus.subscribe(plugin, EntityDeathEvent.class, e -> log.add("death:" + e.entity().type() + ":" + (e.entity().health() > 0)));

        Object[] out = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -50, 8, 0, 0);
            boolean added = level.addFreshEntity(cow);
            Cow named = new Cow(level, level.nextEntityId());
            named.setCustomName(org.domi.furnace.api.text.Text.text("blocked"));
            boolean addedNamed = level.addFreshEntity(named);
            float h0 = cow.health();
            cow.hurt(org.domi.furnace.server.entity.Entity.Damage.GENERIC_KILL, 1000f);   // 취소(>=100)
            float h1 = cow.health();
            cow.kill();                                                                    // 취소 -> 살아 있다
            boolean aliveAfterCancel = !cow.isDeadOrDying();
            return new Object[] {added, addedNamed, h0 == h1, aliveAfterCancel};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(true, out[0]);
        assertEquals(false, out[1], "EntitySpawnEvent 취소 = 월드에 넣지 않는다");
        assertEquals(true, out[2], "EntityDamageEvent 취소 = 체력 그대로");
        assertEquals(true, out[3]);
        assertTrue(log.contains("spawn:minecraft:cow"));
        assertTrue(log.stream().anyMatch(s -> s.startsWith("damage:minecraft:generic_kill") || s.startsWith("damage:")), log.toString());

        // 피해 값을 바꾸면 이후 계산에 쓰인다 + 죽음 이벤트
        bus.unregisterAll(plugin);
        bus.subscribe(plugin, EntityDeathEvent.class, e -> log.add("death:" + e.entity().type()));
        bus.subscribe(plugin, EntityDamageEvent.class, e -> e.setDamage(0f));
        Object[] out2 = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -50, 8, 0, 0);
            level.addFreshEntity(cow);
            cow.hurt(new org.domi.furnace.server.entity.Entity.Damage("fall", false, false, false), 5f);
            boolean full = cow.health() == cow.maxHealth();
            bus.unregisterAll(plugin);
            bus.subscribe(plugin, EntityDeathEvent.class, e -> log.add("death:" + e.entity().type()));
            cow.kill();
            return new Object[] {full, cow.isDeadOrDying()};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(true, out2[0], "setDamage(0) 면 체력이 줄지 않는다");
        assertEquals(true, out2[1]);
        assertTrue(log.contains("death:minecraft:cow"), log.toString());
    }

    @Test
    void entityHandleMutationsRunOnOwningRegionAndRefreshSeesNewState() throws Exception {
        UUID[] id = new UUID[1];
        org.domi.furnace.api.entity.Entity[] handle = new org.domi.furnace.api.entity.Entity[1];
        core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -50, 8, 0, 0);
            level.addFreshEntity(cow);
            id[0] = cow.uuid();
            handle[0] = EntityHandle.of(host, cow);
            return null;
        }).get(30, TimeUnit.SECONDS);
        var living = assertInstanceOf(org.domi.furnace.api.entity.LivingEntity.class, handle[0]);
        assertEquals(id[0], living.uniqueId());
        assertEquals("minecraft:cow", living.type());
        assertEquals(10f, living.maxHealth());
        assertEquals(8.0, living.location().x());
        assertTrue(living.isValid());

        living.updateHealth(3f).get(10, TimeUnit.SECONDS);
        var fresh = (org.domi.furnace.api.entity.LivingEntity) living.refresh().get(10, TimeUnit.SECONDS);
        assertEquals(3f, fresh.health());
        assertEquals(10f, living.health(), "기존 핸들은 만든 시점 값 그대로(스냅샷)");

        assertTrue(living.teleport(new org.domi.furnace.api.world.Location("minecraft:overworld", 4, -50, 4)).get(10, TimeUnit.SECONDS));
        assertEquals(4.0, living.refresh().get(10, TimeUnit.SECONDS).location().x());
        assertFalse(living.teleport(new org.domi.furnace.api.world.Location("minecraft:the_nether", 4, 64, 4)).get(10, TimeUnit.SECONDS), "차원 이동은 지원하지 않는다");
        assertFalse(living.teleport(new org.domi.furnace.api.world.Location("minecraft:overworld", 16 * 40, -50, 4)).get(10, TimeUnit.SECONDS),
                "다른 region 으로 넘기는 이동은 거부(핸드오프는 다음 단계)");

        assertTrue(living.remove().get(10, TimeUnit.SECONDS));
        assertNull(living.refresh().get(10, TimeUnit.SECONDS), "사라진 엔티티는 null");
        assertFalse(living.remove().get(10, TimeUnit.SECONDS));
    }

    @Test
    void chunkLoadEventFiresOnOwningRegion() throws Exception {
        List<String> got = new java.util.concurrent.CopyOnWriteArrayList<>();
        host.eventsImpl().subscribe(plugin, ChunkLoadEvent.class, e -> got.add(e.world() + ":" + e.chunkX() + "," + e.chunkZ()));
        ChunkPos cp = new ChunkPos(2, 2);
        core.world().runAtChunk(cp, r -> r.loadChunk(cp));
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (got.isEmpty() && System.nanoTime() < end) Thread.sleep(20);
        assertEquals(List.of("minecraft:overworld:2,2"), got);
    }

    @Test
    void playerMoveDropAndPickupEventsCanCancel() throws Exception {
        var bus = host.eventsImpl();
        List<String> log = new ArrayList<>();
        bus.subscribe(plugin, PlayerDropItemEvent.class, e -> { log.add("drop:" + e.item().type()); e.setCancelled(true); });
        bus.subscribe(plugin, PlayerPickupItemEvent.class, e -> { log.add("pickup:" + e.item().type()); e.setCancelled(true); });
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        Object[] out = core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            var level = r.level();
            var stack = org.domi.furnace.server.block.B.stack("minecraft:dirt", 3);
            var dropped = r.playerDrop(p, stack, false, true);
            int inInv = 0;
            for (int s = 0; s < PlayerInventory.SIZE; s++) inInv += p.inventory().get(s).count();
            var item = new ItemEntity(level, level.nextEntityId(), 8.5, -59.5, 8.5, org.domi.furnace.server.block.B.stack("minecraft:stone", 2), 0, 0, 0);
            item.setNoPickUpDelay();
            level.addFreshEntity(item);
            item.playerTouch(p);
            return new Object[] {dropped, inInv, item.isRemoved()};
        }).get(30, TimeUnit.SECONDS);
        assertNull(out[0], "드롭 취소 = 바닥 아이템 없음");
        assertEquals(3, out[1], "취소하면 인벤토리로 돌아온다");
        assertEquals(false, out[2], "줍기 취소 = 아이템이 남는다");
        assertEquals(List.of("drop:minecraft:dirt", "pickup:minecraft:stone"), log);
    }

    @Test
    void playerMoveEventFiresForCheckedMoveAndCancelRestores() throws Exception {
        List<String> log = new ArrayList<>();
        host.eventsImpl().subscribe(plugin, PlayerMoveEvent.class, e -> {
            log.add(String.format("%.1f->%.1f", e.from().x(), e.to().x()));
            e.setCancelled(e.to().x() > 12);
        });
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        // processMoves 는 private — Region.tick 이 대기열을 비운다. 첫 샘플은 기준점(이벤트 없음)
        double[] anchorX = new double[1];
        core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            p.pushMove(8.5, -60, 8.5, true);
            p.pushMove(9.5, -60, 8.5, true);
            p.pushMove(13.5, -60, 8.5, true);
            r.tick();
            anchorX[0] = p.moveAnchor().x();
            return null;
        }).get(30, TimeUnit.SECONDS);
        assertEquals(List.of("8.5->9.5", "9.5->13.5"), log);
        assertEquals(9.5, anchorX[0], "취소된 이동은 기준점이 되지 않는다");
    }

    // ------------------------------------------------------------------ 후속 검토 반영

    @Test
    void passengerSpawnCancelIsAllOrNothingAndRestoresLevel() throws Exception {
        host.eventsImpl().subscribe(plugin, EntitySpawnEvent.class, e -> e.setCancelled(e.entity().customName() != null));
        Object[] out = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow vehicle = new Cow(level, level.nextEntityId());
            vehicle.moveTo(8, -50, 8, 0, 0);
            var rider = new org.domi.furnace.server.entity.Zombie(level, level.nextEntityId());
            rider.moveTo(8, -50, 8, 0, 0);
            rider.setCustomName(org.domi.furnace.api.text.Text.text("no"));
            rider.startRiding(vehicle, true);
            boolean cancelled = level.addFreshEntityWithPassengers(vehicle);
            boolean vehicleIn = level.entities().contains(vehicle);
            boolean riderIn = level.entities().contains(rider);
            rider.setCustomName(null);
            boolean ok = level.addFreshEntityWithPassengers(vehicle);
            return new Object[] {cancelled, vehicleIn, riderIn, ok, level.entities().contains(vehicle), level.entities().contains(rider)};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(false, out[0], "승객 하나라도 취소되면 전체 실패");
        assertEquals(false, out[1], "탈것도 들어가지 않는다");
        assertEquals(false, out[2]);
        assertEquals(true, out[3], "취소 사유가 사라지면 정상 추가");
        assertEquals(true, out[4]);
        assertEquals(true, out[5]);
    }

    @Test
    void cancelledMoveOnCheckedPathCorrectsOnceBypassingTeleportEvent() throws Exception {
        List<String> teleportEvents = new ArrayList<>();
        List<double[]> corrections = new java.util.concurrent.CopyOnWriteArrayList<>();
        int[] moveEvents = {0};
        host.eventsImpl().subscribe(plugin, PlayerMoveEvent.class, e -> { moveEvents[0]++; e.setCancelled(true); });
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.player.PlayerTeleportEvent.class, e -> { teleportEvents.add("tp"); e.setCancelled(true); });
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        p.teleporter(t -> { corrections.add(t); p.clearMoves(); });   // 연결이 하는 일: 보정을 보내고 대기열·hold 를 비운다
        Object[] out = core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            p.pushMove(8.5, -60, 8.5, true);   // 기준점
            p.pushCheckedMove(new org.domi.furnace.server.entity.PlayerMoveValidator.Check(true, false, 8.7, -60, 8.5, 0, 0), true);
            r.tick();
            double x1 = p.x();
            int c1 = corrections.size();
            r.tick();   // 이동 없음 — 보정이 되풀이되면 안 된다
            r.tick();
            return new Object[] {x1, c1, corrections.size(), p.moveRejectHold};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(8.5, (double) out[0], 1e-9, "취소하면 서버 위치도 이전 자리로 돌아간다(검증기가 옮겨 둔 값을 되돌림)");
        assertEquals(1, (int) out[1], "보정은 정확히 한 번");
        assertEquals(1, (int) out[2], "무한 보정 루프 없음");
        assertEquals(false, out[3], "hold 는 연결이 대기열을 비울 때 풀린다");
        assertEquals(1, moveEvents[0]);
        assertTrue(teleportEvents.isEmpty(), "보정은 PlayerTeleportEvent 를 거치지 않는다(취소돼도 사라지지 않는다)");
        assertEquals(5, corrections.get(0).length, "회전까지 담은 보정");
        assertEquals(8.5, corrections.get(0)[0], 1e-9);
    }

    @Test
    void futureGetOnServerThreadIsRejected() throws Exception {
        var w = api();
        BlockPos pos = new BlockPos(1, 10, 1);
        Object r = w.scheduler(pos).submit(() -> {
            var pending = w.blockAt(new BlockPos(2, 10, 2));   // 이 작업이 끝난 뒤에야 돈다 -> 아직 미완료
            try {
                pending.get();
                return "no-throw";
            } catch (IllegalStateException e) {
                return "rejected";
            } catch (Exception e) {
                return e.toString();
            }
        }).get(10, TimeUnit.SECONDS);
        assertEquals("rejected", r, "region 스레드에서 미완료 Future 를 get() 하면 데드락이므로 거부");
        assertTrue(w.blockAt(new BlockPos(2, 10, 2)).get(10, TimeUnit.SECONDS).isPresent(), "일반 스레드에서는 기다릴 수 있다");
    }

    @Test
    void playerInventorySetRunsOnOwnerAndCancelledDeathListenerExceptionIsIsolated() throws Exception {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        var inv = host.player(p).inventory();
        host.eventsImpl().subscribe(plugin, EntityDeathEvent.class, e -> { throw new IllegalStateException("리스너 고장"); });
        Object[] out = core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            r.tick();   // 소유 region 표시
            var f = inv.set(0, new org.domi.furnace.api.item.ItemStack("minecraft:dirt", 4));
            r.tick();
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -50, 8, 0, 0);
            level.addFreshEntity(cow);
            cow.kill();
            return new Object[] {f.isDone() && !f.isCompletedExceptionally(), p.inventory().get(36).count(), cow.isDeadOrDying()};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(true, out[0], "쓰기 Future 는 소유 스레드에서 완료");
        assertEquals(4, out[1]);
        assertEquals(true, out[2], "죽음 리스너 예외가 죽음 처리를 막지 않는다");
        assertEquals(4, inv.get(0).get(10, TimeUnit.SECONDS).amount());
        assertTrue(inv.set(99, org.domi.furnace.api.item.ItemStack.EMPTY).isCompletedExceptionally());
    }

    @Test
    void cancelledAirInteractResyncsHeldSlot() throws Exception {
        host.eventsImpl().subscribe(plugin, org.domi.furnace.api.event.player.PlayerInteractEvent.class, e -> e.setCancelled(true));
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", ch);
        PlayerInventory inv = new PlayerInventory();
        p.inventory(inv);
        inv.set(36, org.domi.furnace.server.block.B.stack("minecraft:dirt", 3));
        p.inventoryHost(new Player.InventoryHost() {
            public org.domi.furnace.server.entity.ItemStack slot(int w) { return inv.get(w); }
            public int selected() { return 0; }
            public void replace(int w, org.domi.furnace.server.entity.ItemStack expect, org.domi.furnace.server.entity.ItemStack now) { inv.replace(w, expect, now); }
            public int permissionLevel() { return 0; }
        });
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        core.world().submitAtChunk(C00, r -> {
            r.adopt(p);
            r.handleUseItem(p, 0, 1, 0f, 0f);
            return null;
        }).get(30, TimeUnit.SECONDS);
        ch.runPendingTasks();
        ch.flushOutbound();
        assertFalse(ch.outboundMessages().isEmpty(), "취소하면 손 슬롯을 다시 보내는 패킷이 나간다");
        assertEquals(3, inv.get(36).count(), "서버 아이템은 그대로");
    }
}
