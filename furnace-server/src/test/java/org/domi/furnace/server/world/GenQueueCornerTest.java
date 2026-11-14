// T-170 회귀(PR #371 검토 R1): 클라 시야 < 서버 시야면 모서리(서버 시야 정사각 ∖ 클라 둥근 모양) 미리 불러오기가 시야 밖 취소에 걸린다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class GenQueueCornerTest {

    private void run(int serverVd, int clientVd, boolean genQueue) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ExecutorService io = Executors.newSingleThreadExecutor();
        // 서버 기본값과 같은 설정(fromSystem: 차례 켬)
        GenQueue q = new GenQueue(2, GenQueue.Settings.fromSystem(2, World.defaultMaxChunkSendsInFlight()));
        World w = genQueue
                ? new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, 1, 16, serverVd, serverVd, reg.directBits(), 7, io, q)
                : new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, 1, 16, serverVd, serverVd, reg.directBits(), 7, io);
        try {
            EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
            Player p = new Player(1, UUID.randomUUID(), "A", ch);
            p.viewDistance(clientVd);
            p.applyMove(8, -60, 8, 0, 0, true);
            RegionPos rp = new RegionPos(0, 0);
            sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
            for (int t = 0; t < 300; t++) {
                sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
                ch.runPendingTasks();
                Object o;
                while ((o = ch.readOutbound()) != null) ((ByteBuf) o).release();
                Thread.sleep(5);
            }
            List<ChunkPos> missing = new ArrayList<>();
            for (int x = -serverVd; x <= serverVd; x++) for (int z = -serverVd; z <= serverVd; z++)
                if (w.loadedChunk(x, z) == null) missing.add(new ChunkPos(x, z));
            assertEquals(List.of(), missing, "서버 시야 정사각 전부(바닐라 플레이어 티켓) — genQueue=" + genQueue);
        } finally {
            q.shutdown();
            q.awaitTermination(5, TimeUnit.SECONDS);
            io.shutdown();
            sched.close();
        }
    }

    @Test void plainPool_clientVdBelowServer() throws Exception { run(8, 3, false); }

    @Test void genQueue_clientVdBelowServer() throws Exception { run(8, 3, true); }

    @Test void genQueue_sameVd() throws Exception { run(6, 6, true); }
}
