// 스폰 청크 전송이 ChunkBatchStart…청크…ChunkBatchFinished(count)로 감싸지는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class SpawnBatchTest {

    private static final int CHUNK_BATCH_START = 0x0D;
    private static final int CHUNK_BATCH_FINISHED = 0x0C;
    private static final int LEVEL_CHUNK = 0x27;

    @Test
    void spawnChunks_wrappedInBatch() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        // viewDistance 1 → 3x3=9 청크, regionSize 16 → 전부 region(0,0) 소유.
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 1, 1, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "A", ch);
        p.applyMove(0, -60, 0, 0, 0, true);
        sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();

        // 비동기 청크 파이프라인 완료까지 폴링하며 outbound 패킷 id 수집.
        List<Integer> ids = new ArrayList<>();
        int finishedSize = -1;
        for (int i = 0; i < 300 && finishedSize < 0; i++) {
            ch.runPendingTasks();
            Object o;
            while ((o = ch.readOutbound()) != null) {
                ByteBuf b = (ByteBuf) o;
                int id = Types.readVarInt(b);
                ids.add(id);
                if (id == CHUNK_BATCH_FINISHED) finishedSize = Types.readVarInt(b);
                b.release();
            }
            if (finishedSize < 0) Thread.sleep(10);
        }

        int start = ids.indexOf(CHUNK_BATCH_START);
        int finished = ids.lastIndexOf(CHUNK_BATCH_FINISHED);
        long chunkCount = ids.stream().filter(id -> id == LEVEL_CHUNK).count();
        int firstChunk = ids.indexOf(LEVEL_CHUNK);
        int lastChunk = ids.lastIndexOf(LEVEL_CHUNK);

        assertTrue(start >= 0, "ChunkBatchStart 전송돼야");
        assertTrue(finished >= 0, "ChunkBatchFinished 전송돼야");
        assertEquals(9, chunkCount, "9개 청크 전송");
        assertEquals(9, finishedSize, "Finished count == 청크 수");
        assertTrue(start < firstChunk, "Start가 첫 청크 앞");
        assertTrue(lastChunk < finished, "Finished가 마지막 청크 뒤");

        io.shutdown();
        sched.close();
    }
}
