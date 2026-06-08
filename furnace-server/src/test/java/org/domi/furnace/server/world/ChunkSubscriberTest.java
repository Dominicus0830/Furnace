// 청크 구독 등록/해제/조회 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Set;
import java.util.UUID;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkSubscriberTest {

    @Test
    void subscribe_unsubscribe() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        ChunkPos cp = new ChunkPos(0, 0);
        assertTrue(w.subscribers(cp).isEmpty());
        w.subscribeChunk(cp, p);
        assertEquals(Set.of(p), w.subscribers(cp));
        w.unsubscribeChunk(cp, p);
        assertTrue(w.subscribers(cp).isEmpty());
        sched.close();
    }
}
