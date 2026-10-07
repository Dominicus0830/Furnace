// dirty 청크 저장 → 디스크서 재로드 시 편집 유지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
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

class ChunkSaveTest {

    @Test
    void saveDirty_thenReload_keepsEdit(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(reg, 0));
        var io = Executors.newFixedThreadPool(2);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = new World(src, new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 5, 5, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        // 편집 후 저장
        sched.region(rp).execute(() -> {
            w.regionAt(rp).breakBlock(0, -61, 0);
            w.regionAt(rp).saveDirtyChunks(); // 동기 트리거(테스트)
        }).get();
        // IO 풀의 저장 태스크는 위 region 태스크가 끝나기 전에 이미 제출됐다 — 풀을 닫고 비워 결정적으로 기다린다(고정 sleep 금지).
        io.shutdown();
        assertTrue(io.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS), "IO 풀 미종료");

        // 새 store로 디스크서 직접 로드 → air 유지
        RegionFileStore store2 = new RegionFileStore(dir);
        AnvilWorldSource src2 = new AnvilWorldSource(dir, store2, codec, new SuperflatWorldSource(reg, 0));
        assertEquals(ChunkSection.AIR, src2.generate(0, 0).getBlock(0, -61, 0));
        store2.closeAll();
        io.shutdown(); store.closeAll(); sched.close();
    }
}
