// 저장·로드 빠른 경로 동일성: zlib 압축 바이트·풀기 결과/예외가 Deflater/InflaterInputStream 스트림과 같은지,
// toNbt 섹션 팔레트가 예전 HashMap 구현과 같은 바이트인지, 디스크 로드 청크가 scratch 를 쥐지 않는지,
// 디코드를 레인 밖에서 해도 같은 파일의 저장 → 로드 순서가 지켜지는지.
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BitStorage;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.ChunkSnapshot;
import org.domi.furnace.server.world.ItemRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkIoFastPathCodecTest {

    static BlockRegistry reg;
    static AnvilChunkCodec codec;
    static List<String> biomes;
    static int plains;
    /** 이름(+속성) 왕복이 제자리인 상태 id — 팔레트 비교를 상태 id 로 하기 위해. */
    static int[] roundTripIds;

    @BeforeAll
    static void load() throws Exception {
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        biomes = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");
        plains = biomes.indexOf("minecraft:plains");
        codec = new AnvilChunkCodec(reg, ItemRegistry.load("items/1.21.1.txt"), biomes, plains,
                ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        List<Integer> ok = new ArrayList<>();
        for (int id = 0; id < reg.totalStateCount(); id += 3) {
            String key = reg.canonicalKeyOf(id);
            if (key == null) continue;
            int semi = key.indexOf(';');
            Map<String, String> props = new HashMap<>();
            if (semi >= 0) for (String kv : key.substring(semi + 1).split(",")) { int eq = kv.indexOf('='); if (eq > 0) props.put(kv.substring(0, eq), kv.substring(eq + 1)); }
            String name = semi < 0 ? key : key.substring(0, semi);
            if ((props.isEmpty() ? reg.stateIdOrAir(name) : reg.stateIdOrAir(name, props)) == id) ok.add(id);
        }
        roundTripIds = ok.stream().mapToInt(Integer::intValue).toArray();
        assertTrue(roundTripIds.length > 5000);
    }

    // ------------------------------------------------------------------ zlib

    private static byte[] legacyDeflate(byte[] data, int level) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        java.util.zip.Deflater def = new java.util.zip.Deflater(level);
        try (DeflaterOutputStream d = new DeflaterOutputStream(bos, def)) {
            d.write(data);
        } finally {
            def.end();
        }
        return bos.toByteArray();
    }

    private static byte[] legacyInflate(byte[] data) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data))) {
            in.transferTo(out);
        }
        return out.toByteArray();
    }

    private static byte[] randomPayload(SplittableRandom r) {
        int n = r.nextInt(6) == 0 ? r.nextInt(16) : r.nextInt(300_000);
        byte[] b = new byte[n];
        int alphabet = 1 + r.nextInt(r.nextBoolean() ? 4 : 256);   // 잘 압축되는 것 ~ 안 되는 것
        for (int i = 0; i < n; i++) b[i] = (byte) r.nextInt(alphabet);
        return b;
    }

    @Test
    void deflate_sameBytesAsDeflaterOutputStream() throws Exception {
        SplittableRandom r = new SplittableRandom(0xDEF1);
        for (int iter = 0; iter < 300; iter++) {
            byte[] data = randomPayload(r);
            assertArrayEquals(legacyDeflate(data, Compression.LEVEL), Compression.deflate(data), "iter=" + iter + " n=" + data.length);
        }
    }

    @Test
    void inflate_sameResultAndErrorsAsInflaterInputStream() throws Exception {
        SplittableRandom r = new SplittableRandom(0x1F1A);
        for (int iter = 0; iter < 600; iter++) {
            byte[] data = randomPayload(r);
            byte[] comp = legacyDeflate(data, 6);
            int mode = r.nextInt(4);
            if (mode == 1) comp = java.util.Arrays.copyOf(comp, r.nextInt(comp.length));       // 잘림
            else if (mode == 2 && comp.length > 0) comp[r.nextInt(comp.length)] ^= (byte) (1 + r.nextInt(255)); // 깨짐
            else if (mode == 3) {   // 뒤 쓰레기(첫 스트림 끝에서 멈춘다)
                byte[] tail = new byte[1 + r.nextInt(50)];
                byte[] c2 = java.util.Arrays.copyOf(comp, comp.length + tail.length);
                comp = c2;
            }
            byte[] a = null, b = null;
            Class<?> ea = null, eb = null;
            try { a = legacyInflate(comp); } catch (Exception e) { ea = e.getClass(); }
            try { b = Compression.decompress(Compression.DEFLATE, comp); } catch (Exception e) { eb = e.getClass(); }
            assertEquals(ea, eb, "iter=" + iter + " mode=" + mode);
            if (ea == null) assertArrayEquals(a, b, "iter=" + iter + " mode=" + mode);
        }
    }

    // ------------------------------------------------------------------ toNbt 팔레트

    /** 수정 전 blockStatesFromArray 의 팔레트 순서·인덱스 묶기(HashMap 첫 등장 순). 팔레트는 상태 id 로 비교한다. */
    private static long[] legacyPack(int[] cells, int minBits, List<Integer> paletteOut) {
        Map<Integer, Integer> indexOf = new HashMap<>();
        for (int v : cells) if (!indexOf.containsKey(v)) { indexOf.put(v, paletteOut.size()); paletteOut.add(v); }
        if (paletteOut.size() <= 1) return null;
        int bits = Math.max(minBits, 32 - Integer.numberOfLeadingZeros(paletteOut.size() - 1));
        BitStorage st = new BitStorage(bits, cells.length);
        for (int i = 0; i < cells.length; i++) st.set(i, indexOf.get(cells[i]));
        return st.data();
    }

    @Test
    void toNbtSections_samePaletteAndDataAsLegacy() {
        SplittableRandom r = new SplittableRandom(0x7A1);
        int[] counts = {1, 2, 3, 16, 17, 255, 256, 257, 600, 4096};
        for (int iter = 0; iter < 400; iter++) {
            ChunkColumn col = new ChunkColumn(plains, reg.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS,
                    ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
            int sec = r.nextInt(ChunkColumn.SECTION_COUNT);
            int distinct = counts[r.nextInt(counts.length)];
            int[] pool = new int[distinct];
            for (int i = 0; i < distinct; i++) pool[i] = roundTripIds[r.nextInt(roundTripIds.length)];
            int[] cells = new int[4096];
            for (int i = 0; i < 4096; i++) cells[i] = distinct == 4096 ? pool[i] : pool[r.nextInt(distinct)];
            col.loadSectionBlocks(sec, cells);
            int bd = r.nextInt(1, 9);
            int[] biomeCells = new int[64];
            for (int i = 0; i < 64; i++) biomeCells[i] = r.nextInt(bd);
            col.sections()[sec].replaceBiomes(biomeCells, 0);

            NbtCompound root = codec.toNbt(ChunkSnapshot.forSave(0, 0, col));
            NbtCompound s = null;
            for (Object o : root.getList("sections").items()) {
                NbtCompound c = (NbtCompound) o;
                if (c.getByte("Y") == (byte) ((ChunkColumn.MIN_Y >> 4) + sec) && c.contains("block_states")) s = c;
            }
            assertNotNull(s);
            // 블록
            List<Integer> pal = new ArrayList<>();
            long[] data = legacyPack(col.sections()[sec].blocks(), 4, pal);
            NbtCompound bs = s.getCompound("block_states");
            NbtList pl = bs.getList("palette");
            assertEquals(pal.size(), pl.size(), "iter=" + iter);
            for (int i = 0; i < pal.size(); i++) {
                NbtCompound e = (NbtCompound) pl.get(i);
                Map<String, String> props = new HashMap<>();
                if (e.contains("Properties")) for (var p : e.getCompound("Properties").tags().entrySet()) props.put(p.getKey(), (String) p.getValue());
                assertEquals((int) pal.get(i), props.isEmpty() ? reg.stateIdOrAir(e.getString("Name")) : reg.stateIdOrAir(e.getString("Name"), props), "iter=" + iter + " palette " + i);
            }
            if (data == null) assertFalse(bs.contains("data"));
            else assertArrayEquals(data, bs.getLongArray("data"), "iter=" + iter);
            // 바이옴
            List<Integer> bpal = new ArrayList<>();
            long[] bdata = legacyPack(col.sections()[sec].biomes(), 1, bpal);
            NbtCompound b = s.getCompound("biomes");
            NbtList bpl = b.getList("palette");
            assertEquals(bpal.size(), bpl.size());
            for (int i = 0; i < bpal.size(); i++) assertEquals(biomes.get(bpal.get(i)), bpl.get(i));
            if (bdata == null) assertFalse(b.contains("data"));
            else assertArrayEquals(bdata, b.getLongArray("data"));
            // 왕복
            ChunkColumn back = codec.fromNbt(root);
            assertArrayEquals(col.sections()[sec].blocks(), back.sections()[sec].blocks(), "iter=" + iter + " 왕복");
            assertEquals(col.sections()[sec].nonAirCount(), back.sections()[sec].nonAirCount());
        }
    }

    // ------------------------------------------------------------------ 로드 메모리·순서

    @Test
    void diskLoadedChunk_doesNotKeepScratchArrays() throws Exception {
        ChunkColumn col = new ChunkColumn(plains, reg.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS,
                ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        int stone = reg.defaultStateId("minecraft:stone");
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -64; y < 60; y++) col.setBlock(x, y, z, stone);
        ChunkColumn back = codec.fromNbt(codec.toNbt(ChunkSnapshot.forSave(0, 0, col)));
        java.lang.reflect.Field f = ChunkSection.class.getDeclaredField("scratch");
        f.setAccessible(true);
        for (ChunkSection s : back.sections()) assertNull(f.get(s), "로드 청크 섹션이 int[4096] 을 쥐고 있다");
        assertEquals(59, back.surfaceBlockY(3, 3));
    }

    @TempDir Path tmp;

    @Test
    void decodeOffLane_saveThenLoadSeesSavedChunk() throws Exception {
        RegionFileStore store = new RegionFileStore(tmp);
        var src = new org.domi.furnace.server.world.gen.AnvilWorldSource(tmp, store, codec, (x, z) -> { throw new AssertionError("생성 안 함"); });
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            IoLanes lanes = new IoLanes(pool, 4);
            int stone = reg.defaultStateId("minecraft:stone"), dirt = reg.defaultStateId("minecraft:dirt");
            List<java.util.concurrent.CompletableFuture<ChunkColumn>> loads = new ArrayList<>();
            List<Integer> expect = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                int x = i % 20, z = i / 20;
                int state = (i & 1) == 0 ? stone : dirt;
                ChunkColumn col = new ChunkColumn(plains, reg.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS,
                        ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
                col.setBlock(1, 10 + (i % 7), 1, state);
                byte[] bytes = NbtIo.toBytes(codec.toNbt(ChunkSnapshot.forSave(x, z, col)), "");
                lanes.lane(x, z).execute(() -> {
                    try { store.writeChunk(x, z, bytes); } catch (java.io.IOException e) { throw new RuntimeException(e); }
                });
                // 저장 제출 바로 뒤 로드 — 같은 레인 FIFO 라 저장본을 읽어야 한다
                loads.add(src.loadChunkAsync(x, z, lanes.lane(x, z), pool, pool));
                expect.add(10 + (i % 7));
            }
            for (int i = 0; i < loads.size(); i++) {
                ChunkColumn c = loads.get(i).get(30, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals((int) expect.get(i), c.surfaceBlockY(1, 1), "청크 " + i);
                assertFalse(c.dirty(), "디스크에서 읽은 청크는 깨끗하다");
            }
        } finally {
            pool.shutdownNow();
            store.closeAll();
        }
    }
}
