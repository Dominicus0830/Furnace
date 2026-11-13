// 플러그인 API 3단계 둘째 묶음: 블록 이벤트(물리·레드스톤·피스톤·물 흐름·성장·불·잎)와 깔때기 이동.
// 구독자가 없으면 예전 동작 그대로인지도 같이 본다. 제작·화로·부싯돌은 봇 시험(tools/bot-test/round5).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.block.BlockBurnEvent;
import org.domi.furnace.api.event.block.BlockFromToEvent;
import org.domi.furnace.api.event.block.BlockGrowEvent;
import org.domi.furnace.api.event.block.BlockPhysicsEvent;
import org.domi.furnace.api.event.block.BlockPistonExtendEvent;
import org.domi.furnace.api.event.block.BlockPistonRetractEvent;
import org.domi.furnace.api.event.block.BlockRedstoneEvent;
import org.domi.furnace.api.event.block.LeavesDecayEvent;
import org.domi.furnace.api.event.inventory.InventoryMoveItemEvent;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.HopperBlockEntity;
import org.domi.furnace.server.block.types.CropBlock;
import org.domi.furnace.server.block.types.LeavesBlock;
import org.domi.furnace.server.block.types.LeverBlock;
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

class PluginApiBlockEventsTest {

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

    // ------------------------------------------------------------------ 물리

    @Test
    void physicsCancelKeepsTorchWithoutSupport() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        Pos base = new Pos(8, -50, 8), torch = base.above();
        on(BlockPhysicsEvent.class, e -> {
            if (e.pos().equals(new BlockPos(8, -49, 8))) {
                log.add(e.block().type() + "<" + e.changedType() + "@" + e.sourcePos());
                e.setCancelled(true);
            }
        });
        String kept = at(r -> {
            var l = r.level();
            l.setBlock(base, STONE, 3);
            l.setBlock(torch, B.defaultState("minecraft:torch"), 3);
            l.setBlock(base, AIR, 3);
            return desc(l, torch);
        });
        assertEquals("minecraft:torch", kept, "모양 갱신을 취소하면 받침이 없어도 남는다");
        assertTrue(log.contains("minecraft:torch<minecraft:air@" + new BlockPos(8, -50, 8)), log.toString());

