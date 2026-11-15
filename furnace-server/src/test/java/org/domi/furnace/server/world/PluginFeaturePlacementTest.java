// 플러그인 나무 생성(T-141 E5, Paper generateTree): 주어진 java.util.Random 을 feature 난수로 그대로 쓰기·쓴 칸 기록(반영 안 함)·후렴 식물.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginFeaturePlacementTest {

    @TempDir static Path tmp;
    static FurnaceSchedulerImpl sched;
    static ServerCore core;

    @BeforeAll
    static void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched, reg -> NoiseWorldSource.overworld(0L, reg, RegistrySet.load("registries/1.21.1.txt")));
        for (int cx = 0; cx <= 5; cx++) {
            for (int cz = 0; cz <= 5; cz++) {
                var cp = new ChunkPos(cx, cz);
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

    /** 원점 기준 상자 안의 공기 아닌 칸("dx,dy,dz 상태"). */
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

    /** 원점 위 상자를 공기로 되돌린다(같은 자리에서 두 번 키워 비교하려고 — 잎 거리는 바닐라에서도 자리마다 HashSet 순서가 달라 자리를 바꾸면 안 된다). */
    private static void clear(RegionLevel l, Pos o, int r, int up) {
        for (int y = 0; y <= up; y++)
            for (int z = -r; z <= r; z++)
                for (int x = -r; x <= r; x++) l.setBlock(o.offset(x, y, z), B.defaultState("minecraft:air"), 2 | 16);
    }

    private static void ground(RegionLevel l, Pos p, String block) { l.setBlock(p.below(), B.defaultState(block), 2); }

    @Test
    void everyPaperTreeTypeFeatureExistsAndIsImplemented() throws Exception {
        // Paper CraftRegionAccessor#generateTree 의 TreeType → TreeFeatures 표(균류는 보통 월드라 planted)
        List<String> bad = onRegion(l -> {
            List<String> out = new ArrayList<>();
            for (String id : List.of("oak", "fancy_oak", "birch", "spruce", "pine", "mega_jungle_tree", "jungle_tree_no_vine", "jungle_tree",
                    "jungle_bush", "huge_red_mushroom", "huge_brown_mushroom", "swamp_oak", "acacia", "dark_oak", "mega_spruce", "mega_pine",
                    "super_birch_bees_0002", "crimson_fungus_planted", "warped_fungus_planted", "azalea_tree", "mangrove", "tall_mangrove",
                    "cherry", "chorus_plant")) {
                var f = l.configuredFeature("minecraft:" + id);
                if (f == null || !f.supported()) out.add(id);
            }
            return out;
        });
        assertEquals(List.of(), bad);
    }

    @Test
    void javaRandomDrivesFeatureLikePaperRandomSourceWrapper() throws Exception {
        // java.util.Random 과 바닐라 LegacyRandomSource 는 같은 시드면 같은 수열이다. 그래서 Paper(RandomSourceWrapper)로 키운 나무는
        // 레벨 난수로 키운 나무(묘목 경로, 이미 바닐라 대조됨)와 같아야 하고, 플러그인 Random 도 같은 만큼 나아가야 한다.
        Pos a = new Pos(24, 260, 24), b = a;   // 지형이 닿지 않는 높이, 같은 자리
        long seed = 98765L;
        var viaLevel = onRegion(l -> {
            ground(l, a, "minecraft:dirt");
            LegacyRandomSource r = new LegacyRandomSource(seed);
            assertTrue(l.placeFeature(l.configuredFeature("minecraft:fancy_oak"), a, r));
            return List.of(dump(l, a, 10, 20), List.of(r.nextLong()));
        });
        var viaPlugin = onRegion(l -> {
            clear(l, b, 10, 20);
            Random r = new Random(seed);
            var res = l.placeFeatureWith("minecraft:fancy_oak", b, new JavaRandomWorldgen(r), true);
            assertTrue(res.placed());
            assertEquals(List.of(), res.writes(), "반영하면 기록을 돌려주지 않는다");
            return List.of(dump(l, b, 10, 20), List.of(r.nextLong()));
        });
        assertTrue(viaLevel.get(0).size() > 20, viaLevel.get(0).toString());
        assertEquals(viaLevel, viaPlugin);
    }

    @Test
    void captureLeavesWorldAndListsFinalStatesInFirstWriteOrder() throws Exception {
        Pos c = new Pos(24, 250, 56);
        long seed = 4242L;
        onRegion(l -> {
            ground(l, c, "minecraft:dirt");
            List<String> before = dump(l, c, 6, 12);
            Random r1 = new Random(seed);
            var cap = l.placeFeatureWith("minecraft:birch", c, new JavaRandomWorldgen(r1), false);
            assertTrue(cap.placed());
            assertEquals(before, dump(l, c, 6, 12), "기록만 하면 월드는 그대로");
            Set<Long> seen = new HashSet<>();
            for (long[] w : cap.writes()) assertTrue(seen.add(new Pos((int) w[0], (int) w[1], (int) w[2]).asLong()), "칸마다 한 번");
            long[] first = cap.writes().get(0);
            assertArrayEquals(new long[]{c.x(), c.y(), c.z()}, new long[]{first[0], first[1], first[2]}, "TreeFeature 는 원점 통나무부터 쓴다");

            clear(l, c, 6, 12);
            Random r2 = new Random(seed);
            assertTrue(l.placeFeatureWith("minecraft:birch", c, new JavaRandomWorldgen(r2), true).placed());
            assertEquals(r1.nextLong(), r2.nextLong(), "기록·반영 모두 같은 만큼 뽑는다");
            for (long[] w : cap.writes()) {
                Pos at = new Pos((int) w[0], (int) w[1], (int) w[2]);
                assertEquals(Blocks.get().describe((int) w[3]), Blocks.get().describe(l.getBlockState(at)), at.toString());
            }
            return null;
        });
    }

    @Test
    void touchingUnloadedChunkPlacesNothing() throws Exception {
        // 불러온 청크는 0..5 — x=95 에서 자라는 나무는 청크 6 을 건드린다
        Pos e = new Pos(95, 250, 40);
        onRegion(l -> {
            ground(l, e, "minecraft:dirt");
            List<String> before = dump(l, e, 2, 12);
            for (boolean apply : new boolean[]{true, false}) {
                var res = l.placeFeatureWith("minecraft:oak", e, new JavaRandomWorldgen(new Random(1L)), apply);
                assertFalse(res.placed());
                assertEquals(List.of(), res.writes());
            }
            assertEquals(before, dump(l, e, 2, 12));
            assertNull(l.placeFeatureWith("minecraft:no_such_tree", e, new JavaRandomWorldgen(new Random(1L)), true), "모르는 id");
            return null;
        });
    }

    @Test
    void chorusPlantGrowsWithFlagTwoAndAlwaysReportsPlaced() throws Exception {
        Pos p = new Pos(40, 230, 40), q = new Pos(72, 230, 72);
        onRegion(l -> {
            ground(l, p, "minecraft:end_stone");
            var res = l.chorusPlantWith(p, new JavaRandomWorldgen(new Random(5L)), true);
            assertTrue(res.placed());
            List<String> plant = dump(l, p, 8, 30);
            assertTrue(plant.stream().anyMatch(t -> t.startsWith("0,0,0 minecraft:chorus_plant")), plant.toString());
            assertTrue(plant.stream().anyMatch(t -> t.contains("chorus_flower[age=5]")), plant.toString());
            // 바닥 검사가 없다(Paper 는 ChorusFlowerBlock.generatePlant 를 바로 부른다): 공중에서도 자란다
            var air = l.chorusPlantWith(q, new JavaRandomWorldgen(new Random(5L)), false);
            assertTrue(air.placed());
            assertFalse(air.writes().isEmpty());
            assertTrue(B.isAir(l.getBlockState(q)), "기록만 하면 월드는 그대로");
            return null;
        });
    }

    @Test
    void consumeCountAndForkFollowPaperWrapper() {
        Random r = new Random(3L), ref = new Random(3L);
        var w = new JavaRandomWorldgen(r);
        w.consumeCount(3);
        for (int i = 0; i < 3; i++) ref.nextInt();
        assertEquals(ref.nextLong(), r.nextLong());
        assertThrows(UnsupportedOperationException.class, w::forkPositional);
        w.setSeed(77L);
        assertEquals(new Random(77L).nextInt(), r.nextInt(), "setSeed 도 플러그인 Random 에");
    }
}
