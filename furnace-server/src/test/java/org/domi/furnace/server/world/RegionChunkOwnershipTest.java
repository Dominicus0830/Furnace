// region이 청크를 1회 로드 후 보관(동일 인스턴스)하는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class RegionChunkOwnershipTest {

    @Test
    void chunkAt_loadsOnceAndRetains() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
        RegionPos rp = new RegionPos(0, 0);
        ChunkColumn[] seen = new ChunkColumn[2];
        ChunkPos cp = new ChunkPos(0, 0);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            seen[0] = r.chunkAtBlocking(cp);
            seen[1] = r.chunkAtBlocking(cp);
        }).get();
        assertNotNull(seen[0]);
        assertSame(seen[0], seen[1]); // 보관 — 두 번째 호출 동일 인스턴스
        sched.close();
    }
}
