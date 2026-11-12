// region 스레드서 플레이어 편입·틱 시 청크 로드, 비소유 스레드 접근 시 assert 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class RegionTickTest {

    private static World world(FurnaceSchedulerImpl sched) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2),
                sched, sched.regionThreadCount(), 16, 2, 2, reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
    }

    @Test
    void adoptAndTick_onRegionThread_loadsChunks() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = world(sched);
        Player p = new Player(1, UUID.randomUUID(), "Dom", new ConcurrentEmbeddedChannel());
        p.publishPosition(0, -60, 0, 0, 0, true);
        RegionPos rp = new RegionPos(0, 0);   // 청크 좌표
        sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertEquals(ChunkTrackingViewTest.vanillaCount(2), p.loadedChunks().size()); // viewDistance 2 → 바닐라 둥근 모양 45칸(정사각 5×5 아님)
        sched.close();
    }

    @Test
    void mutatingRegion_fromWrongThread_assertFails() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = world(sched);
        RegionPos rp = new RegionPos(0, 0);
        Player p = new Player(1, UUID.randomUUID(), "Dom", new ConcurrentEmbeddedChannel());
        // 메인 스레드서 직접 adopt → 비소유 → AssertionError(-ea)
        assertThrows(Throwable.class, () -> w.regionAt(rp).adopt(p));
        sched.close();
    }
}
