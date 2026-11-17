// 플러그인이 부른 바닐라 ConfiguredFeature/PlacedFeature#place(T-211): 플러그인 레벨로 가는 쓰기 통로·서버 레벨에 바로 놓기·바이옴 거르기·닿지 못한 청크.
package org.domi.furnace.server.world;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginLevelFeatureTest {

    @TempDir static Path tmp;
    static FurnaceSchedulerImpl sched;
    static ServerCore core;

    @BeforeAll
    static void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched, reg -> NoiseWorldSource.overworld(0L, reg, RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
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

    private static void clear(RegionLevel l, Pos o, int r, int up) {
        for (int y = 0; y <= up; y++)
            for (int z = -r; z <= r; z++)
                for (int x = -r; x <= r; x++) l.setBlock(o.offset(x, y, z), B.defaultState("minecraft:air"), 2 | 16);
    }

    private static void ground(RegionLevel l, Pos p, String block) { l.setBlock(p.below(), B.defaultState(block), 2); }

    /** Paper BlockStateListPopulator 처럼 칸마다 처음 쓴 차례에 마지막 상태를 남기는 통로(블록 엔티티는 beType 으로 답한다). */
    static final class Recorder implements FeatureWorldView.WriteSink {
        final Map<Long, long[]> writes = new LinkedHashMap<>();
        final List<Integer> flags = new ArrayList<>();
        String beType;
        java.util.function.Predicate<Pos> refuse = p -> false;

        @Override
        public boolean setBlock(int x, int y, int z, int state, int flag) {
            if (refuse.test(new Pos(x, y, z))) return false;
            writes.computeIfAbsent(new Pos(x, y, z).asLong(), k -> new long[]{x, y, z, 0})[3] = state;
            flags.add(flag);
            return true;
        }

        @Override
        public String blockEntityType(int x, int y, int z) { return beType; }

        List<String> list() {
            List<String> out = new ArrayList<>();
            for (long[] w : writes.values()) out.add(w[0] + "," + w[1] + "," + w[2] + " " + Blocks.get().describe((int) w[3]));
            return out;
        }
    }

    private static List<String> list(List<long[]> writes) {
        List<String> out = new ArrayList<>();
        for (long[] w : writes) out.add(w[0] + "," + w[1] + "," + w[2] + " " + Blocks.get().describe((int) w[3]));
        return out;
    }

    @Test
    void sinkGetsTheSameWritesAsThePaperTreeCaptureAndLeavesTheWorld() throws Exception {
        // FAWE //forest 의 Paper 길: populator → 서버 레벨 나무 기록. 같은 시드면 기록(쓴 칸·마지막 상태·차례)과 난수 소비가 같아야 한다
        Pos o = new Pos(24, 250, 24);
        long seed = 777L;
        onRegion(l -> {
            ground(l, o, "minecraft:dirt");
            List<String> before = dump(l, o, 8, 16);
            Random r1 = new Random(seed);
            var cap = l.placeFeatureWith("minecraft:fancy_oak", o, new JavaRandomWorldgen(r1), false);
            assertTrue(cap.placed());

            Recorder sink = new Recorder();
            Random r2 = new Random(seed);
            assertTrue(l.placeFeatureThrough("minecraft:fancy_oak", false, o, new JavaRandomWorldgen(r2), sink));
            assertEquals(before, dump(l, o, 8, 16), "통로 판은 월드를 바꾸지 않는다");
            assertTrue(cap.writes().size() > 20);
            assertEquals(list(cap.writes()), sink.list());
            assertEquals(r1.nextLong(), r2.nextLong(), "같은 만큼 뽑는다");
            assertTrue(sink.flags.contains(19), "나무 블록은 flag 19(바닐라 TreeFeature)");
            return null;
        });
    }

    @Test
    void refusedWriteIsNotSeenByLaterReads() throws Exception {
        // Paper: 기록이 거절한 칸(부술 수 없는 블록)은 다음 읽기에도 옛 블록이다. 원점 아래 흙 쓰기를 거절하면 그 칸은 그대로다
        Pos o = new Pos(40, 250, 24);
        onRegion(l -> {
            ground(l, o, "minecraft:grass_block");
            Recorder sink = new Recorder();
            sink.refuse = p -> p.equals(o.below());
            assertTrue(l.placeFeatureThrough("minecraft:oak", false, o, new JavaRandomWorldgen(new Random(3L)), sink));
            assertFalse(sink.writes.containsKey(o.below().asLong()));
            assertTrue(sink.writes.containsKey(o.asLong()), "나머지는 계속 놓는다");
            assertTrue(Blocks.get().describe(l.getBlockState(o.below())).startsWith("minecraft:grass_block"));
            return null;
        });
    }

    @Test
    void beeNestWithoutBlockEntityDrawsNoBeeRandomAndWithOneIsUnsupported() throws Exception {
        // 바닐라 BeehiveDecorator: getBlockEntity(pos, BEEHIVE).ifPresent(벌 2~3 마리 — 난수). Paper 나무 기록 길은 블록 엔티티가 없어 건너뛴다
        Pos o = new Pos(56, 250, 24);
        onRegion(l -> {
            ground(l, o, "minecraft:dirt");
            Recorder none = new Recorder();
            Random r1 = new Random(11L);
            assertTrue(l.placeFeatureThrough("minecraft:super_birch_bees", false, o, new JavaRandomWorldgen(r1), none));
            assertTrue(none.list().stream().anyMatch(s -> s.contains("minecraft:bee_nest")), none.list().toString());

            clear(l, o, 6, 16);
            ground(l, o, "minecraft:dirt");
            Random r2 = new Random(11L);
            assertTrue(l.placeFeatureThrough("minecraft:super_birch_bees", false, o, new JavaRandomWorldgen(r2), null), "서버 레벨에 바로");
            assertNotEquals(r1.nextLong(), r2.nextLong(), "바로 놓으면 벌집 블록 엔티티가 있어 벌 난수를 더 뽑는다");

            clear(l, o, 6, 16);
            ground(l, o, "minecraft:dirt");
            Recorder hive = new Recorder();
            hive.beType = "minecraft:beehive";
            assertThrows(UnsupportedOperationException.class,
                    () -> l.placeFeatureThrough("minecraft:super_birch_bees", false, o, new JavaRandomWorldgen(new Random(11L)), hive));
            return null;
        });
    }

    @Test
    void directPlacementMatchesTheRuntimePath() throws Exception {
        Pos o = new Pos(72, 250, 24);
        long seed = 31L;
        onRegion(l -> {
            ground(l, o, "minecraft:dirt");
            Random r1 = new Random(seed);
            assertTrue(l.placeFeatureWith("minecraft:birch", o, new JavaRandomWorldgen(r1), true).placed());
            List<String> runtime = dump(l, o, 6, 14);
            clear(l, o, 6, 14);
            Random r2 = new Random(seed);
            assertTrue(l.placeFeatureThrough("minecraft:birch", false, o, new JavaRandomWorldgen(r2), null));
            assertEquals(runtime, dump(l, o, 6, 14));
            assertEquals(r1.nextLong(), r2.nextLong());
            return null;
        });
    }

    @Test
    void placedFeatureRunsItsPlacementAndBiomeFilterThrowsLikeVanilla() throws Exception {
        Pos o = new Pos(24, 250, 56);
        onRegion(l -> {
            ground(l, o, "minecraft:dirt");
            Recorder sink = new Recorder();
            assertTrue(l.placeFeatureThrough("minecraft:birch_checked", true, o, new JavaRandomWorldgen(new Random(5L)), sink));
            assertTrue(sink.writes.containsKey(o.asLong()));
            // trees_plains: count 0 또는 1, 마지막이 바이옴 거르기 — 위치가 생기면 바닐라 BiomeFilter 처럼 던진다
            int thrown = 0, empty = 0;
            for (long seed = 1; seed <= 40; seed++) {
                try {
                    assertFalse(l.placeFeatureThrough("minecraft:trees_plains", true, o, new JavaRandomWorldgen(new Random(seed)), new Recorder()));
                    empty++;
                } catch (IllegalStateException e) {
                    assertEquals("Tried to biome check an unregistered feature, or a feature that should not restrict the biome", e.getMessage());
                    thrown++;
                }
            }
            assertTrue(thrown > 0 && empty > 0, thrown + " / " + empty);
            return null;
        });
    }

    @Test
    void oreVeinThroughALevelIsUnsupportedButPlacesDirectly() throws Exception {
        Pos o = new Pos(40, 10, 56);
        onRegion(l -> {
            var e = assertThrows(UnsupportedOperationException.class,
                    () -> l.placeFeatureThrough("minecraft:ore_iron", false, o, new JavaRandomWorldgen(new Random(1L)), new Recorder()));
            assertTrue(e.getMessage().contains("BulkSectionAccess"), e.getMessage());
            assertNotNull(l.placeFeatureThrough("minecraft:ore_iron", false, o, new JavaRandomWorldgen(new Random(1L)), null), "서버 레벨에 바로는 된다");
            return null;
        });
    }

    @Test
    void unknownIdIsNullAndUnreachableChunkFailsWithoutChangingTheWorld() throws Exception {
        Pos e = new Pos(95, 250, 40);   // 불러온 청크는 0..5 — 여기 나무는 청크 6 에 닿는다
        onRegion(l -> {
            ground(l, e, "minecraft:dirt");
            List<String> before = dump(l, e, 2, 12);
            assertThrows(LiveFeatures.Unreachable.class,
                    () -> l.placeFeatureThrough("minecraft:oak", false, e, new JavaRandomWorldgen(new Random(1L)), null));
            assertEquals(before, dump(l, e, 2, 12), "바로 놓는 길은 아무것도 바꾸지 않는다");
            assertThrows(LiveFeatures.Unreachable.class,
                    () -> l.placeFeatureThrough("minecraft:oak", false, e, new JavaRandomWorldgen(new Random(1L)), new Recorder()));
            assertNull(l.placeFeatureThrough("minecraft:no_such_tree", false, e, new JavaRandomWorldgen(new Random(1L)), null));
            return null;
        });
    }
}
