// 청크가 "이번에 생성기가 만든 것"인지 — 저장 안 된 청크는 새 청크, 디스크에서 읽은 청크는 아니다(ChunkLoadEvent#isNewChunk 의 근거)
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkNewlyCreatedTest {

    @Test
    void generatedChunkIsNewAndReloadedChunkIsNot(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(reg, 0));
        // 디스크에 없는 청크: 새로 만든다(동기·비동기 경로 모두)
        assertTrue(src.generate(5, 5).newlyCreated(), "동기 경로");
        var asyncExec = Executors.newFixedThreadPool(2);
        assertTrue(src.loadChunkAsync(6, 6, asyncExec, asyncExec).get().newlyCreated(), "비동기 경로");

        // (0,0) 을 만들어 저장한 뒤 새 저장소로 다시 읽으면 새 청크가 아니다
        var io = Executors.newFixedThreadPool(2);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = new World(src, new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> {
            w.regionAt(rp).breakBlock(0, -61, 0);
            w.regionAt(rp).saveDirtyChunks();
        }).get();
        io.shutdown();
        assertTrue(io.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS), "IO 풀 미종료");

        RegionFileStore store2 = new RegionFileStore(dir);
        AnvilWorldSource src2 = new AnvilWorldSource(dir, store2, codec, new SuperflatWorldSource(reg, 0));
        assertFalse(src2.generate(0, 0).newlyCreated(), "저장돼 있던 청크는 새 청크가 아니다");
        assertFalse(src2.loadChunkAsync(0, 0, asyncExec, asyncExec).get().newlyCreated(), "비동기 경로도 같다");
        asyncExec.shutdown();
        store2.closeAll();
        store.closeAll();
        sched.close();
    }
}
