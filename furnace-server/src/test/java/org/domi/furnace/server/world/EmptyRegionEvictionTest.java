// 빈 region(플레이어·청크·로딩 0)이 tickThread서 제거되고, 플레이어 있는 region은 유지되는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class EmptyRegionEvictionTest {

    private static World world(FurnaceSchedulerImpl sched) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2),
                sched, sched.regionThreadCount(), 16, 1, 1, reg.directBits(), 7,
                java.util.concurrent.Executors.newSingleThreadExecutor());
    }

    @Test
    void emptyRegion_removedByTick_playerRegionKept() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1); // region thread 1개
        World w = world(sched);

        // 1) 플레이어 없이 빈 region 생성
        RegionPos empty = new RegionPos(50, 50);
        sched.region(empty).execute(() -> w.regionAt(empty)).get();
        assertEquals(1, w.regionCount(), "빈 region 생성됨");

        // 2) tick 1회 → 빈 region 제거
        sched.region(empty).execute(() -> w.tickThread(0)).get();
        assertEquals(0, w.regionCount(), "빈 region이 tick서 제거됨");

        // 3) 플레이어 편입된 region은 tick 후에도 유지. (8,8)은 region(0,0) — drain 후에도 같은 region이라 핸드오프 없음.
        Player p = new Player(1, UUID.randomUUID(), "Dom", new EmbeddedChannel());
        p.publishPosition(8, -60, 8, 0, 0, true);
        RegionPos prp = p.regionPos(16);
        sched.region(prp).execute(() -> w.regionAt(prp).adopt(p)).get();
        sched.region(prp).execute(() -> w.tickThread(0)).get();
        assertTrue(w.regionCount() >= 1, "플레이어 region은 유지(+로드된 청크 region)");

        sched.close();
    }
}
