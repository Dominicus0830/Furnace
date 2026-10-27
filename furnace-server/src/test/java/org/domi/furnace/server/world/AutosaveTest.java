// region 틱이 autosaveTicks마다 dirty 청크를 디스크에 저장하는지(공유 플래그 경쟁 없이) 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AutosaveTest {

    private static void waitFor(BooleanSupplier condition, String description) throws Exception {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        fail("timeout waiting for " + description);
    }

    private static int loadBlock(Path dir, BlockRegistry reg, AnvilChunkCodec codec,
                                 int chunkX, int chunkZ, int blockX, int blockY, int blockZ) {
        RegionFileStore store = new RegionFileStore(dir);
        try {
            AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(reg, 0));
            return src.generate(chunkX, chunkZ).getBlock(blockX, blockY, blockZ);
        } finally {
            try {
                store.closeAll();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void region_tracksOnlyDirtyChunks(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(reg, 0));
        var io = Executors.newFixedThreadPool(2);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = new World(src, new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 5, 5, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);

        sched.region(rp).execute(() -> {
            Region region = w.regionAt(rp);
            region.chunkAtBlocking(new ChunkPos(0, 0));
            region.chunkAtBlocking(new ChunkPos(1, 0));
            region.breakBlock(0, -61, 0);
            assertEquals(2, region.dirtyChunkCount(), "새로 생성된 두 청크 모두 저장 대상(바닐라)");
            region.saveDirtyChunks();
            assertEquals(0, region.dirtyChunkCount());
        }).get();
        Thread.sleep(200);

        io.shutdown();
        store.closeAll();
        sched.close();
    }

    @Test
    void autosave_savesDirtyChunk_afterInterval(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(reg, 0));
        var io = Executors.newFixedThreadPool(2);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = new World(src, new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 5, 5, reg.directBits(), 7, io);
        w.configurePersistence(3, 100); // autosave 3틱마다, eviction grace 100틱
        RegionPos rp = new RegionPos(0, 0);
        // 편집 후 region을 3틱 → autosave가 dirty 청크 저장(종료 flush 없이).
        sched.region(rp).execute(() -> {
            Region region = w.regionAt(rp);
            region.breakBlock(0, -61, 0);
            for (int i = 0; i < 3; i++) region.tick();
            assertEquals(0, region.dirtyChunkCount(), "autosave tick should drain dirty chunk set");
        }).get();

        // 새 store는 매 poll마다 다시 열어 헤더 캐시가 외부 write를 놓치지 않게 한다.
        waitFor(() -> loadBlock(dir, reg, codec, 0, 0, 0, -61, 0) == ChunkSection.AIR,
                "autosave write for chunk 0,0");
        assertEquals(ChunkSection.AIR, loadBlock(dir, reg, codec, 0, 0, 0, -61, 0));
        io.shutdown(); store.closeAll(); sched.close();
    }
}
