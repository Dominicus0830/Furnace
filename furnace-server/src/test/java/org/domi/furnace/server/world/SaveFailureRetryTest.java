// I-3: 저장 IO 실패는 백오프 재시도하고, 끝내 실패하면 주인 region 에서 청크를 다시 dirty 로 되돌려 다음 저장에 싣는다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SaveFailureRetryTest {

    private long[] savedBackoff;

    // 블록 동작 표(가마솥 등)가 정적 초기화에서 아이템 id 를 찾는다 — 먼저 설치하지 않으면 같은 JVM 의 뒤 테스트까지 깨진다
    @org.junit.jupiter.api.BeforeAll
    static void installItems() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void fastBackoff() {
        savedBackoff = World.saveRetryBackoffMs;
        World.saveRetryBackoffMs = new long[] {1, 1, 1};
    }

    @AfterEach
    void restoreBackoff() {
        World.saveRetryBackoffMs = savedBackoff;
    }

    @Test
    void transientFailure_retriedUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(World.runWithRetry(() -> {
            if (calls.incrementAndGet() < 3) throw new java.io.IOException("주입");
        }, "t"));
        assertEquals(3, calls.get());
    }

    @Test
    void persistentFailure_givesUpAfterCap() {
        AtomicInteger calls = new AtomicInteger();
        assertFalse(World.runWithRetry(() -> {
            calls.incrementAndGet();
            throw new java.io.IOException("주입");
        }, "t"));
        assertEquals(4, calls.get(), "첫 시도 + 백오프 3회");
    }

    @Test
    void failedSave_restoresDirty_andNextSaveSucceeds(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(reg, 0));
        var io = Executors.newFixedThreadPool(2);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        World w = new World(src, new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        ChunkPos cp = new ChunkPos(0, 0);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.chunkAt(cp);
            r.breakBlock(0, -61, 0);
        }).get();

        // 실패 주입: 열린 핸들을 닫고 region 디렉토리 자리에 일반 파일을 둔다 → 새 RegionFile 생성이 IOException
        store.closeAll();
        Path regionDir = dir.resolve("region");
        if (Files.isDirectory(regionDir)) {
            try (var s = Files.list(regionDir)) { for (Path p : s.toList()) Files.delete(p); }
            Files.delete(regionDir);
        }
        Files.writeString(regionDir, "not a dir");

        sched.region(rp).execute(() -> w.regionAt(rp).saveDirtyChunks()).get();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (w.saveFailures() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
        assertEquals(1, w.saveFailures());
        // 되돌리기는 region 스레드로 보내진다 — 그 뒤에 줄 선 작업으로 확인
        boolean[] dirty = new boolean[1];
        int[] count = new int[1];
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            sched.region(rp).execute(() -> {
                Region r = w.regionAt(rp);
                dirty[0] = r.chunkAt(cp).dirty();
                count[0] = r.dirtyChunkCount();
            }).get();
        } while (!dirty[0] && System.nanoTime() < deadline);
        assertTrue(dirty[0], "실패한 저장은 청크를 다시 dirty 로");
        assertEquals(1, count[0]);

        // 장애 해소 후 다음 저장이 성공하고 편집이 디스크에 남는다
        Files.delete(regionDir);
        sched.region(rp).execute(() -> w.regionAt(rp).saveDirtyChunks()).get();
        io.shutdown();
        assertTrue(io.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(1, w.saveFailures());
        store.closeAll();
        RegionFileStore check = new RegionFileStore(dir);
        AnvilWorldSource reloaded = new AnvilWorldSource(dir, check, codec, new SuperflatWorldSource(reg, 0));
        assertEquals(ChunkSection.AIR, reloaded.generate(0, 0).getBlock(0, -61, 0));
        check.closeAll();
        sched.close();
    }
}