        host.eventsImpl().unregisterAll(plugin);
        String gone = at(r -> {
            var l = r.level();
            Pos b2 = new Pos(4, -50, 4);
            l.setBlock(b2, STONE, 3);
            l.setBlock(b2.above(), B.defaultState("minecraft:torch"), 3);
            l.setBlock(b2, AIR, 3);
            return desc(l, b2.above());
        });
        assertEquals("minecraft:air", gone, "구독자가 없으면 예전처럼 떨어진다");
    }

    /**
     * T-032 검토 R4: 실제 입구 PluginGameEvents.blockPhysics 가 게으르게 만든 block()·changedType() 이 옛 서버가 미리 만들던 값
     * (ApiConvert.block(state)·ApiConvert.block(defaultState(원인)).type())과 같다 — 속성이 여럿인 상태(가루 세기·방향, 반복기, 계단)로.
     */
    @Test
    void physicsEntryLazyContentEqualsEagerConversion() {
        var blocks = core.world().blockRegistry();
        List<String> got = new CopyOnWriteArrayList<>();
        on(BlockPhysicsEvent.class, e -> got.add(e.block() + "|" + e.changedType() + "|" + e.pos() + "|" + e.sourcePos()));
        String[] names = {"minecraft:redstone_wire", "minecraft:repeater", "minecraft:oak_stairs", "minecraft:note_block", "minecraft:torch"};
        List<String> want = new java.util.ArrayList<>();
        int i = 0;
        for (String n : names) {
            int def = B.defaultState(n), count = BL.stateCountOf(B.block(def));
            for (int k = 0; k < count; k += Math.max(1, count / 7)) {
                int state = BL.firstState(B.block(def)) + k;
                int source = B.block(B.defaultState(names[(i + 1) % names.length]));
                Pos pos = new Pos(i, -50, 0), src = new Pos(i, -49, 0);
                assertTrue(host.gameEvents().blockPhysics("minecraft:overworld", pos, state, src, source));
                want.add(ApiConvert.block(state, blocks) + "|" + ApiConvert.block(B.defaultState(source), blocks).type()
                        + "|" + new BlockPos(i, -50, 0) + "|" + new BlockPos(i, -49, 0));
                i++;
            }
        }
        assertTrue(want.size() > names.length * 3, "속성 있는 상태를 여럿 봐야 한다: " + want.size());
        assertEquals(want, got);
        assertTrue(got.stream().anyMatch(s -> s.contains("power=")), got.toString());
    }

    // ------------------------------------------------------------------ 레드스톤

    @Test
    void redstoneEventCanKeepLeverOffAndDimWire() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(BlockRedstoneEvent.class, e -> {
            log.add(e.blockType() + ":" + e.oldCurrent() + ">" + e.newCurrent());
            if (e.blockType().equals("minecraft:lever")) e.setNewCurrent(0);
            if (e.blockType().equals("minecraft:redstone_wire")) e.setNewCurrent(3);
        });
        String[] res = at(r -> {
            var l = r.level();
            Pos lever = new Pos(8, -50, 8);
            l.setBlock(lever.below(), STONE, 3);
            int ls = B.prop("face").with(B.defaultState("minecraft:lever"), "floor");
            l.setBlock(lever, ls, 3);
            ((LeverBlock) B.behavior(ls)).pull(l.getBlockState(lever), l, lever, null);
            String leverAfter = desc(l, lever);
            Pos wire = new Pos(3, -50, 3);
            l.setBlock(wire.below(), STONE, 3);
            l.setBlock(wire, B.defaultState("minecraft:redstone_wire"), 3);
            l.setBlock(wire.east(), B.defaultState("minecraft:redstone_block"), 3);
            return new String[] {leverAfter, B.prop("power").valueName(l.getBlockState(wire))};
        });
        assertTrue(res[0].contains("powered=false"), "새 세기를 0 으로 두면 레버가 켜지지 않는다: " + res[0]);
        assertTrue(log.contains("minecraft:lever:0>15"), log.toString());
        assertTrue(log.contains("minecraft:redstone_wire:0>15"), log.toString());
        assertEquals("3", res[1], "가루 세기는 플러그인이 준 값");
    }

    // ------------------------------------------------------------------ 피스톤

    @Test
    void pistonExtendListsBlocksAndCancelStopsIt() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(BlockPistonExtendEvent.class, e -> {
            log.add("extend:" + e.direction() + ":" + e.isSticky() + ":" + e.blocks());
            e.setCancelled(true);
        });
        on(BlockPistonRetractEvent.class, e -> log.add("retract"));
        String[] res = at(r -> {
            var l = r.level();
            Pos p = new Pos(8, -50, 8);
            l.setBlock(p, B.prop("facing").with(B.defaultState("minecraft:piston"), "east"), 3);
            l.setBlock(p.east(), STONE, 3);
            l.setBlock(p.west(), B.defaultState("minecraft:redstone_block"), 3);
            step(l, 4);
            return new String[] {desc(l, p), desc(l, p.east())};
        });
        assertEquals(List.of("extend:east:false:[" + new BlockPos(9, -50, 8) + "]"), log);
        assertEquals("minecraft:piston[extended=false,facing=east]", res[0], "취소하면 늘어나지 않는다");
        assertEquals("minecraft:stone", res[1]);
    }

    @Test
    void stickyRetractCancelLeavesBlockBehind() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        String[] res = at(r -> {
            var l = r.level();
            Pos p = new Pos(8, -50, 8);
            l.setBlock(p, B.prop("facing").with(B.defaultState("minecraft:sticky_piston"), "east"), 3);
            l.setBlock(p.east(), STONE, 3);
            l.setBlock(p.west(), B.defaultState("minecraft:redstone_block"), 3);
            step(l, 6);
            return new String[] {desc(l, p), desc(l, p.east().east())};
        });
        assertEquals("minecraft:sticky_piston[extended=true,facing=east]", res[0]);
        assertEquals("minecraft:stone", res[1]);
        on(BlockPistonRetractEvent.class, e -> {
            log.add("retract:" + e.isSticky() + ":" + e.blocks());
            e.setCancelled(true);
        });
        String[] after = at(r -> {
            var l = r.level();
            Pos p = new Pos(8, -50, 8);
            l.setBlock(p.west(), AIR, 3);
            step(l, 6);
            return new String[] {desc(l, p), desc(l, p.east()), desc(l, p.east().east())};
        });
        assertEquals(List.of("retract:true:[" + new BlockPos(10, -50, 8) + "]"), log);
        assertEquals("minecraft:sticky_piston[extended=false,facing=east]", after[0], "머리는 들어간다");
        assertEquals("minecraft:air", after[1]);
        assertEquals("minecraft:stone", after[2], "블록은 끌려오지 않는다");
    }

    /** 일반 피스톤의 빈 수축 이벤트는 Paper 처럼 triggerEvent 의 수축 갈래에서 한 번만 나고, 취소하면 머리가 그대로다(T-040a R2). */
    @Test
    void plainRetractEventFiresOnceFromTheBlockEventAndCancelKeepsItExtended() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        String[] ext = at(r -> {
            var l = r.level();
            Pos p = new Pos(8, -50, 8);
            l.setBlock(p, B.prop("facing").with(B.defaultState("minecraft:piston"), "east"), 3);
            l.setBlock(p.west(), B.defaultState("minecraft:redstone_block"), 3);
            step(l, 6);
            return new String[] {desc(l, p)};
        });
        assertEquals("minecraft:piston[extended=true,facing=east]", ext[0]);
        on(BlockPistonRetractEvent.class, e -> {
            log.add("retract:" + e.direction() + ":" + e.isSticky() + ":" + e.blocks());
            e.setCancelled(true);
        });
        String[] cancelled = at(r -> {
            var l = r.level();
            Pos p = new Pos(8, -50, 8);
            l.setBlock(p.west(), AIR, 3);
            // 같은 틱에 이웃 갱신이 여러 번 와도(Spigot 자리는 그때마다 낸다) 이벤트는 블록 이벤트마다 한 번이다
            l.blockUpdated(p, B.block(B.defaultState("minecraft:piston")));
            l.blockUpdated(p, B.block(B.defaultState("minecraft:piston")));
            step(l, 6);
            return new String[] {desc(l, p)};
        });
        assertEquals("minecraft:piston[extended=true,facing=east]", cancelled[0], "취소하면 수축하지 않는다");
        assertEquals(List.of("retract:east:false:[]"), log, "한 번만");
    }

    // ------------------------------------------------------------------ 물 흐름

    @Test
    void fromToCancelStopsWater() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(BlockFromToEvent.class, e -> {
            log.add(e.blockType() + ":" + e.face());
            e.setCancelled(true);
        });
        String below = at(r -> {
            var l = r.level();
            Pos p = new Pos(8, -50, 8);
            l.setBlock(p, B.defaultState("minecraft:water"), 3);
            step(l, 20);
            return desc(l, p.below()) + "|" + desc(l, p.north());
        });
        assertEquals("minecraft:air|minecraft:air", below);
        assertTrue(log.contains("minecraft:water:down"), log.toString());
        assertFalse(log.contains("minecraft:water:north"), "아래 흐름이 취소되면 옆으로도 안 흐른다: " + log);

        host.eventsImpl().unregisterAll(plugin);
        String flowed = at(r -> {
            var l = r.level();
            Pos p = new Pos(3, -50, 3);
            l.setBlock(p, B.defaultState("minecraft:water"), 3);
            step(l, 10);
            return desc(l, p.below());
        });
        assertTrue(flowed.startsWith("minecraft:water"), "구독자가 없으면 흐른다: " + flowed);
    }

    // ------------------------------------------------------------------ 성장·잎

    @Test
    void growCancelKeepsCropAge() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(BlockGrowEvent.class, e -> {
            log.add(e.oldData().properties().get("age") + ">" + e.newData().properties().get("age"));
            e.setCancelled(true);
        });
        String age = at(r -> {
            var l = r.level();
            Pos p = new Pos(8, -50, 8);
            l.setBlock(p.below(), B.defaultState("minecraft:farmland"), 3);
            int wheat = B.defaultState("minecraft:wheat");
            l.setBlock(p, wheat, 3);
            ((CropBlock) B.behavior(wheat)).growCrops(l, p, wheat);
            return B.prop("age").valueName(l.getBlockState(p));
        });
        assertEquals("0", age);
        assertEquals(1, log.size());
        assertTrue(log.get(0).startsWith("0>"), log.toString());
    }

    @Test
    void leavesDecayCancelKeepsLeaves() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(LeavesDecayEvent.class, e -> {
            log.add(e.block().type());
            e.setCancelled(true);
        });
        String kept = at(r -> {
            var l = r.level();
            Pos p = new Pos(8, -50, 8);
            int leaves = B.prop("distance").withInt(B.defaultState("minecraft:oak_leaves"), 7);
            l.setBlock(p, leaves, 2);
            ((LeavesBlock) B.behavior(leaves)).randomTick(l.getBlockState(p), l, p, l.random());
            return desc(l, p);
        });
        assertTrue(kept.startsWith("minecraft:oak_leaves"), kept);
        assertEquals(List.of("minecraft:oak_leaves"), log);
    }

    @Test
    void burnEventReachesSubscriberAndCancelKeepsPlanks() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(BlockBurnEvent.class, e -> {
            log.add(e.blockType() + "<" + e.ignitingPos());
            e.setCancelled(true);
        });
        boolean planksStay = at(r -> {
            var l = r.level();
            Pos fire = new Pos(8, -50, 8);
            int planks = B.defaultState("minecraft:oak_planks");
            for (int d = 0; d < 6; d++) l.setBlock(fire.relative(d), planks, 2);
            l.setBlock(fire, B.defaultState("minecraft:fire"), 2);
            for (int i = 0; i < 200; i++) B.behavior(l.getBlockState(fire)).tick(l.getBlockState(fire), l, fire, l.random());
            boolean all = true;
            for (int d = 0; d < 6; d++) all &= l.getBlockState(fire.relative(d)) == planks;
            return all;
        });
        assertFalse(log.isEmpty(), "200 번 틱이면 옆 판자를 태우려 한다");
        assertTrue(log.get(0).startsWith("minecraft:oak_planks<" + new BlockPos(8, -50, 8)), log.toString());
        assertTrue(planksStay, "취소하면 판자가 그대로다");
    }

    // ------------------------------------------------------------------ 깔때기

    @Test
    void hopperMoveCanChangeItemAndCancel() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(InventoryMoveItemEvent.class, e -> {
            log.add(e.source().type() + ">" + e.destination().type() + ":" + e.item().type() + ":" + e.initiatorIsSource());
            e.setItem(new org.domi.furnace.api.item.ItemStack("minecraft:diamond", 1));
        });
        String[] res = at(r -> {
            var l = r.level();
            Pos h = new Pos(8, -50, 8);
            l.setBlock(h.below(), B.defaultState("minecraft:chest"), 3);
            l.setBlock(h, B.defaultState("minecraft:hopper"), 3);
            var hopper = (HopperBlockEntity) l.getBlockEntity(h);
            hopper.items[0] = B.stack("minecraft:stick", 2);
            hopper.tick(l);
            var chest = (org.domi.furnace.server.block.entity.ContainerBlockEntity) l.getBlockEntity(h.below());
            return new String[] {org.domi.furnace.server.item.ItemTools.itemName(chest.items[0]) + "x" + chest.items[0].count(),
                    org.domi.furnace.server.item.ItemTools.itemName(hopper.items[0]) + "x" + hopper.items[0].count()};
        });
        assertEquals(List.of("minecraft:hopper>minecraft:chest:minecraft:stick:true"), log);
        assertEquals("minecraft:diamondx1", res[0], "바꾼 아이템이 들어간다");
        assertEquals("minecraft:stickx1", res[1], "원래 아이템 하나는 빠진다");

        host.eventsImpl().unregisterAll(plugin);
        on(InventoryMoveItemEvent.class, e -> e.setCancelled(true));
        int[] after = at(r -> {
            var l = r.level();
            Pos h = new Pos(3, -50, 3);
            l.setBlock(h.below(), B.defaultState("minecraft:chest"), 3);
            l.setBlock(h, B.defaultState("minecraft:hopper"), 3);
            var hopper = (HopperBlockEntity) l.getBlockEntity(h);
            hopper.items[0] = B.stack("minecraft:stick", 2);
            hopper.tick(l);
            var chest = (org.domi.furnace.server.block.entity.ContainerBlockEntity) l.getBlockEntity(h.below());
            return new int[] {chest.items[0].count(), hopper.items[0].count(), hopper.cooldownTime};
        });
        assertEquals(0, after[0], "취소하면 옮기지 않는다");
        assertEquals(2, after[1], "꺼낸 것을 되돌린다");
        assertEquals(HopperBlockEntity.MOVE_ITEM_SPEED, after[2], "취소된 깔때기는 쉰다");
    }

    @Test
    void noSubscribersHopperMovesAsBefore() throws Exception {
        int[] res = at(r -> {
            var l = r.level();
            Pos h = new Pos(8, -50, 8);
            l.setBlock(h.below(), B.defaultState("minecraft:chest"), 3);
            l.setBlock(h, B.defaultState("minecraft:hopper"), 3);
            var hopper = (HopperBlockEntity) l.getBlockEntity(h);
            hopper.items[0] = B.stack("minecraft:stick", 2);
            hopper.tick(l);
            var chest = (org.domi.furnace.server.block.entity.ContainerBlockEntity) l.getBlockEntity(h.below());
            return new int[] {chest.items[0].count(), hopper.items[0].count()};
        });
        assertArrayEquals(new int[] {1, 1}, res);
    }
}
