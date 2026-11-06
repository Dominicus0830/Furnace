// 플러그인 API 3단계 셋째 묶음: 블록 번짐·형성·소멸, 생물 스폰 이유, 탈것 승하차, 양조. 마법 부여·모루·낚시·차원문·책은 봇 시험(tools/bot-test/round8).
// 구독자가 없으면 예전 동작 그대로인지도 같이 본다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.block.BlockFadeEvent;
import org.domi.furnace.api.event.block.BlockFormEvent;
import org.domi.furnace.api.event.block.BlockSpreadEvent;
import org.domi.furnace.api.event.entity.CreatureSpawnEvent;
import org.domi.furnace.api.event.entity.EntityDismountEvent;
import org.domi.furnace.api.event.entity.EntityMountEvent;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.api.event.inventory.BrewEvent;
import org.domi.furnace.api.event.inventory.BrewingStandFuelEvent;
import org.domi.furnace.api.event.vehicle.VehicleEnterEvent;
import org.domi.furnace.api.event.vehicle.VehicleExitEvent;
import org.domi.furnace.server.entity.Boat;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BrewingStandBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
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

class PluginApiFormEventsTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final Blocks BL = Blocks.get();
    private static final int AIR = B.defaultState("minecraft:air"), STONE = B.defaultState("minecraft:stone");

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

    private static String desc(ChunkLevel l, Pos p) { return BL.describe(l.getBlockState(p)); }

    private static void step(ChunkLevel l, int n) {
        for (int i = 0; i < n; i++) {
            l.runScheduledTicks();
            l.runBlockEvents();
            l.tickBlockEntities(k -> true);
        }
    }

    // ------------------------------------------------------------------ 소멸(Fade)

    @Test
    void coralBlockFadeCancelKeepsLiveCoral() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        boolean[] cancel = {true};
        on(BlockFadeEvent.class, e -> {
            log.add(e.oldData().type() + "->" + e.newData().type());
            e.setCancelled(cancel[0]);
        });
        Pos p = new Pos(8, -50, 8);
        String kept = at(r -> {
            var l = r.level();
            l.setBlock(p, B.defaultState("minecraft:tube_coral_block"), 3);
            B.behavior(l.getBlockState(p)).tick(l.getBlockState(p), l, p, l.random());
            return desc(l, p);
        });
        assertEquals("minecraft:tube_coral_block", kept, "취소하면 죽지 않는다");
        assertEquals(List.of("minecraft:tube_coral_block->minecraft:dead_tube_coral_block"), log);
        cancel[0] = false;
        String dead = at(r -> {
            var l = r.level();
            B.behavior(l.getBlockState(p)).tick(l.getBlockState(p), l, p, l.random());
            return desc(l, p);
        });
        assertEquals("minecraft:dead_tube_coral_block", dead);
    }

    @Test
    void grassUnderBlockFadesToDirtAndFarmlandDries() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        boolean[] cancel = {true};
        on(BlockFadeEvent.class, e -> {
            log.add(e.oldData().type() + "->" + e.newData().type() + "@" + e.pos());
            e.setCancelled(cancel[0]);
        });
        Pos g = new Pos(8, -50, 8), f = new Pos(12, -50, 12);
        String[] res = at(r -> {
            var l = r.level();
            l.setBlock(g.above(), STONE, 3);
            l.setBlock(g, B.defaultState("minecraft:grass_block"), 3);
            l.setBlock(f, B.defaultState("minecraft:farmland"), 3);
            var gs = l.getBlockState(g);
            B.behavior(gs).randomTick(gs, l, g, l.random());
            var fs = l.getBlockState(f);
            B.behavior(fs).randomTick(fs, l, f, l.random());
            return new String[] {desc(l, g), desc(l, f)};
        });
        assertTrue(res[0].startsWith("minecraft:grass_block"), res[0]);
        assertTrue(res[1].startsWith("minecraft:farmland"), res[1]);
        assertEquals(2, log.size(), log.toString());
        assertTrue(log.contains("minecraft:grass_block->minecraft:dirt@" + new BlockPos(8, -50, 8)));
        assertTrue(log.contains("minecraft:farmland->minecraft:dirt@" + new BlockPos(12, -50, 12)));
        cancel[0] = false;
        String[] after = at(r -> {
            var l = r.level();
            var gs = l.getBlockState(g);
            B.behavior(gs).randomTick(gs, l, g, l.random());
            var fs = l.getBlockState(f);
            B.behavior(fs).randomTick(fs, l, f, l.random());
            return new String[] {desc(l, g), desc(l, f)};
        });
        assertEquals("minecraft:dirt", after[0]);
        assertEquals("minecraft:dirt", after[1]);
    }

    // ------------------------------------------------------------------ 형성(Form)·번짐(Spread)

    @Test
    void lavaMeetingWaterFormsObsidianUnlessCancelled() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        boolean[] cancel = {true};
        on(BlockFormEvent.class, e -> {
            log.add(e.oldData().type() + "->" + e.newData().type() + "@" + e.pos());
            e.setCancelled(cancel[0]);
        });
        Pos lava = new Pos(8, -55, 8), water = lava.east();
        String kept = at(r -> {
            var l = r.level();
            l.setBlock(lava, B.defaultState("minecraft:lava"), 3);
            l.setBlock(water, B.defaultState("minecraft:water"), 3);
            step(l, 40);
            return desc(l, lava);
        });
        assertFalse(kept.startsWith("minecraft:obsidian") || kept.startsWith("minecraft:cobblestone"), "취소하면 굳지 않는다: " + kept);
        assertFalse(log.isEmpty());
        assertTrue(log.get(0).contains("->minecraft:obsidian@" + new BlockPos(8, -55, 8)), log.toString());
        cancel[0] = false;
        String formed = at(r -> {
            var l = r.level();
            l.setBlock(lava, B.defaultState("minecraft:lava"), 3);
            l.setBlock(water, B.defaultState("minecraft:water"), 3);
            step(l, 40);
            return desc(l, lava);
        });
        assertEquals("minecraft:obsidian", formed);
    }

    @Test
    void mushroomSpreadEventHasSourceAndCancelBlocksIt() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(BlockSpreadEvent.class, e -> {
            log.add(e.newData().type() + "@" + e.pos() + "<" + e.source());
            e.setCancelled(true);
        });
        Pos m = new Pos(8, -50, 8);
        int[] count = at(r -> {
            var l = r.level();
            for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) l.setBlock(new Pos(7 + x, -51, 7 + z), B.defaultState("minecraft:mycelium"), 3);
            l.setBlock(m, B.defaultState("minecraft:red_mushroom"), 3);
            int n = 0;
            for (int i = 0; i < 4000; i++) {
                var s = l.getBlockState(m);
                B.behavior(s).randomTick(s, l, m, l.random());
            }
            for (int x = 4; x < 16; x++) for (int z = 4; z < 12; z++) for (int y = -52; y <= -49; y++) {
                if (desc(l, new Pos(x, y, z)).startsWith("minecraft:red_mushroom")) n++;
            }
            return new int[] {n};
        });
        assertFalse(log.isEmpty(), "번짐 시도가 이벤트로 온다");
        assertTrue(log.get(0).contains("<" + new BlockPos(8, -50, 8)), log.toString());
        assertEquals(1, count[0], "취소하면 버섯이 늘지 않는다");
    }

    // ------------------------------------------------------------------ 생물 스폰 이유

    private static long cowCount(ChunkLevel level) {
        long n = 0;
        for (Entity e : level.entities().all()) if (e instanceof Cow) n++;
        return n;
    }

    @Test
    void creatureSpawnCarriesReasonAndCancelSkips() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        List<String> parent = new CopyOnWriteArrayList<>();
        on(CreatureSpawnEvent.class, e -> {
            log.add(e.entity().type() + ":" + e.reason());
            if (e.reason() == CreatureSpawnEvent.SpawnReason.BREEDING) e.setCancelled(true);
        });
        on(EntitySpawnEvent.class, e -> parent.add(e.getClass().getSimpleName()));
        Object[] res = at(r -> {
            var level = r.level();
            Cow a = new Cow(level, level.nextEntityId());
            a.moveTo(8, -60, 8, 0, 0);
            Cow b = new Cow(level, level.nextEntityId());
            b.moveTo(9, -60, 8, 0, 0);
            boolean plain = level.addFreshEntity(a);
            level.addFreshEntity(b.spawnReason("SPAWNER"));
            a.setInLove(null);
            b.setInLove(null);
            long cows = cowCount(level);
            a.spawnChildFromBreeding(level, b);
            return new Object[] {plain, cowCount(level) - cows};
        });
        assertEquals(true, res[0]);
        assertEquals(0L, res[1], "번식 새끼 스폰을 취소했다");
        assertTrue(log.contains("minecraft:cow:DEFAULT"), log.toString());
        assertTrue(log.contains("minecraft:cow:SPAWNER"), log.toString());
        assertTrue(log.contains("minecraft:cow:BREEDING"), log.toString());
        assertTrue(parent.contains("CreatureSpawnEvent"), "EntitySpawnEvent 구독자도 받는다: " + parent);
    }

    // ------------------------------------------------------------------ 탈것

    @Test
    void vehicleAndMountEventsFireInOrderAndCancelStopsRiding() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        boolean[] cancelEnter = {true}, cancelExit = {true};
        on(VehicleEnterEvent.class, e -> {
            log.add("enter:" + e.entered().type() + ">" + e.vehicle().type());
            e.setCancelled(cancelEnter[0]);
        });
        on(EntityMountEvent.class, e -> log.add("mount:" + e.entity().type() + ">" + e.mount().type()));
        on(VehicleExitEvent.class, e -> {
            log.add("exit:" + e.exited().type() + "<" + e.vehicle().type() + ":" + e.isCancellable());
            e.setCancelled(cancelExit[0]);
        });
        on(EntityDismountEvent.class, e -> log.add("dismount:" + e.entity().type() + "<" + e.dismounted().type()));
        boolean[] res = at(r -> {
            var level = r.level();
            Boat boat = new Boat(level, level.nextEntityId());
            boat.moveTo(8, -60, 8, 0, 0);
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -60, 8, 0, 0);
            level.addFreshEntity(boat);
            level.addFreshEntity(cow);
            boolean first = cow.startRiding(boat);
            cancelEnter[0] = false;
            boolean second = cow.startRiding(boat);
            cow.stopRiding();   // 취소됨
            boolean stillRiding = cow.vehicle() == boat;
            cancelExit[0] = false;
            cow.stopRiding();
            boolean left = cow.vehicle() == null;
            cow.startRiding(boat, true);
            cancelExit[0] = true;
            boat.ejectPassengers();   // 강제: 취소해도 내린다
            return new boolean[] {first, second, stillRiding, left, cow.vehicle() == null};
        });
        assertFalse(res[0], "VehicleEnterEvent 취소면 타지 못한다");
        assertTrue(res[1]);
        assertTrue(res[2], "VehicleExitEvent 취소면 계속 탄다");
        assertTrue(res[3]);
        assertTrue(res[4], "강제 하차는 취소해도 내린다");
        assertEquals(List.of("enter:minecraft:cow>minecraft:boat",
                "enter:minecraft:cow>minecraft:boat", "mount:minecraft:cow>minecraft:boat",
                "exit:minecraft:cow<minecraft:boat:true",
                "exit:minecraft:cow<minecraft:boat:true", "dismount:minecraft:cow<minecraft:boat",
                "enter:minecraft:cow>minecraft:boat", "mount:minecraft:cow>minecraft:boat",
                "exit:minecraft:cow<minecraft:boat:false", "dismount:minecraft:cow<minecraft:boat"), log);
    }

    /** 이벤트 취소 계약: EntityMountEvent·EntityDismountEvent 도 VehicleEnter·Exit 처럼 취소하면 탄 상태가 그대로다. */
    @Test
    void entityMountAndDismountCancelKeepRidingState() throws Exception {
        boolean[] cancel = {true};
        on(EntityMountEvent.class, e -> e.setCancelled(cancel[0]));
        on(EntityDismountEvent.class, e -> e.setCancelled(cancel[0]));
        boolean[] res = at(r -> {
            var level = r.level();
            Boat boat = new Boat(level, level.nextEntityId());
            boat.moveTo(8, -60, 8, 0, 0);
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -60, 8, 0, 0);
            level.addFreshEntity(boat);
            level.addFreshEntity(cow);
            boolean blocked = !cow.startRiding(boat) && cow.vehicle() == null;
            cancel[0] = false;
            boolean mounted = cow.startRiding(boat) && cow.vehicle() == boat;
            cancel[0] = true;
            cow.stopRiding();
            boolean stays = cow.vehicle() == boat;
            cancel[0] = false;
            cow.stopRiding();
            return new boolean[] {blocked, mounted, stays, cow.vehicle() == null};
        });
        assertTrue(res[0], "EntityMountEvent 취소면 타지 못한다");
        assertTrue(res[1], "대조: 허용하면 탄다");
        assertTrue(res[2], "EntityDismountEvent 취소면 계속 탄다");
        assertTrue(res[3], "대조: 허용하면 내린다");
    }

    /** 이벤트 취소 계약: BrewingStandFuelEvent 를 취소하면 블레이즈 가루도 연료도 그대로다. */
    @Test
    void brewingFuelCancelKeepsBlazePowderAndFuel() throws Exception {
        boolean[] cancel = {true};
        on(BrewingStandFuelEvent.class, e -> e.setCancelled(cancel[0]));
        Pos p = new Pos(8, -50, 8);
        int[] res = at(r -> {
            var l = r.level();
            l.setBlock(p, B.defaultState("minecraft:brewing_stand"), 3);
            var be = (BrewingStandBlockEntity) l.getBlockEntity(p);
            be.items[0] = org.domi.furnace.server.item.ItemTools.with(B.stack("minecraft:potion", 1), "minecraft:potion_contents", java.util.Map.of("potion", "minecraft:water"));
            be.items[3] = B.stack("minecraft:nether_wart", 2);
            be.items[4] = B.stack("minecraft:blaze_powder", 3);
            for (int i = 0; i < 5; i++) be.tick(l);
            int cancelledFuel = be.fuel, cancelledPowder = be.items[4].count();
            cancel[0] = false;
            for (int i = 0; i < 5; i++) be.tick(l);
            return new int[] {cancelledFuel, cancelledPowder, be.fuel, be.items[4].count()};
        });
        assertEquals(0, res[0], "취소하면 연료가 차지 않는다");
        assertEquals(3, res[1], "취소하면 블레이즈 가루가 그대로");
        assertEquals(19, res[2], "대조: 허용하면 연료가 20 으로 차고 양조를 시작하며 하나 줄었다");
        assertEquals(2, res[3]);
    }

    // ------------------------------------------------------------------ 양조

    @Test
    void brewingFuelAndBrewEventsCanBeCancelledOrTuned() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        boolean[] cancelBrew = {true};
        on(BrewingStandFuelEvent.class, e -> {
            log.add("fuel:" + e.fuel().type() + ":" + e.fuelPower());
            e.setFuelPower(7);
            e.setConsuming(false);
        });
        on(BrewEvent.class, e -> {
            log.add("brew:" + e.contents().get(3).type() + ":fuel=" + e.fuelLevel());
            e.setCancelled(cancelBrew[0]);
        });
        Pos p = new Pos(8, -50, 8);
        Object[] res = at(r -> {
            var l = r.level();
            l.setBlock(p, B.defaultState("minecraft:brewing_stand"), 3);
            var be = (BrewingStandBlockEntity) l.getBlockEntity(p);
            be.items[0] = org.domi.furnace.server.item.ItemTools.with(B.stack("minecraft:potion", 1), "minecraft:potion_contents", java.util.Map.of("potion", "minecraft:water"));
            be.items[3] = B.stack("minecraft:nether_wart", 2);
            be.items[4] = B.stack("minecraft:blaze_powder", 3);
            for (int i = 0; i < 401; i++) be.tick(l);
            String bottle = org.domi.furnace.server.item.ItemTools.itemName(be.items[0]) + ":"
                    + ((java.util.Map<?, ?>) org.domi.furnace.server.item.ItemTools.component(be.items[0], "minecraft:potion_contents")).get("potion");
            return new Object[] {bottle, be.items[3].count(), be.items[4].count(), be.fuel};
        });
        assertEquals("minecraft:potion:minecraft:water", res[0], "BrewEvent 취소면 병이 그대로");
        assertEquals(2, res[1], "재료도 그대로");
        assertEquals(3, res[2], "consuming=false 면 블레이즈 가루를 줄이지 않는다");
        assertEquals(6, res[3], "연료는 7 에서 시작해 양조를 시작할 때 하나 줄었다");
        assertTrue(log.contains("fuel:minecraft:blaze_powder:20"), log.toString());
        assertTrue(log.contains("brew:minecraft:nether_wart:fuel=6"), log.toString());
    }
}
