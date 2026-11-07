// M10 차원 이동 도착 편입 넘기기: 연결 스레드가 리스폰 패킷을 다 보내는 동안 목적 region 이 잠깐 기다렸다가 그 자리에서(틱 사이) 편입한다 —
// 바닐라처럼 넘어간 그 틱에 새 차원에 있다. 기다림을 넘기면 연결이 편입 작업을 보낸다. 어느 쪽이든 편입은 정확히 한 번.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArrivalHandoffTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private World w;

    @AfterEach
    void close() { if (sched != null) sched.close(); }

    private World world() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7, TestServers.ioPool());
        w.worldDir(tmp);
        w.startTicking();
        ChunkPos c = new ChunkPos(0, 0);
        w.submitAtChunk(c, r -> r.chunkAtBlocking(c)).get(30, TimeUnit.SECONDS);
        return w;
    }

    @Test
    void sentWhileWaiting_adoptsInlineOnTheOwnerRegionThread() throws Exception {
        World w = world();
        AtomicReference<Thread> adoptedOn = new AtomicReference<>();
        AtomicInteger adopts = new AtomicInteger();
        CompletableFuture<Boolean> beforeReturn = w.submitAtChunk(new ChunkPos(0, 0), r -> {
            assertTrue(r.betweenTicks(), "작업 큐를 도는 중은 틱 사이");
            Player.ArrivalHandoff h = new Player.ArrivalHandoff();
            Thread conn = new Thread(() -> h.sent(w, 8.5, 8.5, region -> { adopts.incrementAndGet(); adoptedOn.set(Thread.currentThread()); }));
            conn.start();
            h.awaitInline(TimeUnit.SECONDS.toNanos(5));
            return adoptedOn.get() == Thread.currentThread();
        });
        assertTrue(beforeReturn.get(10, TimeUnit.SECONDS), "기다리던 region 스레드가 그 자리에서 편입한다");
        Thread.sleep(150);
        assertEquals(1, adopts.get());
    }

    @Test
    void sentAfterGivingUp_connectionSchedulesAdoptOnce() throws Exception {
        World w = world();
        AtomicInteger adopts = new AtomicInteger();
        CompletableFuture<Thread> regionThread = new CompletableFuture<>();
        Player.ArrivalHandoff h = new Player.ArrivalHandoff();
        w.submitAtChunk(new ChunkPos(0, 0), r -> {
            h.awaitInline(1_000_000L);   // 1ms — 연결이 아직 안 보냈다
            return null;
        }).get(10, TimeUnit.SECONDS);
        assertEquals(0, adopts.get());
        h.sent(w, 8.5, 8.5, region -> { adopts.incrementAndGet(); regionThread.complete(Thread.currentThread()); });
        assertNotEquals(Thread.currentThread(), regionThread.get(10, TimeUnit.SECONDS), "편입은 그 칸 주인 region 에서");
        Thread.sleep(150);
        assertEquals(1, adopts.get());
    }

    @Test
    void detachedHandoff_neverWaits() throws Exception {
        World w = world();
        AtomicInteger adopts = new AtomicInteger();
        Player.ArrivalHandoff h = new Player.ArrivalHandoff();
        h.detach();
        long t0 = System.nanoTime();
        h.awaitInline(TimeUnit.SECONDS.toNanos(5));
        assertTrue(System.nanoTime() - t0 < TimeUnit.SECONDS.toNanos(1), "기다리는 쪽이 없다고 했으면 바로 돌아온다");
        CompletableFuture<Void> done = new CompletableFuture<>();
        h.sent(w, 0, 0, region -> { adopts.incrementAndGet(); done.complete(null); });
        done.get(10, TimeUnit.SECONDS);
        assertEquals(1, adopts.get());
    }
}
