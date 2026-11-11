// A05-01 회귀 시험(재현: docs/audit/findings/A05-repro/A05ChunkCorruptionReproTest.java.txt): 손상된 청크(깨진 zlib 본문·팔레트 범위 밖 인덱스)를
// 만나면 바닐라·Paper 처럼 로그를 남기고 그 자리를 새로 생성한다(바닐라 ChunkMap.handleChunkLoadFailure → createEmptyChunk,
// Paper Moonrise ChunkDataLoadTask "chunk data will be lost" → 빈 ProtoChunk). 덮어쓰기 전에 원본을 region_corrupt/ 에 남긴다.
// A05-03: 이 서버보다 새 판으로 저장된 청크는 새로 생성하지도 덮지도 않는다(Paper 와 같이 거부).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A05CorruptChunkRegressionTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    /** 정상 청크 (0,0) 을 디스크에 쓴다. */
    private static void writeGoodChunk(Path dir) throws Exception {
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        ChunkColumn col = new SuperflatWorldSource(REG, 0).generate(0, 0);
        RegionFileStore store = new RegionFileStore(dir);
        store.writeChunk(0, 0, NbtIo.toBytes(codec.toNbt(col, 0, 0), ""));
        store.closeAll();
    }

    /** r.0.0.mca 의 청크 (0,0) 압축 본문 앞 64바이트를 쓰레기로 덮는다(헤더·길이는 그대로 — 바닐라도 길이 검사를 통과한다). */
    private static void corruptZlib(Path dir) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(dir.resolve("region/r.0.0.mca").toFile(), "rw")) {
            raf.seek(0);
            int loc = raf.readInt();
            long off = (long) (loc >>> 8) * 4096;
            raf.seek(off);
            int len = raf.readInt();
            raf.seek(off + 5);
            byte[] junk = new byte[Math.min(64, len - 1)];
            java.util.Arrays.fill(junk, (byte) 0x55);
            raf.write(junk);
        }
    }

    /** 압축은 멀쩡하지만 섹션 0 의 팔레트가 2개인데 data 칸이 인덱스 15 를 가리키는 청크(바닐라: "Recoverable errors"/ChunkReadException → 새로 생성). */
    private static void writeBadPaletteChunk(Path dir) throws Exception {
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        ChunkColumn col = new SuperflatWorldSource(REG, 0).generate(0, 0);
        NbtCompound root = codec.toNbt(col, 0, 0);
        NbtCompound s0 = (NbtCompound) root.getList("sections").get(0);
        NbtCompound bs = new NbtCompound();
        NbtList pal = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound a = new NbtCompound(); a.putString("Name", "minecraft:stone"); pal.add(a);
        NbtCompound b = new NbtCompound(); b.putString("Name", "minecraft:dirt"); pal.add(b);
        bs.putList("palette", pal);
        long[] data = new long[256];   // 4비트 × 4096 칸
        java.util.Arrays.fill(data, -1L); // 모든 칸 인덱스 15
        bs.putLongArray("data", data);
        s0.putCompound("block_states", bs);
        RegionFileStore store = new RegionFileStore(dir);
        store.writeChunk(0, 0, NbtIo.toBytes(root, ""));
        store.closeAll();
    }

    /** loadChunk 를 region 스레드에서 걸고 결과(예외면 null)와 예외를 돌려준다. */
    private static Object[] load(World w, FurnaceSchedulerImpl sched) throws Exception {
        RegionPos rp = new RegionPos(0, 0);
        CompletableFuture<CompletableFuture<ChunkColumn>> f = new CompletableFuture<>();
        sched.region(rp).execute(() -> f.complete(w.regionAt(rp).loadChunk(new ChunkPos(0, 0)))).get();
        try {
            return new Object[] {f.get().get(30, TimeUnit.SECONDS), null};
        } catch (java.util.concurrent.ExecutionException e) {
            return new Object[] {null, e.getCause()};
        }
    }

    private static void assertRegenerates(Path dir, String what) throws Exception {
        RegionFileStore store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, new AnvilChunkCodec(REG, 0), new SuperflatWorldSource(REG, 0));
        var io = Executors.newFixedThreadPool(2);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        World w = new World(src, new ChunkWorkerPool(1), sched, 1, 16, 5, 5, REG.directBits(), 7, io);
        try {
            Object[] first = load(w, sched);
            Object[] second = load(w, sched);   // "다음 요청에서 재시도" — 다시 읽어도 같은 파일이라 또 실패한다
            System.out.println("[A05-01] " + what + " 1차: " + (first[1] == null ? "로드됨" : first[1])
                    + " / 2차: " + (second[1] == null ? "로드됨" : second[1]));
            assertNotNull(first[0], what + ": 바닐라·Paper 는 손상 청크를 로그 후 새로 생성한다 — Furnace 는 로드 실패(" + first[1] + ")");
            assertNotNull(second[0], what + ": 다시 요청해도 올라오지 않는다(영구 구멍)");
            assertTrue(((ChunkColumn) first[0]).dirty(), what + ": 새로 생성한 청크는 다음 저장에서 깨진 청크를 덮어야 한다(dirty)");
            assertTrue(w.isChunkLoaded(0, 0));
            Path backups = dir.resolve("region_corrupt");
            assertTrue(java.nio.file.Files.isDirectory(backups), what + ": 원본 백업 폴더");
            try (var files = java.nio.file.Files.list(backups)) {
                var names = files.map(p -> p.getFileName().toString()).toList();
                assertFalse(names.isEmpty(), what + ": 덮이기 전 원본이 남아야 한다");
                assertTrue(names.stream().allMatch(n -> n.startsWith("c.0.0-") && (n.endsWith(".nbt") || n.endsWith(".bin"))), names.toString());
            }
        } finally {
            io.shutdown();
            sched.close();
            store.closeAll();
        }
    }

    @Test
    void corruptZlibPayload_isRegeneratedLikeVanilla(@TempDir Path dir) throws Exception {
        writeGoodChunk(dir);
        corruptZlib(dir);
        assertRegenerates(dir, "깨진 zlib 본문");
    }

    @Test
    void outOfRangePaletteIndex_isRegeneratedLikeVanilla(@TempDir Path dir) throws Exception {
        writeBadPaletteChunk(dir);
        assertRegenerates(dir, "팔레트 범위 밖 인덱스");
    }

    @Test
    void corruptChunk_blockingPathAlsoRegenerates(@TempDir Path dir) throws Exception {
        writeGoodChunk(dir);
        corruptZlib(dir);
        RegionFileStore store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, new AnvilChunkCodec(REG, 0), new SuperflatWorldSource(REG, 0));
        try {
            ChunkColumn col = src.generate(0, 0);
            assertNotNull(col);
            assertTrue(col.dirty());
            assertEquals("minecraft:bedrock", REG.nameOf(col.getBlock(0, -64, 0)), "평지 바닥이 다시 생겼다");
            try (var files = java.nio.file.Files.list(dir.resolve("region_corrupt"))) {
                // zlib 이 깨져 압축 풀기에서 실패 — 압축 본문을 .bin(종류 1바이트 + 본문)으로 남긴다
                var f = files.findFirst().orElseThrow();
                assertTrue(f.getFileName().toString().endsWith(".bin"), f.toString());
                byte[] b = java.nio.file.Files.readAllBytes(f);
                assertEquals(2, b[0], "zlib 압축 종류");
            }
        } finally {
            store.closeAll();
        }
    }

    @Test
    void badPalette_backupIsDecompressedNbt(@TempDir Path dir) throws Exception {
        writeBadPaletteChunk(dir);
        RegionFileStore store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, new AnvilChunkCodec(REG, 0), new SuperflatWorldSource(REG, 0));
        try {
            assertNotNull(src.generate(0, 0));
            try (var files = java.nio.file.Files.list(dir.resolve("region_corrupt"))) {
                var f = files.findFirst().orElseThrow();
                assertTrue(f.getFileName().toString().endsWith(".nbt"), f.toString());
                NbtCompound back = NbtIo.read(java.nio.file.Files.readAllBytes(f));
                assertEquals(256, back.getList("sections").size() > 0
                        ? ((NbtCompound) back.getList("sections").get(0)).getCompound("block_states").getLongArray("data").length : -1,
                        "원본 NBT 그대로");
            }
        } finally {
            store.closeAll();
        }
    }

    @Test
    void newerDataVersionChunk_isNotRegeneratedNorOverwritten(@TempDir Path dir) throws Exception {
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        NbtCompound root = codec.toNbt(new SuperflatWorldSource(REG, 0).generate(0, 0), 0, 0);
        root.putInt("DataVersion", 4189);   // 1.21.4
        byte[] bytes = NbtIo.toBytes(root, "");
        RegionFileStore store = new RegionFileStore(dir);
        store.writeChunk(0, 0, bytes);
        store.closeAll();
        store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(REG, 0));
        var io = Executors.newFixedThreadPool(2);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        World w = new World(src, new ChunkWorkerPool(1), sched, 1, 16, 5, 5, REG.directBits(), 7, io);
        try {
            Object[] first = load(w, sched);
            assertNull(first[0], "새 판 청크는 올리지 않는다");
            Throwable t = (Throwable) first[1];
            while (t != null && !(t instanceof org.domi.furnace.server.world.anvil.NewerDataVersionException)) t = t.getCause();
            assertNotNull(t, "새 판 청크 거부 사유: " + first[1]);
            assertFalse(w.isChunkLoaded(0, 0));
            assertThrows(org.domi.furnace.server.world.anvil.NewerDataVersionException.class, () -> src.generate(0, 0));
            // 거부한 청크는 다시 요청해도 같은 거부(R2: 서버가 도는 동안 다시 읽지 않는다)
            Object[] again = load(w, sched);
            assertNull(again[0]);
            assertArrayEquals(bytes, store.readChunk(0, 0), "디스크의 새 판 청크는 그대로");
            assertFalse(java.nio.file.Files.exists(dir.resolve("region_corrupt")), "깨진 청크로 다루지 않는다");
        } finally {
            io.shutdown();
            sched.close();
            store.closeAll();
        }
    }

    @Test
    void newerDataVersionChunk_opensWithPaperOverride(@TempDir Path dir) throws Exception {
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        NbtCompound root = codec.toNbt(new SuperflatWorldSource(REG, 0).generate(0, 0), 0, 0);
        root.putInt("DataVersion", 4189);
        RegionFileStore store = new RegionFileStore(dir);
        store.writeChunk(0, 0, NbtIo.toBytes(root, ""));
        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(REG, 0));
        String prop = org.domi.furnace.server.world.anvil.NewerDataVersionException.IGNORE_PROPERTY;
        System.setProperty(prop, "true");
        try {
            ChunkColumn col = src.generate(0, 0);
            assertNotNull(col);
            assertFalse(col.dirty(), "디스크에서 읽은 청크");
        } finally {
            System.clearProperty(prop);
            store.closeAll();
        }
    }

    // ---- A05-04: 옛 판 청크는 올리지도, 새로 생성해 덮지도 않는다 ----

    @Test
    void olderDataVersionChunk_isNotRegeneratedNorOverwritten(@TempDir Path dir) throws Exception {
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        NbtCompound root = codec.toNbt(new SuperflatWorldSource(REG, 0).generate(0, 0), 0, 0);
        root.putInt("DataVersion", 3700);   // 1.20.4
        byte[] bytes = NbtIo.toBytes(root, "");
        RegionFileStore store = new RegionFileStore(dir);
        store.writeChunk(0, 0, bytes);
        store.closeAll();
        store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(REG, 0));
        try {
            assertThrows(org.domi.furnace.server.world.anvil.OlderDataVersionException.class, () -> src.generate(0, 0));
            assertThrows(org.domi.furnace.server.world.anvil.OlderDataVersionException.class, () -> src.generate(0, 0));   // 거부 기억
            assertArrayEquals(bytes, store.readChunk(0, 0), "디스크의 옛 판 청크는 그대로");
            assertFalse(java.nio.file.Files.exists(dir.resolve("region_corrupt")), "깨진 청크로 다루지 않는다");
        } finally {
            store.closeAll();
        }
    }

    @Test
    void olderDataVersionChunk_opensWithOverride(@TempDir Path dir) throws Exception {
        AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        NbtCompound root = codec.toNbt(new SuperflatWorldSource(REG, 0).generate(0, 0), 0, 0);
        root.putInt("DataVersion", 3700);
        RegionFileStore store = new RegionFileStore(dir);
        store.writeChunk(0, 0, NbtIo.toBytes(root, ""));
        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(REG, 0));
        String prop = org.domi.furnace.server.world.anvil.OlderDataVersionException.IGNORE_PROPERTY;
        System.setProperty(prop, "true");
        try {
            assertNotNull(src.generate(0, 0));
        } finally {
            System.clearProperty(prop);
            store.closeAll();
        }
    }
}
