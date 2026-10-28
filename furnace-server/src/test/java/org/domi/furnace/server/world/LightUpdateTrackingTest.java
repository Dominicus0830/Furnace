// 빛 갱신 대상(ChunkMap.getPlayers(pos, true)): 받은 청크이면서 둘레 8칸 중 받지 않은(구독 없음·전송 전) 청크가 있는 플레이어만. 전송 전 구독은 건너뛴다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.protocol.play.LightUpdate;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class LightUpdateTrackingTest {

    @Test
    void borderAndPendingRules() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, Executors.newSingleThreadExecutor());
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        ChunkPos c = new ChunkPos(0, 0);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.subscribeChunk(new ChunkPos(dx, dz), p).ready = true;
        assertFalse(w.onTrackedBorder(c, p), "둘레를 모두 받았으면 안쪽");
        w.subscriptionOf(new ChunkPos(1, 1), p).ready = false;
        assertTrue(w.onTrackedBorder(c, p), "이웃이 전송 전이면 가장자리");
        w.subscriptionOf(new ChunkPos(1, 1), p).ready = true;
        w.unsubscribeChunk(new ChunkPos(-1, 0), p);
        assertTrue(w.onTrackedBorder(c, p), "이웃 구독이 없으면 가장자리");

        // 자기 청크가 전송 전이면 보내지도 보류하지도 않는다(빛은 청크 패킷에 들어간다)
        World.Subscription own = w.subscriptionOf(c, p);
        own.ready = false;
        w.broadcastLightUpdate(c, new LightUpdate(0, 0, 0, 0, 0, 0, new byte[0][], new byte[0][]));
        assertTrue(own.pending.isEmpty());
    }
}
