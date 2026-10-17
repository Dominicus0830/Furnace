// 아무것도 없는 곳으로 보낸 작업이 만든 region 은 곧 사라지고, 플레이어가 있는 region 은 남는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class EmptyRegionEvictionTest {

    private static World world(FurnaceSchedulerImpl sched) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2),
                sched, sched.regionThreadCount(), 16, 1, 1, reg.directBits(), 7,
                java.util.concurrent.Executors.newSingleThreadExecutor());
    }

    @Test
    void emptyRegion_disappears_playerRegionKept() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(10_000_000L, 1);
        World w = world(sched);
        try {
            // 1) 아무 청크도 없는 곳으로 작업 → 그 구역의 region 이 생겼다가 다음 틱 끝 정리에서 사라진다
            RegionPos empty = new RegionPos(5000, 5000);
            sched.region(empty).execute(() -> w.regionAt(empty)).get();
            awaitRegions(w, 0);

            // 2) 플레이어가 편입된 region 은 청크가 없어도 남는다
            Player p = new Player(1, UUID.randomUUID(), "Dom", new EmbeddedChannel());
            p.publishPosition(8, -60, 8, 0, 0, true);
            p.drainPending();
            RegionPos prp = new RegionPos(0, 0);
            sched.region(prp).execute(() -> w.regionAt(prp).adopt(p)).get();
            Thread.sleep(100);
            assertEquals(1, w.regionCount(), "플레이어 region 은 유지");
        } finally {
            sched.close();
        }
    }

    private static void awaitRegions(World w, int n) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (w.regionCount() != n && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertEquals(n, w.regionCount());
    }
}
