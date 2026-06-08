// 인접 region 스냅샷에 있는 플레이어가 가시성 후보로 spawn되는지 검증
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

class CrossRegionVisibilityTest {

    @Test
    void neighborRegionPlayer_isTracked() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 1, 5, 5, reg.directBits(), 7); // regionSize 1 → 16블록
        // A는 region(0,0), B는 region(1,0) — 다른 region이지만 16블록 거리(view 5청크=80블록 내)
        Player a = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        a.applyMove(0, -60, 0, 0, 0, true);
        Player b = new Player(2, UUID.randomUUID(), "B", new EmbeddedChannel());
        b.applyMove(16, -60, 0, 0, 0, true);
        RegionPos ra = a.regionPos(1), rb = b.regionPos(1);
        assertNotEquals(ra, rb); // 다른 region 확인
        sched.region(ra).execute(() -> w.regionAt(ra).adopt(a)).get();
        sched.region(rb).execute(() -> w.regionAt(rb).adopt(b)).get();
        // B 틱 먼저(스냅샷 공개), 그 다음 A 틱(B를 후보로 봄)
        sched.region(rb).execute(() -> w.regionAt(rb).tick()).get();
        sched.region(ra).execute(() -> w.regionAt(ra).tick()).get();
        assertTrue(a.tracked().containsKey(2), "A가 인접 region의 B를 추적해야"); // 교차region 가시성
        sched.close();
    }
}
