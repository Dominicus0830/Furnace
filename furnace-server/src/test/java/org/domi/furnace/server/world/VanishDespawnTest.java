// 교차region 관찰자가 사라진 엔티티(퇴장)를 grace 틱 후 despawn 하는지 검증(고스트 방지)
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

class VanishDespawnTest {

    @Test
    void crossRegionWatcher_despawnsDisconnectedPeer_afterGrace() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 4);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        // regionSize 1 → 16블록 셀. A=region(0,0), B=region(1,0). 거리 20<128(view 8청크).
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 1, 8, 8, reg.directBits(), 7,
                java.util.concurrent.Executors.newSingleThreadExecutor());
        RegionPos r00 = new RegionPos(0, 0);
        RegionPos r10 = new RegionPos(1, 0);
        Player a = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        a.applyMove(0, -60, 0, 0, 0, true);
        Player b = new Player(2, UUID.randomUUID(), "B", new EmbeddedChannel());
        b.applyMove(20, -60, 0, 0, 0, true);
        sched.region(r00).execute(() -> w.regionAt(r00).adopt(a)).get();
        sched.region(r10).execute(() -> w.regionAt(r10).adopt(b)).get();

        // B 스냅샷 공개 → A가 B 추적 시작
        sched.region(r10).execute(() -> w.regionAt(r10).tick()).get();
        sched.region(r00).execute(() -> w.regionAt(r00).tick()).get();
        assertTrue(a.tracked().containsKey(2), "사전: A가 교차region B를 추적해야");

        // B 접속 해제 → B의 region 틱이 B 제거(+빈 스냅샷 공개)
        b.markDisconnected();
        sched.region(r10).execute(() -> w.regionAt(r10).tick()).get();

        // A의 region 틱 grace 미만 → 아직 추적(즉시 despawn 안 함)
        sched.region(r00).execute(() -> w.regionAt(r00).tick()).get();
        assertTrue(a.tracked().containsKey(2), "grace 미만: 아직 추적(핸드오프 깜빡임 방지)");

        // grace 도달까지 추가 틱 → despawn
        sched.region(r00).execute(() -> w.regionAt(r00).tick()).get();
        sched.region(r00).execute(() -> w.regionAt(r00).tick()).get();
        assertFalse(a.tracked().containsKey(2), "grace 후: A가 사라진 B를 despawn해야(고스트 없음)");
        sched.close();
    }
}
