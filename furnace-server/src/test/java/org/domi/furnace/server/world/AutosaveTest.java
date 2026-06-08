// region 틱이 autosaveTicks마다 dirty 청크를 디스크에 저장하는지(공유 플래그 경쟁 없이) 검증
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

class AutosaveTest {

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
            w.regionAt(rp).breakBlock(0, -61, 0);
            for (int i = 0; i < 3; i++) w.regionAt(rp).tick();
        }).get();
        Thread.sleep(200); // IO flush 대기

        // 새 store로 디스크서 직접 로드 → air 유지(autosave 동작 확인).
        RegionFileStore store2 = new RegionFileStore(dir);
        AnvilWorldSource src2 = new AnvilWorldSource(dir, store2, codec, new SuperflatWorldSource(reg, 0));
        assertEquals(ChunkSection.AIR, src2.generate(0, 0).getBlock(0, -61, 0));
        store2.closeAll();
        io.shutdown(); store.closeAll(); sched.close();
    }
}
