// F-14: 생성만 하고 편집하지 않은 청크도 바닐라처럼 저장된다 — 재로드 때 월드젠을 다시 돌리지 않고, 저장 결과는 parity-audit 이 읽는 .mca 형식이다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.domi.furnace.server.world.gen.structure.ChunkStructures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedChunkSaveTest {

    private static final RegionPos R00 = new RegionPos(0, 0);
    private static final ChunkPos CP = new ChunkPos(1, 1);
    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final List<String> BIOMES = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");
    private static final int PLAINS = BIOMES.indexOf("minecraft:plains");
    private static final int DESERT = BIOMES.indexOf("minecraft:desert");

    /** 월드젠 호출을 세는 생성기: 평지 + 특이 블록·바이옴·구조물 참조·월드젠 주민. */
    private static final class CountingGen implements WorldSource {
        final AtomicInteger calls = new AtomicInteger();
        final SuperflatWorldSource flat = new SuperflatWorldSource(REG, PLAINS);

        @Override
        public ChunkColumn generate(int cx, int cz) {
            calls.incrementAndGet();
            ChunkColumn c = flat.generate(cx, cz);
            c.setBlock(3, -55, 4, B.defaultState("stone"));   // 높이맵도 바뀐다
            c.setBlock(9, -40, 9, B.defaultState("oak_planks"));
            c.setBiome(1, 2, 3, DESERT);
            if (cx == CP.x() && cz == CP.z()) {
                NbtCompound st = new NbtCompound();
                st.putCompound("starts", new NbtCompound());
                NbtCompound refs = new NbtCompound();
                refs.putLongArray("minecraft:village_plains", new long[]{ChunkStructures.chunkLong(2, 1)});
                st.putCompound("References", refs);
                c.structures(ChunkStructures.fromNbt(st));
                NbtCompound v = new NbtCompound();
                v.putString("id", "minecraft:villager");
                NbtList pos = new NbtList((byte) 6);
                pos.add(20.5); pos.add(-60.0); pos.add(20.5);
                v.put("Pos", pos);
                c.addGeneratedEntity(v);
            }
            return c;
        }
    }

    private static AnvilChunkCodec codec() {
        return new AnvilChunkCodec(REG, ItemRegistry.load("items/1.21.1.txt"), BIOMES, PLAINS, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
    }

    private static World world(Path dir, WorldSource base, FurnaceSchedulerImpl sched, ExecutorService io, int grace) {
        AnvilWorldSource src = new AnvilWorldSource(dir, new RegionFileStore(dir), codec(), base);
        src.entityStore(new RegionFileStore(dir, "entities"));
        World w = new World(src, new ChunkWorkerPool(1), sched, 1, 16, 2, 2, REG.directBits(), 7, io);
        w.setRegistries(REG, ItemRegistry.load("items/1.21.1.txt"));
        w.configurePersistence(1_000_000, grace);
        return w;
    }

    private record Observed(int[] blocks, int[] biomes, long[] mb, long[] ws, long[] of, String refs, int villagers) {}

    private static Observed observe(World w, FurnaceSchedulerImpl sched) throws Exception {
        w.awaitChunk(CP);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!sched.region(R00).submit(() -> w.loadedChunk(CP.x(), CP.z()).entitiesLoaded()).get()) {
            assertTrue(System.nanoTime() < deadline, "엔티티 묶음 읽기 대기 시간 초과");
            Thread.onSpinWait();
        }
        return sched.region(R00).submit(() -> {
            ChunkColumn c = w.loadedChunk(CP.x(), CP.z());
            int h = ChunkColumn.SECTION_COUNT * 16;
            int[] blocks = new int[16 * 16 * h];
            int i = 0;
            for (int y = ChunkColumn.MIN_Y; y < ChunkColumn.MIN_Y + h; y++)
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) blocks[i++] = c.getBlock(x, y, z);
            int[] biomes = java.util.Arrays.stream(c.sections()).flatMapToInt(s -> java.util.Arrays.stream(s.biomes())).toArray();
            var refs = new StringBuilder();
            c.structures().references().forEach((k, v) -> refs.append(k).append(java.util.Arrays.toString(v)));
            Region r = w.regionAt(R00);
            int villagers = (int) r.level().entities().tickSnapshot().stream().filter(e -> e instanceof Villager && !e.isRemoved()).count();
            return new Observed(blocks, biomes, c.motionBlockingHeightmap().clone(), c.worldSurfaceHeightmap().clone(),
                    c.oceanFloorHeightmap().clone(), refs.toString(), villagers);
        }).get();
    }

    private static void evict(World w, FurnaceSchedulerImpl sched) throws Exception {
        sched.region(R00).submit(() -> {
            Region r = w.regionAt(R00);
            for (int i = 0; i < 10; i++) r.tick();   // 구독 없음 → 유예 3틱 뒤 저장·퇴출
            assertFalse(r.chunkLoaded(CP), "퇴출돼야 디스크에서 다시 읽는다");
            return null;
        }).get();
    }

    /** (a) 생성 → (편집 없이) 퇴출 저장 → 재로드: 같은 내용, 월드젠은 다시 안 돈다. 새 서버(다시 연 저장소)에서도. */
    @Test
    void untouchedGeneratedChunk_isSavedAndReloadedWithoutRegenerating(@TempDir Path dir) throws Exception {
        CountingGen gen = new CountingGen();
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ExecutorService io = Executors.newFixedThreadPool(2);
        World w = world(dir, gen, sched, io, 3);
        Observed first = observe(w, sched);
        assertEquals(1, gen.calls.get(), "처음엔 한 번 생성");
        assertTrue(first.refs().contains("village_plains"));
        assertEquals(1, first.villagers());
        evict(w, sched);

        Observed second = observe(w, sched);
        assertEquals(1, gen.calls.get(), "재로드는 디스크에서 — 월드젠 다시 안 돈다");
        assertSame(first, second);
        evict(w, sched);
        io.shutdown();
        assertTrue(io.awaitTermination(30, TimeUnit.SECONDS));
        sched.close();

        // 새 프로세스처럼 저장소를 새로 열어도 같다
        CountingGen gen2 = new CountingGen();
        FurnaceSchedulerImpl sched2 = new FurnaceSchedulerImpl(50_000_000L, 1);
        ExecutorService io2 = Executors.newFixedThreadPool(2);
        World w2 = world(dir, gen2, sched2, io2, 3);
        Observed third = observe(w2, sched2);
        assertEquals(0, gen2.calls.get(), "다시 연 월드에서도 월드젠 0회");
        assertSame(first, third);
        io2.shutdown();
        sched2.close();
    }

    private static void assertSame(Observed a, Observed b) {
        assertArrayEquals(a.blocks(), b.blocks(), "블록");
        assertArrayEquals(a.biomes(), b.biomes(), "바이옴");
        assertArrayEquals(a.mb(), b.mb(), "MOTION_BLOCKING");
        assertArrayEquals(a.ws(), b.ws(), "WORLD_SURFACE");
        assertArrayEquals(a.of(), b.of(), "OCEAN_FLOOR");
        assertEquals(a.refs(), b.refs(), "구조물 참조");
        assertEquals(a.villagers(), b.villagers(), "월드젠 엔티티는 한 번만");
    }

    /** (c) 저장된 청크를 tools/parity-audit Harness.readChunk 와 같은 방식(world/region/r.X.Z.mca 헤더·압축 바이트)으로 읽는다. */
    @Test
    void savedGeneratedChunk_isReadableLikeParityAudit(@TempDir Path dir) throws Exception {
        CountingGen gen = new CountingGen();
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ExecutorService io = Executors.newFixedThreadPool(2);
        World w = world(dir, gen, sched, io, 3);
        observe(w, sched);
        evict(w, sched);
        io.shutdown();
        assertTrue(io.awaitTermination(30, TimeUnit.SECONDS));
        sched.close();

        byte[] f = Files.readAllBytes(dir.resolve("region").resolve("r." + (CP.x() >> 5) + "." + (CP.z() >> 5) + ".mca"));
        int idx = ((CP.x() & 31) + (CP.z() & 31) * 32) * 4;
        int off = ((f[idx] & 0xFF) << 16) | ((f[idx + 1] & 0xFF) << 8) | (f[idx + 2] & 0xFF);
        assertNotEquals(0, off, "헤더에 청크가 있다");
        int pos = off * 4096;
        int len = ByteBuffer.wrap(f, pos, 4).getInt();
        int comp = f[pos + 4];
        InputStream raw = new ByteArrayInputStream(f, pos + 5, len - 1);
        InputStream dec = switch (comp) {
            case 1 -> new GZIPInputStream(raw);
            case 2 -> new InflaterInputStream(raw);
            case 3 -> raw;
            default -> throw new AssertionError("compression " + comp);
        };
        NbtCompound root = NbtIo.read(new DataInputStream(dec));
        assertEquals("minecraft:full", root.getString("Status"));
        assertEquals(CP.x(), root.getInt("xPos"));
        assertEquals(CP.z(), root.getInt("zPos"));
        assertTrue(root.contains("DataVersion"));
        NbtList sections = root.getList("sections");
        assertFalse(sections.items().isEmpty());
        NbtCompound s0 = (NbtCompound) sections.items().stream()
                .filter(o -> o instanceof NbtCompound c && c.contains("block_states")).findFirst().orElseThrow();
        assertTrue(s0.getCompound("block_states").contains("palette"));
        assertTrue(s0.contains("biomes"));
        NbtCompound hm = root.getCompound("Heightmaps");
        for (String k : new String[]{"MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES", "OCEAN_FLOOR", "WORLD_SURFACE"}) assertTrue(hm.contains(k), k);
        assertTrue(root.getCompound("structures").getCompound("References").contains("minecraft:village_plains"));
    }

    /**
     * (b) 생성 청크가 모두 dirty 여도 자동 저장은 틱당 4 ms 예산 큐로 나뉜다. region 스레드 CPU 시간으로 잰다.
     * p95 는 기준 틱 + 예산 4 ms + 마지막 한 청크(스냅샷) 근처여야 한다(한 틱에 몰면 수백 청크 × 스냅샷).
     */
    @Test
    void autosaveOfManyGeneratedChunks_isSpreadAcrossTicks(@TempDir Path dir) throws Exception {
        var gen = new SuperflatWorldSource(REG, PLAINS);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ExecutorService io = Executors.newFixedThreadPool(2);
        World w = world(dir, gen, sched, io, 1_000_000);
        int n = 24;   // 24×24 = 576 청크(시야 12 정도 한 명)
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) w.awaitChunk(new ChunkPos(x, z));
        var mx = ManagementFactory.getThreadMXBean();
        long[] result = sched.region(R00).submit(() -> {
            Region r = w.regionAt(R00);
            int dirty = r.dirtyChunkCount();
            long base = 0;
            for (int i = 0; i < 20; i++) {   // 저장 없는 틱의 비용
                long t0 = mx.getCurrentThreadCpuTime();
                r.tick();
                base = Math.max(base, mx.getCurrentThreadCpuTime() - t0);
            }
            w.configurePersistence(1, 1_000_000);   // 다음 틱에 자동 저장 시작
            long max = 0, total = 0, maxAt = -1;
            long[] each = new long[10_000];
            int ticks = 0;
            while (r.dirtyChunkCount() > 0 && ticks < 10_000) {
                long t0 = mx.getCurrentThreadCpuTime();
                r.tick();
                long d = mx.getCurrentThreadCpuTime() - t0;
                if (d > max) { max = d; maxAt = ticks; }
                each[ticks] = d;
                total += d;
                ticks++;
                w.configurePersistence(1_000_000, 1_000_000);   // 한 번만 시작, 나머지는 큐가 이어 간다
            }
            long[] sorted = java.util.Arrays.copyOf(each, ticks);
            java.util.Arrays.sort(sorted);
            return new long[]{dirty, base, max, total, ticks, r.dirtyChunkCount(), maxAt, sorted[ticks / 2], sorted[(int) (ticks * 0.95)]};
        }).get();
        System.out.printf("[F-14] dirty=%d 기준 틱 최대=%.2fms 저장 틱 최대=%.2fms(%d번째) 중앙=%.2fms p95=%.2fms 합계=%.2fms 틱수=%d%n",
                result[0], result[1] / 1e6, result[2] / 1e6, result[6], result[7] / 1e6, result[8] / 1e6, result[3] / 1e6, result[4]);
        assertEquals(n * n, result[0], "생성 청크 전부 저장 대상");
        assertEquals(0, result[5], "결국 다 저장된다");
        // 첫 몇 틱은 JIT·클래스 로딩으로 한 항목이 길 수 있다 — 분산 여부는 p95 로, 몰림이 없는지는 최대로 본다
        assertTrue(result[4] > 10, "여러 틱에 나뉜다: " + result[4]);
        assertTrue(result[8] < result[1] + 10_000_000L, "p95 틱 CPU 는 기준 틱 + 예산 근처: " + result[8] / 1e6 + " ms");
        assertTrue(result[2] < 100_000_000L, "한 틱에 몰리지 않는다: " + result[2] / 1e6 + " ms");
        io.shutdown();
        sched.close();
    }

    /** 구독이 한꺼번에 사라져 생성 청크가 모두 퇴출 대상이 돼도(퇴출 저장) 틱마다 예산 안에서 나눠 내린다. */
    @Test
    void massEviction_isSpreadAcrossTicks(@TempDir Path dir) throws Exception {
        var gen = new SuperflatWorldSource(REG, PLAINS);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ExecutorService io = Executors.newFixedThreadPool(2);
        World w = world(dir, gen, sched, io, 1_000_000);
        int n = 24;
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) w.awaitChunk(new ChunkPos(x, z));
        var mx = ManagementFactory.getThreadMXBean();
        long[] res = sched.region(R00).submit(() -> {
            Region r = w.regionAt(R00);
            for (int i = 0; i < 20; i++) r.tick();
            w.configurePersistence(1_000_000, 1);
            long max = 0, total = 0;
            long[] each = new long[10_000];
            int ticks = 0;
            while (r.dirtyChunkCount() > 0 && ticks < 10_000) {
                long t0 = mx.getCurrentThreadCpuTime();
                r.tick();
                long d = mx.getCurrentThreadCpuTime() - t0;
                max = Math.max(max, d);
                each[ticks++] = d;
                total += d;
            }
            long[] sorted = java.util.Arrays.copyOf(each, ticks);
            java.util.Arrays.sort(sorted);
            return new long[]{max, total, ticks, r.dirtyChunkCount(), sorted[ticks / 2], sorted[(int) (ticks * 0.95)]};
        }).get();
        System.out.printf("[F-14] 퇴출 576청크: 틱 최대=%.2fms 중앙=%.2fms p95=%.2fms 합계=%.2fms 틱수=%d%n",
                res[0] / 1e6, res[4] / 1e6, res[5] / 1e6, res[1] / 1e6, res[2]);
        assertEquals(0, res[3], "결국 다 저장·퇴출된다");
        assertTrue(res[2] > 10, "여러 틱에 나뉜다: " + res[2]);
        assertTrue(res[5] < 20_000_000L, "p95 틱 CPU: " + res[5] / 1e6 + " ms");
        io.shutdown();
        sched.close();
    }
}
