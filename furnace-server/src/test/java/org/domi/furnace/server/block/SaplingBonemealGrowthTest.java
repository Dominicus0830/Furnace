// 묘목→나무(TreeGrower)·뼛가루 지형 생성 feature 연결: 노이즈 오버월드의 실제 region 에서 결정적 시드로 키운다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.types.BonemealableBlock;
import org.domi.furnace.server.block.types.SaplingBlock;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.RegionLevel;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaplingBonemealGrowthTest {

    @TempDir static Path tmp;
    static FurnaceSchedulerImpl sched;
    static ServerCore core;

    @BeforeAll
    static void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched, reg -> NoiseWorldSource.overworld(0L, reg, RegistrySet.load("registries/1.21.1.txt")));
        for (int cx = 0; cx <= 5; cx++) {
            for (int cz = 0; cz <= 5; cz++) {
                var cp = new org.domi.furnace.server.world.ChunkPos(cx, cz);
                var loaded = new CompletableFuture<CompletableFuture<?>>();
                core.world().runAtChunk(cp, r -> loaded.complete(r.loadChunk(cp)));
                loaded.get(30, TimeUnit.SECONDS).get(120, TimeUnit.SECONDS);
            }
        }
    }

    @AfterAll
    static void tearDown() { sched.close(); }

    private static <T> T onRegion(Function<RegionLevel, T> f) throws Exception {
        var fut = new CompletableFuture<T>();
        core.world().runAt(0.5, 0.5, region -> {
            try {
                fut.complete(f.apply(region.level()));
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            }
        });
        return fut.get(30, TimeUnit.SECONDS);
    }

    private static LegacyRandomSource seeded(RegionLevel l, long seed) {
        LegacyRandomSource r = (LegacyRandomSource) l.random();
        r.setSeed(seed);
        return r;
    }

    /** 원점 기준 상자 안의 공기 아닌 칸 목록("dx,dy,dz 상태"). */
    private static List<String> dump(RegionLevel l, Pos o, int r, int up) {
        List<String> out = new ArrayList<>();
        for (int y = -1; y <= up; y++)
            for (int z = -r; z <= r; z++)
                for (int x = -r; x <= r; x++) {
                    int s = l.getBlockState(o.offset(x, y, z));
                    if (!B.isAir(s)) out.add(x + "," + y + "," + z + " " + Blocks.get().describe(s));
                }
        return out;
    }

    private static int sapling(String name, int stage) { return B.prop("stage").withInt(B.defaultState(name), stage); }

    @Test
    void neededFeaturesAreImplemented() throws Exception {
        List<String> unsupported = onRegion(l -> {
            List<String> bad = new ArrayList<>();
            for (String id : List.of("oak", "fancy_oak", "oak_bees_005", "fancy_oak_bees_005", "spruce", "mega_spruce", "mega_pine",
                    "mangrove", "tall_mangrove", "azalea_tree", "birch", "birch_bees_005", "jungle_tree_no_vine", "mega_jungle_tree",
                    "acacia", "cherry", "cherry_bees_005", "dark_oak", "huge_brown_mushroom", "huge_red_mushroom",
                    "crimson_fungus_planted", "warped_fungus_planted", "moss_patch_bonemeal", "crimson_forest_vegetation_bonemeal",
                    "warped_forest_vegetation_bonemeal", "nether_sprouts_bonemeal", "twisting_vines_bonemeal")) {
                LevelFeature f = l.configuredFeature("minecraft:" + id);
                if (f == null || !f.supported()) bad.add(id);
            }
            LevelFeature g = l.placedFeature("minecraft:grass_bonemeal");
            if (g == null || !g.supported()) bad.add("grass_bonemeal");
            for (String biome : List.of("minecraft:plains", "minecraft:flower_forest", "minecraft:meadow", "minecraft:cherry_grove",
                    "minecraft:swamp", "minecraft:forest", "minecraft:savanna", "minecraft:sunflower_plains")) {
                LevelFeature f = l.biomeFlowerFeature(biome);
                if (f == null || !f.supported()) bad.add("flower@" + biome);
            }
            return bad;
        });
        assertEquals(List.of(), unsupported);
    }

    @Test
    void oakSaplingGrowsDeterministically() throws Exception {
        Pos a = new Pos(24, 200, 24), b = new Pos(56, 200, 56);
        List<List<String>> trees = new ArrayList<>();
        List<Long> states = new ArrayList<>();
        for (Pos p : List.of(a, b)) {
            onRegion(l -> {
                l.setBlock(p.below(), B.defaultState("minecraft:dirt"), 2);
                int s = sapling("minecraft:oak_sapling", 1);
                l.setBlock(p, s, 2);
                LegacyRandomSource r = seeded(l, 12345L);
                ((SaplingBlock) B.behavior(s)).advanceTree(l, p, s, r);
                states.add(r.rawState());
                trees.add(dump(l, p, 6, 12));
                return null;
            });
        }
        assertTrue(trees.get(0).contains("0,0,0 minecraft:oak_log[axis=y]"), trees.get(0).toString());
        assertTrue(trees.get(0).stream().anyMatch(t -> t.contains("oak_leaves")));
        assertEquals(trees.get(0), trees.get(1), "같은 시드 → 같은 나무");
        assertEquals(states.get(0), states.get(1), "같은 시드 → 같은 난수 소비");
        // 고르기 nextFloat 한 번 + 나무 feature 의 난수를 레벨 난수에서 이어 썼다
        LegacyRandomSource ref = new LegacyRandomSource(12345L);
        ref.nextFloat();
        assertNotEquals(ref.rawState(), (long) states.get(0));
    }

    @Test
    void stageZeroOnlyAdvancesStage() throws Exception {
        Pos p = new Pos(28, 210, 40);
        int after = onRegion(l -> {
            l.setBlock(p.below(), B.defaultState("minecraft:dirt"), 2);
            int s = sapling("minecraft:birch_sapling", 0);
            l.setBlock(p, s, 2);
            LegacyRandomSource r = seeded(l, 7L);
            ((SaplingBlock) B.behavior(s)).advanceTree(l, p, s, r);
            assertEquals(new LegacyRandomSource(7L).rawState(), r.rawState(), "단계만 올릴 때 난수를 안 쓴다");
            return l.getBlockState(p);
        });
        assertEquals(sapling("minecraft:birch_sapling", 1), after);
    }

    @Test
    void blockedSaplingStaysAndDarkOakNeedsTwoByTwo() throws Exception {
        Pos p = new Pos(40, 220, 24);
        onRegion(l -> {
            l.setBlock(p.below(), B.defaultState("minecraft:dirt"), 2);
            l.setBlock(p.above(), B.defaultState("minecraft:stone"), 2);   // 바로 위가 막힘
            int s = sapling("minecraft:oak_sapling", 1);
            l.setBlock(p, s, 2);
            ((SaplingBlock) B.behavior(s)).advanceTree(l, p, s, seeded(l, 99L));
            assertEquals(s, l.getBlockState(p), "못 자라면 묘목 그대로");
            Pos q = new Pos(44, 220, 24);
            l.setBlock(q.below(), B.defaultState("minecraft:dirt"), 2);
            int d = sapling("minecraft:dark_oak_sapling", 1);
            l.setBlock(q, d, 2);
            ((SaplingBlock) B.behavior(d)).advanceTree(l, q, d, seeded(l, 99L));
            assertEquals(d, l.getBlockState(q), "짙은 참나무는 2×2 가 아니면 안 자란다");
            return null;
        });
    }

    @Test
    void spruceTwoByTwoGrowsMegaTree() throws Exception {
        Pos p = new Pos(40, 200, 40);
        List<String> tree = onRegion(l -> {
            int s = sapling("minecraft:spruce_sapling", 1);
            for (int dx = 0; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++) {
                    l.setBlock(p.offset(dx, -1, dz), B.defaultState("minecraft:podzol"), 2);
                    l.setBlock(p.offset(dx, 0, dz), s, 2);
                }
            ((SaplingBlock) B.behavior(s)).advanceTree(l, p, s, seeded(l, 4242L));
            return dump(l, p, 8, 40);
        });
        for (String c : List.of("0,0,0", "1,0,0", "0,0,1", "1,0,1"))
            assertTrue(tree.contains(c + " minecraft:spruce_log[axis=y]"), c + " " + tree);
    }

    @Test
    void bonemealOnGrassMushroomMossNylium() throws Exception {
        onRegion(l -> {
            // 잔디: 9×9 판 위에 풀·꽃
            Pos g = new Pos(24, 230, 56);
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) l.setBlock(g.offset(x, 0, z), B.defaultState("minecraft:grass_block"), 2);
            int gs = l.getBlockState(g);
            ((BonemealableBlock) B.behavior(gs)).performBonemeal(l, seeded(l, 1L), g, gs);
            long plants = dump(l, g.above(), 4, 1).stream().filter(t -> !t.contains("grass_block")).count();
            assertTrue(plants > 0, "잔디 뼛가루가 아무것도 안 놓음");

            // 갈색 버섯: 균사체 위에서 거대 버섯
            Pos m = new Pos(40, 230, 56);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) l.setBlock(m.offset(x, -1, z), B.defaultState("minecraft:mycelium"), 2);
            int ms = B.defaultState("minecraft:brown_mushroom");
            l.setBlock(m, ms, 2);
            ((BonemealableBlock) B.behavior(ms)).performBonemeal(l, seeded(l, 3L), m, ms);
            assertTrue(dump(l, m, 4, 10).stream().anyMatch(t -> t.contains("brown_mushroom_block")), "거대 버섯 없음");

            // 이끼
            Pos s = new Pos(56, 230, 24);
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) l.setBlock(s.offset(x, 0, z), B.defaultState("minecraft:stone"), 2);
            int moss = B.defaultState("minecraft:moss_block");
            l.setBlock(s, moss, 2);
            ((BonemealableBlock) B.behavior(moss)).performBonemeal(l, seeded(l, 5L), s, moss);
            long mossCount = dump(l, s, 4, 1).stream().filter(t -> t.contains("moss")).count();
            assertTrue(mossCount > 1, "이끼가 안 번짐");

            // 진홍 나일리움
            Pos n = new Pos(56, 230, 56);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) l.setBlock(n.offset(x, 0, z), B.defaultState("minecraft:crimson_nylium"), 2);
            int ns = l.getBlockState(n);
            ((BonemealableBlock) B.behavior(ns)).performBonemeal(l, seeded(l, 9L), n, ns);
            assertTrue(dump(l, n.above(), 3, 0).stream().anyMatch(t -> t.contains("crimson")), "진홍 식물 없음");
            return null;
        });
    }

    @Test
    void edgeOfLoadedAreaFailsKeepsSaplingButConsumesRandom() throws Exception {
        // 청크 6 은 불러오지 않았다: x=95 의 나무 잎이 그쪽에 닿는다
        Pos p = new Pos(95, 200, 40);
        onRegion(l -> {
            l.setBlock(p.below(), B.defaultState("minecraft:dirt"), 2);
            int s = sapling("minecraft:oak_sapling", 1);
            l.setBlock(p, s, 2);
            LegacyRandomSource r = seeded(l, 12345L);
            ((SaplingBlock) B.behavior(s)).advanceTree(l, p, s, r);
            assertEquals(s, l.getBlockState(p), "경계 밖에 닿으면 묘목 그대로");
            assertTrue(B.isAir(l.getBlockState(p.above())), "일부만 놓지 않는다");
            LegacyRandomSource ref = new LegacyRandomSource(12345L);
            ref.nextFloat();
            assertNotEquals(ref.rawState(), r.rawState(), "feature 가 쓴 난수는 소비된 채(바닐라와 다른 점, 문서화)");
            return null;
        });
    }

    @Test
    void treeUsesKnownShapeFlagsAndEdgeUpdatesAndMovesBeehive() throws Exception {
        Pos p = new Pos(72, 200, 24);
        onRegion(l -> {
            l.setBlock(p.below(), B.defaultState("minecraft:dirt"), 2);
            LevelFeature f = l.configuredFeature("minecraft:fancy_oak_bees");   // 벌집 확률 1.0
            assertTrue(f != null && f.supported());
            assertTrue(l.placeFeature(f, p, seeded(l, 77L)));
            int hives = 0, edgeTicked = 0, edges = 0, inner = 0, innerTicked = 0;
            for (int y = -1; y <= 16; y++)
                for (int z = -8; z <= 8; z++)
                    for (int x = -8; x <= 8; x++) {
                        Pos q = p.offset(x, y, z);
                        int s = l.getBlockState(q);
                        if (B.is(s, "minecraft:bee_nest") || B.is(s, "minecraft:beehive")) {
                            hives++;
                            assertNotNull(l.getBlockEntity(q), "벌집 블록 엔티티");
                        }
                        if (!B.is(s, "minecraft:oak_leaves")) continue;
                        boolean open = false;
                        for (int d = 0; d < 6; d++) {
                            int n = l.getBlockState(q.offset(Dir.DX[d], Dir.DY[d], Dir.DZ[d]));
                            if (!B.is(n, "minecraft:oak_leaves") && !B.is(n, "minecraft:oak_log")) open = true;
                        }
                        boolean ticked = l.hasScheduledTick(q, B.block(s));
                        if (open) { edges++; if (ticked) edgeTicked++; } else { inner++; if (ticked) innerTicked++; }
                    }
            assertTrue(hives > 0, "벌집 없음");
            assertTrue(edges > 0 && edgeTicked > 0, "가장자리 잎 모양 갱신(예약 틱) " + edgeTicked + "/" + edges);
            assertEquals(0, innerTicked, "안쪽 잎은 flag 19 라 모양 갱신 없음(" + inner + "칸)");
            return null;
        });
    }

    @Test
    void copyOnWriteReducesAllocation() throws Exception {
        var mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long[] bytes = new long[2];
        for (int mode = 0; mode < 2; mode++) {
            boolean cow = mode == 1;
            int m = mode;
            onRegion(l -> {
                Pos g = new Pos(40, 250, 24 + 32 * m);   // 서로 다른 판
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) l.setBlock(g.offset(x, 0, z), B.defaultState("minecraft:grass_block"), 2);
                int gs = l.getBlockState(g);
                org.domi.furnace.server.world.LiveFeatures.copyOnWrite = cow;
                long t = Thread.currentThread().threadId();
                long before = mx.getThreadAllocatedBytes(t);
                ((BonemealableBlock) B.behavior(gs)).performBonemeal(l, seeded(l, 1L), g, gs);
                bytes[m] = mx.getThreadAllocatedBytes(t) - before;
                org.domi.furnace.server.world.LiveFeatures.copyOnWrite = true;
                return null;
            });
        }
        System.out.println("GRASS_BONEMEAL_ALLOC full=" + bytes[0] + " cow=" + bytes[1]);
        assertTrue(bytes[1] < bytes[0], "쓰기 시 복사가 덜 할당해야 한다");
    }

    @Test
    void bonemealSuccessUsesOneLevelFloat() throws Exception {
        onRegion(l -> {
            Pos p = new Pos(30, 240, 30);
            int s = sapling("minecraft:oak_sapling", 0);
            for (long seed = 0; seed < 20; seed++) {
                seeded(l, seed);
                boolean ok = ((BonemealableBlock) B.behavior(s)).isBonemealSuccess(l, l.random(), p, s);
                assertEquals(new java.util.Random(seed).nextFloat() < 0.45, ok, "seed " + seed);
            }
            return null;
        });
    }
}
