package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class WorldSnapshotCacheTest {

    @Test
    void snapshotChunk_reusesCleanSnapshot_andInvalidatesOnEdit() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 8, 8, reg.directBits(), 7, io);
        ChunkPos cp = new ChunkPos(0, 0);
        RegionPos owner = new RegionPos(0, 0);

        ChunkSnapshot first = w.snapshotChunk(cp).get();
        ChunkSnapshot second = w.snapshotChunk(cp).get();
        assertSame(first, second, "clean chunk snapshot은 재사용해야");

        sched.region(owner).execute(() -> w.regionAt(owner).breakBlock(0, -61, 0)).get();

        ChunkSnapshot third = w.snapshotChunk(cp).get();
        assertNotSame(first, third, "청크 편집 후에는 snapshot cache가 무효화돼야");

        io.shutdown();
        sched.close();
    }
}
