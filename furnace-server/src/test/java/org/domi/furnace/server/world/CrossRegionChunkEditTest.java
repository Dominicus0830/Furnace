// 교차region: 소유 region이 편집한 청크가 canonical 스냅샷 경로에 반영되는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class CrossRegionChunkEditTest {

    @Test
    void breakInOwnerRegion_reflectedInSnapshot() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 4);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        // regionSize 1 → chunk (5,0)은 region (5,0) 소유, 다른 스레드(floorMod(5*31,4)=3)
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 1, 8, 8, reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
        ChunkPos cp = new ChunkPos(5, 0);
        int bx = 5 * 16, by = -61, bz = 0; // 청크 (5,0) 안의 블록
        RegionPos owner = RegionGrid.regionPosOfChunk(5, 0, 1);

        // 소유 region에서 블록 파괴
        sched.region(owner).execute(() -> w.regionAt(owner).breakBlock(bx, by, bz)).get();

        // canonical 스냅샷 경로(소유 region에 hop)로 청크 조회 → 편집 반영돼야
        ChunkSnapshot snap = w.snapshotChunk(cp).get();
        int sectionIdx = (by - ChunkColumn.MIN_Y) >> 4;
        int local = (by - ChunkColumn.MIN_Y) & 15;
        assertEquals(ChunkSection.AIR, snap.sections()[sectionIdx].blocks()[local * 256]);
        sched.close();
    }
}
