// 같은 청크를 여러 스레드(region/Netty)가 동시에 구독·해제해도 구독이 고아 맵에 들어가 사라지지 않는지 스트레스 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkSubscriptionRaceTest {

    @Test
    void concurrentSubscribeUnsubscribe_neverLosesALiveSubscription() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, io);
        ChunkPos cp = new ChunkPos(0, 0);
        Player stable = new Player(1, UUID.randomUUID(), "stable", new EmbeddedChannel());
        List<Player> churners = new ArrayList<>();
        for (int i = 0; i < 4; i++) churners.add(new Player(10 + i, UUID.randomUUID(), "c" + i, new EmbeddedChannel()));
        var pool = Executors.newFixedThreadPool(5);
        try {
            for (int round = 0; round < 3000; round++) {
                CountDownLatch go = new CountDownLatch(1);
                List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
                for (Player c : churners) {
                    fs.add(pool.submit(() -> { go.await(); w.subscribeChunk(cp, c); w.unsubscribeChunk(cp, c); return null; }));
                }
                fs.add(pool.submit(() -> { go.await(); w.subscribeChunk(cp, stable); return null; }));
                go.countDown();
                for (var f : fs) f.get();
                assertNotNull(w.subscriptionOf(cp, stable), "라운드 " + round + ": 구독이 고아 맵으로 사라졌다");
                w.unsubscribeChunk(cp, stable);
            }
        } finally {
            pool.shutdown();
            io.shutdown();
            sched.close();
        }
    }
}
