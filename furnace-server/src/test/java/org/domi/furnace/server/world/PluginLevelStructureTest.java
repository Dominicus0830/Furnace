// 플러그인이 부른 바닐라 Structure#generate·StructureStart#placeInChunk(T-211 2단계): 통로(Paper 의 감싸개 쓰기 갈래·방벽 칸·엔티티),
// 서버 레벨에 바로, 세션, 구조물 몹 finalizeSpawn(T-221 — 플러그인 레벨의 난수·지역 난이도·달 밝기, 서버 레벨에 바로 놓으면 그 레벨의 몹).
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
import org.domi.furnace.server.world.gen.structure.BoundingBox;
import org.domi.furnace.server.world.gen.structure.StructureStart;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginLevelStructureTest {

    @TempDir static Path tmp;
    static FurnaceSchedulerImpl sched;
    static ServerCore core;
    static NoiseWorldSource gen;

    @BeforeAll
    static void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched, reg -> NoiseWorldSource.overworld(0L, reg, RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
        gen = NoiseWorldSource.of(core.world().worldSource());
        for (int cx = 0; cx <= 7; cx++) {
            for (int cz = 0; cz <= 7; cz++) {
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
        return fut.get(60, TimeUnit.SECONDS);
    }

    /** Paper 의 populator·나무 기록처럼 칸마다 마지막 상태를 남기는 통로. 쓰기 갈래(인자 3·4개)와 flag, 엔티티, blockUpdated 를 적는다. */
    static final class Recorder implements FeatureWorldView.WriteSink {
        final Map<Long, int[]> writes = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();
        final List<String> entities = new ArrayList<>();
        int blockUpdates;
        int seedAsks;
        long seed = core.world().seed();
        /** 참이면 Paper 의 나무 기록처럼 부술 수 없는 블록(방벽)이 이미 든 칸의 쓰기를 거절한다. */
        boolean paperCapture;

        private boolean write(int x, int y, int z, int state) {
            int[] old = writes.get(new Pos(x, y, z).asLong());
            if (paperCapture && old != null && Blocks.get().describe(old[3]).startsWith("minecraft:barrier")) return false;
            writes.put(new Pos(x, y, z).asLong(), new int[]{x, y, z, state});
            return true;
        }

        @Override
        public boolean setBlock(int x, int y, int z, int state, int flags) {
            calls.add("3:" + flags);
            return write(x, y, z, state);
        }

        @Override
        public boolean setBlock(int x, int y, int z, int state, int flags, int depth) {
            calls.add("4:" + flags + ":" + depth);
            return write(x, y, z, state);
        }

        @Override public String blockEntityType(int x, int y, int z) { return null; }
        @Override public long levelSeed(boolean throughGetLevel) { seedAsks++; return seed; }
        @Override public void entityLevel() {}
        @Override public void addEntity(String id, double x, double y, double z) { entities.add(id); }
        @Override public void blockUpdated(int x, int y, int z, int state) { blockUpdates++; }
        final List<String> asks = new ArrayList<>();
        float moon = 0.5F;
        @Override public float[] currentDifficultyAt(int x, int y, int z) { asks.add("difficulty"); return new float[] {2, 1.5F}; }
        @Override public float moonBrightness() { asks.add("moon"); return moon; }
        @Override public String biomeAt(int x, int y, int z) { asks.add("biome"); return "minecraft:swamp"; }

        long count(String name) {
            return writes.values().stream().filter(w -> Blocks.get().describe(w[3]).startsWith(name)).count();
        }
    }

    private static BoundingBox chunkBox(int cx, int cz) { return new BoundingBox(cx << 4, -64, cz << 4, (cx << 4) + 15, 320, (cz << 4) + 15); }

    private static List<String> dump(RegionLevel l, BoundingBox b) {
        List<String> out = new ArrayList<>();
        for (int y = b.minY(); y <= b.maxY(); y++)
            for (int z = b.minZ(); z <= b.maxZ(); z++)
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    int s = l.getBlockState(new Pos(x, y, z));
                    if (!B.isAir(s)) out.add(x + "," + y + "," + z + " " + Blocks.get().describe(s));
                }
        return out;
    }

    /** 시작의 상자가 닿는 청크마다 차례로(FAWE generateStructure 와 같은 순서 — x 먼저). */
    private static void placeAll(RegionLevel l, StructureStart start, Random random, Random levelRandom, LiveStructures.Session session, Recorder sink) {
        BoundingBox b = start.boundingBox();
        for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++)
            for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++)
                LiveStructures.placeInChunk(l, gen, start, chunkBox(cx, cz), cx, cz, new JavaRandomWorldgen(random), () -> levelRandom, session, sink);
    }

    @Test
    void sinkPlacesTheIglooThroughTheLevelOnly() throws Exception {
        // FAWE //structure 의 Paper 길: 조각 쓰기는 감싸개를 거쳐 인자 4개·깊이 512, 템플릿 본체는 감싸개를 벗겨 인자 3개 flag 2, NBT 칸은 먼저 방벽 flag 20
        onRegion(l -> {
            StructureStart start = LiveStructures.generate(gen, "minecraft:igloo", 2, 2, b -> true);
            assertNotNull(start);
            BoundingBox b0 = start.boundingBox();
            BoundingBox box = new BoundingBox(b0.minX() - 2, -64, b0.minZ() - 2, b0.maxX() + 2, 319, b0.maxZ() + 2);
            List<String> before = dump(l, box);
            Recorder sink = new Recorder();
            placeAll(l, start, new Random(5L), new Random(6L), new LiveStructures.Session(), sink);
            assertEquals(before, dump(l, box), "통로 판은 월드를 바꾸지 않는다");
            assertTrue(sink.count("minecraft:snow_block") > 20, "이글루 몸체: " + sink.writes.size());
            assertTrue(sink.calls.contains("3:2"), "템플릿 본체는 인자 3개 flag 2");
            assertTrue(sink.calls.contains("3:20"), "NBT 칸은 방벽을 flag 20 으로 먼저");
            assertTrue(sink.calls.stream().noneMatch(c -> c.startsWith("4:") && !c.endsWith(":512")), "조각 쓰기의 깊이는 512");
            assertTrue(sink.blockUpdates > 0, "템플릿 모양 갱신 뒤 blockUpdated");
            return null;
        });
    }

    @Test
    void paperCaptureKeepsBarriersOnBlockEntityCells() throws Exception {
        // Paper 나무 기록: 방벽이 든 칸은 부술 수 없어 다음 쓰기를 거절한다 — FAWE //structure 의 블록 엔티티 칸은 방벽으로 남는다
        onRegion(l -> {
            StructureStart start = LiveStructures.generate(gen, "minecraft:igloo", 2, 2, b -> true);
            Recorder plain = new Recorder();
            placeAll(l, start, new Random(5L), new Random(6L), new LiveStructures.Session(), plain);
            Recorder capture = new Recorder();
            capture.paperCapture = true;
            placeAll(l, start, new Random(5L), new Random(6L), new LiveStructures.Session(), capture);
            long barriers = capture.count("minecraft:barrier");
            assertTrue(barriers > 0, "블록 엔티티 칸은 방벽");
            assertEquals(0, plain.count("minecraft:barrier"), "평범한 통로는 방벽 위에 그 블록을 쓴다");
            assertEquals(plain.writes.keySet(), capture.writes.keySet(), "같은 칸에 쓴다");
            return null;
        });
    }

    @Test
    void desertPyramidAsksTheLevelForRandomAndSeed() throws Exception {
        // 사막 피라미드: 지하실은 레벨의 getRandom() 에서 뽑고, 의심스러운 모래 자리는 레벨의 getSeed() 로 시드를 만들며, afterPlace 는 감싸지 않은 레벨(인자 3개)
        onRegion(l -> {
            StructureStart start = LiveStructures.generate(gen, "minecraft:desert_pyramid", 3, 3, b -> true);
            assertNotNull(start);
            BoundingBox b0 = start.boundingBox();
            BoundingBox box = new BoundingBox(b0.minX() - 2, -64, b0.minZ() - 2, b0.maxX() + 2, 319, b0.maxZ() + 2);
            List<String> before = dump(l, box);
            int[] levelRandomAsks = {0};
            Random levelRandom = new Random(9L);
            Recorder sink = new Recorder();
            BoundingBox b = start.boundingBox();
            for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++)
                for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++)
                    LiveStructures.placeInChunk(l, gen, start, chunkBox(cx, cz), cx, cz, new JavaRandomWorldgen(new Random(5L)),
                            () -> { levelRandomAsks[0]++; return levelRandom; }, new LiveStructures.Session(), sink);
            assertEquals(before, dump(l, box), "통로 판은 월드를 바꾸지 않는다");
            assertTrue(sink.count("minecraft:sandstone") > 50, "피라미드 몸체: " + sink.writes.size());
            assertTrue(levelRandomAsks[0] > 0, "지하실은 레벨 난수에서 뽑는다");
            assertTrue(sink.seedAsks > 0, "시드는 레벨에 묻는다");
            assertTrue(sink.calls.stream().anyMatch(c -> c.startsWith("4:") && c.endsWith(":512")), "조각 쓰기는 인자 4개·깊이 512");
            assertTrue(sink.calls.stream().anyMatch(c -> c.startsWith("3:")), "afterPlace 는 인자 3개");

            Recorder otherSeed = new Recorder();
            otherSeed.seed = core.world().seed() + 1;
            assertThrows(UnsupportedOperationException.class, () -> placeAll(l, start, new Random(5L), new Random(9L), new LiveStructures.Session(), otherSeed),
                    "레벨 시드가 월드 시드와 다르면 같은 결과를 낼 수 없다");
            return null;
        });
    }

    @Test
    void spawnersDoNotAskTheLevelRandom() throws Exception {
        // Paper placeCraftSpawner: 월드 없는 상태라 setSpawnedType 은 RandomSource.create() — 레벨의 getRandom() 이 던져도 끝난다(평범한 populator)
        onRegion(l -> {
            StructureStart start = LiveStructures.generate(gen, "minecraft:stronghold", 2, 3, b -> true);
            assertNotNull(start);
            BoundingBox room = start.pieces().stream().filter(p -> p.getClass().getSimpleName().equals("PortalRoom")).findFirst().orElseThrow().boundingBox();
            Recorder sink = new Recorder();
            for (int cz = room.minZ() >> 4; cz <= room.maxZ() >> 4; cz++)
                for (int cx = room.minX() >> 4; cx <= room.maxX() >> 4; cx++)
                    LiveStructures.placeInChunk(l, gen, start, chunkBox(cx, cz), cx, cz, new JavaRandomWorldgen(new Random(3L)),
                            () -> { throw new UnsupportedOperationException("Not supported yet."); }, new LiveStructures.Session(), sink);
            assertEquals(1, sink.count("minecraft:spawner"), "차원문 방의 스포너");
            return null;
        });
    }

    @Test
    void sessionShowsAcceptedWritesToLaterChunkCopies() throws Exception {
        onRegion(l -> {
            var session = new LiveStructures.Session();
            int stone = B.defaultState("minecraft:diamond_block");
            session.accept(40, 120, 41, stone);
            ChunkColumn copy = l.listenerChunk(2, 2).copy();
            session.applyTo(2, 2, copy);
            assertEquals(stone, copy.getBlock(40 & 15, 120, 41 & 15));
            ChunkColumn other = l.listenerChunk(3, 2).copy();
            int before = other.getBlock(0, 120, 9);
            session.applyTo(3, 2, other);
            assertEquals(before, other.getBlock(0, 120, 9), "다른 청크에는 얹지 않는다");
            return null;
        });
    }

    /** 뽑기 이름을 적는 레벨 난수(바닐라 조각의 world.getRandom()). */
    static final class TracingRandom extends Random {
        final List<String> draws = new ArrayList<>();
        TracingRandom(long seed) { super(seed); }
        @Override public double nextDouble() { draws.add("D"); return super.nextDouble(); }
        @Override public float nextFloat() { draws.add("F"); return super.nextFloat(); }
        @Override public int nextInt(int bound) { draws.add("I" + bound); return super.nextInt(bound); }
        @Override public int nextInt() { draws.add("I"); return super.nextInt(); }
        @Override public long nextLong() { draws.add("L"); return super.nextLong(); }
        @Override public boolean nextBoolean() { draws.add("B"); return super.nextBoolean(); }
    }

    @Test
    void swampHutMobsDrawFromThePluginLevel() throws Exception {
        // 바닐라 1.21.1(Paper 같음): 마녀 — Raider·PatrollingMonster 는 STRUCTURE 에서 뽑지 않고 Mob.finalizeSpawn 이 triangle(nextDouble 둘)·왼손잡이(nextFloat),
        // 고양이 — AgeableMob(무리 크기 0 이라 아기 뽑기 없음)·Mob 같은 뽑기 뒤 달 밝기로 고른 변종 표(보름 아님 10개)에서 nextInt.
        // 둘 다 지역 난이도를 그 레벨에 묻고, 만든 몹은 그 레벨의 addFreshEntity 로 넘긴다.
        onRegion(l -> {
            StructureStart start = LiveStructures.generate(gen, "minecraft:swamp_hut", 5, 5, b -> true);
            assertNotNull(start);
            Recorder sink = new Recorder();
            TracingRandom lr = new TracingRandom(2L);
            placeAll(l, start, new Random(1L), lr, new LiveStructures.Session(), sink);
            assertEquals(List.of("minecraft:witch", "minecraft:cat"), sink.entities);
            assertEquals(List.of("D", "D", "F", "D", "D", "F", "I10"), lr.draws);
            assertEquals(2, sink.asks.stream().filter("difficulty"::equals).count(), sink.asks.toString());
            assertTrue(sink.asks.contains("moon"), sink.asks.toString());
            return null;
        });
        onRegion(l -> {
            // 보름달이면 변종 표가 11개
            StructureStart start = LiveStructures.generate(gen, "minecraft:swamp_hut", 5, 5, b -> true);
            Recorder sink = new Recorder();
            sink.moon = 1.0F;
            TracingRandom lr = new TracingRandom(2L);
            placeAll(l, start, new Random(1L), lr, new LiveStructures.Session(), sink);
            assertEquals("I11", lr.draws.get(lr.draws.size() - 1));
            return null;
        });
    }

    @Test
    void directSwampHutAddsFinalizedMobsToTheLevel() throws Exception {
        onRegion(l -> {
            StructureStart start = LiveStructures.generate(gen, "minecraft:swamp_hut", 5, 5, b -> true);
            assertNotNull(start);
            BoundingBox b = start.boundingBox();
            var box = new org.domi.furnace.server.entity.AABB(b.minX(), -64, b.minZ(), b.maxX() + 1, 320, b.maxZ() + 1);
            assertTrue(l.getEntitiesOfClass(org.domi.furnace.server.entity.Witch.class, box, e -> true).isEmpty());
            Random lr = new Random(2L);
            Random expect = new Random(2L);
            placeAll(l, start, new Random(1L), lr, null, null);
            var witches = l.getEntitiesOfClass(org.domi.furnace.server.entity.Witch.class, box, e -> true);
            assertEquals(1, witches.size());
            var witch = witches.get(0);
            assertTrue(witch.isPersistenceRequired(), "바닐라 SwampHutPiece: setPersistenceRequired");
            double bonus = 0.0 + 0.11485000000000001 * (expect.nextDouble() - expect.nextDouble());
            var mod = witch.attributes().get(org.domi.furnace.server.attribute.Attribute.FOLLOW_RANGE).getModifier("minecraft:random_spawn_bonus");
            assertNotNull(mod);
            assertEquals(bonus, mod.amount(), 0.0, "Mob.finalizeSpawn 의 triangle 이 레벨 난수의 첫 뽑기");
            assertEquals(expect.nextFloat() < 0.05F, witch.isLeftHanded());
            assertEquals(1, l.getEntitiesOfClass(org.domi.furnace.server.entity.Cat.class, box, e -> true).size());
            return null;
        });
    }

    @Test
    void directPlacementWritesTheWorld() throws Exception {
        onRegion(l -> {
            StructureStart start = LiveStructures.generate(gen, "minecraft:igloo", 5, 2, b -> true);
            assertNotNull(start);
            BoundingBox b0 = start.boundingBox();   // 이글루는 놓을 때 지표 높이로 내려간다(바닐라 IglooPiece) — 높이 전체를 센다
            BoundingBox box = new BoundingBox(b0.minX(), -64, b0.minZ(), b0.maxX(), 319, b0.maxZ());
            long before = dump(l, box).stream().filter(s -> s.endsWith("minecraft:snow_block")).count();
            placeAll(l, start, new Random(5L), new Random(6L), null, null);
            long after = dump(l, box).stream().filter(s -> s.endsWith("minecraft:snow_block")).count();
            assertTrue(after > before + 20, before + " -> " + after);
            return null;
        });
    }

    @Test
    void unknownStructureAndFarChunksFail() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> LiveStructures.generate(gen, "minecraft:nope", 0, 0, b -> true));
        assertNull(LiveStructures.generate(gen, "minecraft:igloo", 2, 2, b -> false), "바이옴이 거절하면 유효하지 않다");
        onRegion(l -> {
            StructureStart start = LiveStructures.generate(gen, "minecraft:igloo", 30, 30, b -> true);
            assertNotNull(start);
            // 로드되지 않은 청크(30, 30)에 놓으려 하면 닿지 못한다
            assertThrows(IllegalStateException.class, () -> LiveStructures.placeInChunk(l, gen, start, chunkBox(30, 30), 30, 30,
                    new JavaRandomWorldgen(new Random(1L)), Random::new, new LiveStructures.Session(), new Recorder()));
            return null;
        });
    }
}
