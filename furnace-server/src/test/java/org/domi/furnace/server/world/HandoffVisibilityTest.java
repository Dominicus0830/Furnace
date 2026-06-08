// 핸드오프 시 같은 region 관찰자가 플레이어를 despawn하지 않는지(깜빡임 없음) 검증
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

class HandoffVisibilityTest {

    @Test
    void watcher_keepsTracking_whenPeerHandsOffToNeighbor() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 4);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        // regionSize 1 → 16블록 셀. A·B 처음 같은 region(0,0).
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 1, 8, 8, reg.directBits(), 7);
        RegionPos r00 = new RegionPos(0, 0);
        Player a = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        a.applyMove(0, -60, 0, 0, 0, true);
        Player b = new Player(2, UUID.randomUUID(), "B", new EmbeddedChannel());
        b.applyMove(5, -60, 0, 0, 0, true); // 같은 청크(0,0) → region(0,0)
        sched.region(r00).execute(() -> { w.regionAt(r00).adopt(a); w.regionAt(r00).adopt(b); }).get();
        // 첫 틱: A가 B를 추적
        sched.region(r00).execute(() -> w.regionAt(r00).tick()).get();
        assertTrue(a.tracked().containsKey(2), "사전: A가 B 추적해야");

        // B를 인접 region(1,0)으로 이동(20블록=청크1, A 시야 128블록 내)
        b.applyMove(20, -60, 0, 0, 0, true);
        // region(0,0) 틱: B가 핸드오프 — A는 B를 계속 추적해야(despawn 안 함)
        sched.region(r00).execute(() -> w.regionAt(r00).tick()).get();
        assertTrue(a.tracked().containsKey(2), "핸드오프 후에도 A가 B를 계속 추적해야(깜빡임 없음)");
        sched.close();
    }
}
