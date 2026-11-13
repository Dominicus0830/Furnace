// 청크 다시 보내기의 재구독은 지금 구독 중인 플레이어만 바꾼다 — 그 사이 구독을 푼 플레이어를 되살리지 않는다(PR #273 검토 R1).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ResubscribeIfPresentTest {

    @Test
    void onlyReplacesLiveSubscriptions() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
            World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7,
                    Executors.newSingleThreadExecutor());
            ChunkPos cp = new ChunkPos(3, 4);
            Player a = new Player(1, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
            Player b = new Player(2, UUID.randomUUID(), "B", new ConcurrentEmbeddedChannel());
            assertNull(w.resubscribeIfPresent(cp, a), "구독자가 없는 청크");
            assertTrue(w.subscribers(cp).isEmpty(), "새로 만들지 않는다");

            World.Subscription old = w.subscribeChunk(cp, a);
            old.ready = true;
            World.Subscription now = w.resubscribeIfPresent(cp, a);
            assertNotNull(now);
            assertNotSame(old, now);
            assertFalse(now.ready, "새 구독은 아직 받지 않은 상태");
            assertSame(now, w.subscriptionOf(cp, a));

            assertNull(w.resubscribeIfPresent(cp, b), "구독하지 않던 플레이어는 넣지 않는다");
            w.unsubscribeChunk(cp, a);
            assertNull(w.resubscribeIfPresent(cp, a), "그 사이 푼 구독은 되살리지 않는다");
            assertTrue(w.subscribers(cp).isEmpty());
        } finally {
            sched.close();
        }
    }
}
