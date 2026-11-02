// 플러그인 API 1단계: 월드 블록 접근(비동기·소유 스레드 위반 거부)·엔티티 핸들·ItemStack 변환·서버 발화 지점(취소 포함).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
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
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new EmbeddedChannel());
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
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new EmbeddedChannel());
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
}
