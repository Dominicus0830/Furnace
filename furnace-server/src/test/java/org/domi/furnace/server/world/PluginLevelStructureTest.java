// 플러그인이 부른 바닐라 Structure#generate·StructureStart#placeInChunk(T-211 2단계): 통로(Paper 의 감싸개 쓰기 갈래·방벽 칸·엔티티),
// 서버 레벨에 바로, 세션, 몹 finalizeSpawn 자리의 정직한 실패.
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
        @Override public long levelSeed(boolean throughGetLevel) { return core.world().seed(); }
        @Override public void entityLevel() {}
        @Override public void addEntity(String id, double x, double y, double z) { entities.add(id); }
        @Override public void blockUpdated(int x, int y, int z, int state) { blockUpdates++; }

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

    @Test
    void structureMobsFailHonestly() throws Exception {
        // 마녀 오두막의 마녀: 바닐라는 finalizeSpawn 에서 레벨 난수를 뽑는다 — 이식 전에는 같은 결과를 낼 수 없어 오류(통로·바로 둘 다)
        onRegion(l -> {
            StructureStart start = LiveStructures.generate(gen, "minecraft:swamp_hut", 5, 5, b -> true);
            assertNotNull(start);
            var e = assertThrows(UnsupportedOperationException.class,
                    () -> placeAll(l, start, new Random(1L), new Random(2L), new LiveStructures.Session(), new Recorder()));
            assertTrue(e.getMessage().contains("finalizeSpawn"), e.getMessage());
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
