// 청크 스냅샷 이후·전송 이전의 블록 변경이 청크 "뒤에" 도착하는지(변경 유실 방지), 언로드된 구독은 버려지는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkDeliveryOrderTest {

    private static final RegionPos R00 = new RegionPos(0, 0);
    private static final ChunkPos C00 = new ChunkPos(0, 0);

    private static List<Integer> ids(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<Integer> ids = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            ids.add(Types.readVarInt(b));
            b.release();
        }
        return ids;
    }

    private static World world(FurnaceSchedulerImpl sched, ChunkWorkerPool pool, BlockRegistry reg) {
        return new World(new SuperflatWorldSource(reg, 0), pool, sched, 1, 16, 2, 2, reg.directBits(), 7,
                Executors.newSingleThreadExecutor());
    }

    /** 직렬화 풀을 막아 "스냅샷 이후·전송 이전" 구간을 만든 뒤 편집하면, 변경은 반드시 그 청크 패킷 뒤에 와야 한다. */
    @Test
    void editAfterSnapshot_isDeliveredAfterThatChunk() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkWorkerPool pool = new ChunkWorkerPool(1);
        World w = world(sched, pool, reg);
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "P", ch);

        CountDownLatch gate = new CountDownLatch(1);
        pool.submit(() -> { try { gate.await(); } catch (InterruptedException ignored) { } return null; });
        sched.region(R00).execute(() -> { Region r = w.regionAt(R00); r.adopt(p); r.tick(); }).get();   // 구독+큐+전송 요청
        for (int i = 0; i < 5; i++) { sched.region(R00).execute(() -> { }).get(); Thread.sleep(60); }    // 스냅샷까지 진행
        sched.region(R00).execute(() -> w.regionAt(R00).breakBlock(0, -61, 0)).get();                     // 스냅샷 이후 편집
        gate.countDown();
        pool.submit(() -> null).get();
        Thread.sleep(300);
        sched.region(R00).execute(() -> { }).get();
        Thread.sleep(200);

        List<Integer> seq = ids(ch);
        int update = seq.indexOf(BlockUpdate.ID);
        int firstChunk = seq.indexOf(LevelChunkWithLight.ID);
        assertTrue(update >= 0, "BlockUpdate가 결국 도착해야 한다: " + seq);
        assertTrue(firstChunk >= 0, "청크가 도착해야 한다: " + seq);
        assertTrue(update > firstChunk, "BlockUpdate가 청크보다 먼저 도착하면 클라가 버려 변경을 영구히 놓친다: " + seq);
        sched.close();
    }

    /** 전달 완료(ready) 뒤의 변경은 보류 없이 즉시 전송된다. */
    @Test
    void editAfterDelivery_isSentImmediately() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = world(sched, new ChunkWorkerPool(1), reg);
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "P", ch);

        World.Subscription sub = w.subscribeChunk(C00, p);
        sched.region(R00).execute(() -> w.regionAt(R00).breakBlock(0, -61, 0)).get();   // 전달 전 — 보류
        assertEquals(List.of(), ids(ch), "ready 전의 변경은 보류돼야 한다");
        w.chunkDelivered(C00, p, sub);
        sched.region(R00).execute(() -> { }).get();
        assertEquals(List.of(BlockUpdate.ID), ids(ch), "전달 완료와 함께 보류분이 흘러가야 한다");
        sched.region(R00).execute(() -> w.regionAt(R00).breakBlock(1, -61, 0)).get();   // 전달 후 — 즉시
        assertEquals(List.of(BlockUpdate.ID), ids(ch));
        sched.close();
    }

    /** 전달 전에 언로드/재구독된 구독의 전달 완료는 무시되고, 보류분은 버려진다. */
    @Test
    void unsubscribedBeforeDelivery_dropsPending() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = world(sched, new ChunkWorkerPool(1), reg);
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "P", ch);

        World.Subscription stale = w.subscribeChunk(C00, p);
        sched.region(R00).execute(() -> w.regionAt(R00).breakBlock(0, -61, 0)).get();   // 보류됨
        w.unsubscribeChunk(C00, p);                                                      // 전달 전 언로드
        w.chunkDelivered(C00, p, stale);
        sched.region(R00).execute(() -> { }).get();
        assertEquals(List.of(), ids(ch), "언로드된 구독의 보류분은 흘러가면 안 된다");

        World.Subscription fresh = w.subscribeChunk(C00, p);                             // 재구독
        w.chunkDelivered(C00, p, stale);                                                 // 옛 구독의 지연된 완료 — 무시돼야 한다
        sched.region(R00).execute(() -> w.regionAt(R00).breakBlock(2, -61, 0)).get();
        assertEquals(List.of(), ids(ch), "재구독된 새 구독은 아직 ready가 아니다");
        assertFalse(w.isSubscribed(C00, p, stale));
        assertTrue(w.isSubscribed(C00, p, fresh));
        sched.close();
    }
}
