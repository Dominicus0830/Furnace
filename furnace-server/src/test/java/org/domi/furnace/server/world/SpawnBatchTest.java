// 스폰 청크 전송이 ChunkBatchStart…청크…ChunkBatchFinished(count)로 감싸지는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
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
        // viewDistance 2 → 바닐라 둥근 모양 45 청크(정사각 5×5=25 아님), regionSize 16 → 전부 region(0,0) 소유.
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 2, 2, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "A", ch);
        p.applyMove(0, -60, 0, 0, 0, true);
        sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
        // 비동기 청크 파이프라인(미리 불러오기 → 본문 준비)이 끝날 때까지 틱을 돌리며 outbound 패킷 id 수집. 배치는 틱마다 준비된 청크만 담으므로 여러 개일 수 있다.
        int expected = ChunkTrackingViewTest.vanillaCount(2);
        List<Integer> ids = new ArrayList<>();
        List<Integer> finishedSizes = new ArrayList<>();
        long chunkCount = 0;
        for (int i = 0; i < 600 && chunkCount < expected; i++) {
            sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
            ch.runPendingTasks();
            Object o;
            while ((o = ch.readOutbound()) != null) {
                ByteBuf b = (ByteBuf) o;
                int id = Types.readVarInt(b);
                ids.add(id);
                if (id == CHUNK_BATCH_FINISHED) finishedSizes.add(Types.readVarInt(b));
                else if (id == LEVEL_CHUNK) chunkCount++;
                b.release();
            }
            Thread.sleep(10);
        }
        // 마지막 Finished 가 나가기까지 한 번 더
        for (int i = 0; i < 100 && ids.lastIndexOf(CHUNK_BATCH_FINISHED) < ids.lastIndexOf(LEVEL_CHUNK); i++) {
            ch.runPendingTasks();
            Object o;
            while ((o = ch.readOutbound()) != null) {
                ByteBuf b = (ByteBuf) o;
                int id = Types.readVarInt(b);
                ids.add(id);
                if (id == CHUNK_BATCH_FINISHED) finishedSizes.add(Types.readVarInt(b));
                b.release();
            }
            Thread.sleep(10);
        }

        assertEquals(expected, chunkCount, "바닐라 둥근 모양 " + expected + "개 청크 전송");
        assertEquals(expected, finishedSizes.stream().mapToInt(Integer::intValue).sum(), "Finished count 합 == 청크 수");
        // 배치마다 Start → 청크들 → Finished(그 배치의 청크 수), 배치끼리 겹치지 않는다
        int open = -1;
        int batchIndex = 0;
        for (int id : ids) {
            if (id == CHUNK_BATCH_START) {
                assertEquals(-1, open, "앞 배치가 닫히기 전에 새 배치가 열렸다");
                open = 0;
            } else if (id == LEVEL_CHUNK) {
                assertTrue(open >= 0, "청크가 배치 밖에서 나갔다");
                open++;
            } else if (id == CHUNK_BATCH_FINISHED) {
                assertTrue(open >= 0, "Finished 가 Start 없이 나갔다");
                assertEquals(finishedSizes.get(batchIndex++), open, "배치 Finished 수 == 그 배치의 청크 수");
                open = -1;
            }
        }
        assertEquals(-1, open, "마지막 배치가 닫혀야 한다");

        io.shutdown();
        sched.close();
    }
}
