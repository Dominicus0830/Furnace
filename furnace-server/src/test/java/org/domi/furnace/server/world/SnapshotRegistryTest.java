// 스냅샷 공개/반경 수집, neighborRadius 계산 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityView;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class SnapshotRegistryTest {

    private static World world(FurnaceSchedulerImpl sched, int regionSize, int viewDistance) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), regionSize, viewDistance, reg.directBits(), 7);
    }

    @Test
    void candidatesAround_collectsNeighbors() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = world(sched, 16, 5);
        EntityView v = new EntityView(1, UUID.randomUUID(), "A", 0, -60, 0, 0, 0, true);
        w.publishSnapshot(new RegionPos(0, 0), List.of(v));
        w.publishSnapshot(new RegionPos(1, 0), List.of(
                new EntityView(2, UUID.randomUUID(), "B", 0, -60, 0, 0, 0, true)));
        List<EntityView> got = w.candidatesAround(new RegionPos(0, 0), 1);
        assertEquals(2, got.size()); // (0,0)+(1,0) 둘 다 수집
        sched.close();
    }

    @Test
    void neighborRadius_coversViewDistance() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        World w = world(sched, 4, 10); // vd 10, regionSize 4 → ceil(10/4)+1 = 4
        assertEquals(4, w.neighborRadius());
        sched.close();
    }
}
