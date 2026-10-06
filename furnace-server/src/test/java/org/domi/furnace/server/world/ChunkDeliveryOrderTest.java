// 청크 스냅샷 이후·전송 이전의 블록 변경이 청크 "뒤에" 도착하는지(변경 유실 방지) + 구독 해제 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkDeliveryOrderTest {

    private static final RegionPos R00 = new RegionPos(0, 0);
    private static final ChunkPos C00 = new ChunkPos(0, 0);

    private static List<Integer> ids(EmbeddedChannel ch) {
        List<Integer> ids = new ArrayList<>();
        for (ByteBuf b : BlockBreakTest.drain(ch)) { ids.add(Types.readVarInt(b)); b.release(); }
        return ids;
    }

    @Test
    void editAfterSnapshot_isDeliveredAfterChunk() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkWorkerPool pool = new ChunkWorkerPool(1);
        World w = new World(new SuperflatWorldSource(reg, 0), pool, sched, 1, 16, 1, 1, reg.directBits(), 7);
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "P", ch);

        CountDownLatch gate = new CountDownLatch(1);          // 직렬화를 막아 "스냅샷 후·전송 전" 구간을 만든다
        pool.submit(() -> { try { gate.await(); } catch (InterruptedException ignored) { } return null; });
        sched.region(R00).execute(() -> {
            w.regionAt(R00).openChunk(C00, p);                // 스냅샷(파괴 전)
            w.regionAt(R00).breakBlock(0, -61, 0);            // 곧바로 파괴 → 아직 ready 아님
        }).get();
        gate.countDown();
        pool.submit(() -> null).get();
        sched.region(R00).execute(() -> { }).get();

        assertEquals(List.of(0x27, 0x09), ids(ch), "청크 → BlockUpdate 순이어야 클라가 파괴를 놓치지 않는다");
        sched.close();
    }

    @Test
    void closedBeforeDelivery_dropsChunkAndUpdates() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkWorkerPool pool = new ChunkWorkerPool(1);
        World w = new World(new SuperflatWorldSource(reg, 0), pool, sched, 1, 16, 1, 1, reg.directBits(), 7);
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "P", ch);

        CountDownLatch gate = new CountDownLatch(1);
        pool.submit(() -> { try { gate.await(); } catch (InterruptedException ignored) { } return null; });
        sched.region(R00).execute(() -> {
            w.regionAt(R00).openChunk(C00, p);
            w.regionAt(R00).closeChunk(C00, p);               // 전달 전에 언로드
            w.regionAt(R00).breakBlock(0, -61, 0);
        }).get();
        gate.countDown();
        pool.submit(() -> null).get();
        sched.region(R00).execute(() -> { }).get();

        assertEquals(List.of(), ids(ch));
        sched.close();
    }
}
