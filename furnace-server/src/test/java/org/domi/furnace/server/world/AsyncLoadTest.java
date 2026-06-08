// loadChunk async: 부재 시 generate, in-flight dedup, 재호출 동일 인스턴스
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

class AsyncLoadTest {

    @Test
    void loadChunk_generatesAndRetains(@TempDir Path dir) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, new AnvilChunkCodec(reg, 0),
                new SuperflatWorldSource(reg, 0));
        var io = Executors.newFixedThreadPool(2);
        World w = new World(src, new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 5, 5, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        ChunkPos cp = new ChunkPos(0, 0);
        ChunkColumn c1 = sched.region(rp).submit(() -> w.regionAt(rp).loadChunk(cp)).get().get();
        ChunkColumn c2 = sched.region(rp).submit(() -> w.regionAt(rp).loadChunk(cp)).get().get();
        assertNotNull(c1);
        assertSame(c1, c2);
        assertFalse(c1.dirty()); // 로드 직후 clean
        io.shutdown();
        store.closeAll();
        sched.close();
    }
}
