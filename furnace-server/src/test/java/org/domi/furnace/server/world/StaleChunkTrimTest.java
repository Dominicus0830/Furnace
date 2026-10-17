// 큰 뷰 거리에서 플레이어가 먼 곳으로 이동하면 이미 시야 밖이 된 청크는 대기열/구독에서 정리돼야 한다(>512 대기 분기 버그)
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class StaleChunkTrimTest {

    @Test
    void teleportFar_dropsOldViewChunks_evenWhenManyPending() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 20, 20,
                reg.directBits(), 7, io);
        Player p = new Player(1, UUID.randomUUID(), "p", new EmbeddedChannel());
        p.viewDistance(20);
        p.publishPosition(8, -60, 8, 0, 0, true);
        p.drainPending();
        RegionPos rp = new RegionPos(0, 0);
        RegionPos far = new RegionPos(400, 0);   // 청크 좌표
        try {
            sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
            sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();   // 41x41=1681청크 큐잉(>512)
            // 아직 대부분 대기 중일 때 먼 곳으로 이동
            p.publishPosition(16 * 400 + 8, -60, 8, 0, 0, true);
            sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();    // drain 후 먼 region으로 핸드오프
            sched.region(far).execute(() -> {}).get();                       // 핸드오프 adopt 완료 대기
            sched.region(far).execute(() -> w.regionAt(far).tick()).get();
            boolean[] stale = new boolean[1];
            sched.region(far).execute(() -> {
                for (ChunkPos cp : p.loadedChunks()) if (cp.x() < 100) stale[0] = true;
                for (ChunkPos cp : p.pendingChunks()) if (cp.x() < 100) stale[0] = true;
            }).get();
            assertFalse(stale[0], "시야 밖이 된 옛 청크가 구독/대기열에 남아 있다");
        } finally {
            io.shutdown();
            sched.close();
        }
    }
}
