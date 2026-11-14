// T-170 생성 대기열: 거리 순, 시야 밖 미리 불러오기 취소(고정 요청은 안 함), 플레이어 생성 칸, 취소가 다른 요청자에게 새지 않음.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GenQueueTest {

    private static final int VD = 4;

    private FurnaceSchedulerImpl sched;
    private ExecutorService io;
    private GenQueue q;
    private World w;
    private final RegionPos rp = new RegionPos(0, 0);

    private static GenQueue.Settings settings(boolean distance, int budget, int fixedCap, long delayMs) {
        return new GenQueue.Settings(distance, budget, fixedCap, 16, TimeUnit.MILLISECONDS.toNanos(delayMs), 0,
                TimeUnit.SECONDS.toNanos(3), false);
    }

    private World world(GenQueue.Settings s, int threads) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        io = Executors.newSingleThreadExecutor();
        q = new GenQueue(threads, s);
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, VD, VD,
                reg.directBits(), 7, io, q);
        w.configureStreaming(0, Config.CHUNKS_PER_TICK_AUTO);
        return w;
    }

    @AfterEach
    void close() throws Exception {
        if (q != null) { q.shutdown(); q.awaitTermination(5, TimeUnit.SECONDS); }
        if (io != null) io.shutdown();
        if (sched != null) sched.close();
    }

    private Player join(int id, double x, double z) throws Exception {
        Player p = new Player(id, UUID.randomUUID(), "P" + id, new ConcurrentEmbeddedChannel());
        p.viewDistance(VD);
        p.applyMove(x, -60, z, 0, 0, true);
        sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
        return p;
    }

    private void tick() throws Exception {
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
    }

    /** 일하는 스레드 하나를 막아 둔다(위치 없는 작업이 먼저 뽑힌다). */
    private CountDownLatch block() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1), gate = new CountDownLatch(1);
        q.execute(() -> {
            started.countDown();
            try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        return gate;
    }

    @Test
    void generationRequests_runNearestPlayerFirst_tiesInSubmitOrder() throws Exception {
        world(settings(true, 16, 0, 0), 1);
        join(1, 8, 8);   // 청크 (0,0)
        CountDownLatch gate = block();
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        int[][] at = {{3, 0}, {1, 1}, {0, 2}, {-1, 0}, {2, -2}};   // 거리 3, 1, 2, 1, 2
        CountDownLatch all = new CountDownLatch(at.length);
        for (int[] c : at) {
            q.tagged(w, c[0], c[1], null).execute(() -> { order.add(c[0] + "," + c[1]); all.countDown(); });
        }
        gate.countDown();
        assertTrue(all.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("1,1", "-1,0", "0,2", "2,-2", "3,0"), order);
    }

    @Test
    void worldWithoutPlayers_isSubmitOrder() throws Exception {
        world(settings(true, 16, 0, 0), 1);
        CountDownLatch gate = block();
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch all = new CountDownLatch(3);
        int[] xs = {9, 0, 4};
        for (int x : xs) q.tagged(w, x, 0, null).execute(() -> { order.add(x); all.countDown(); });
        gate.countDown();
        assertTrue(all.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(9, 0, 4), order, "플레이어 없는 차원은 넣은 순");
    }

    @Test
    void fifoMode_ignoresDistance_andHasNoGenCap() throws Exception {
        world(settings(false, 16, 0, 0), 1);
        join(1, 8, 8);
        CountDownLatch gate = block();
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch all = new CountDownLatch(3);
        int[] xs = {9, 0, 4};
        for (int x : xs) q.tagged(w, x, 0, null).execute(() -> { order.add(x); all.countDown(); });
        gate.countDown();
        assertTrue(all.await(5, TimeUnit.SECONDS));
        assertEquals(List.of(9, 0, 4), order, "fifo 모드는 넣은 순");
        assertEquals(Integer.MAX_VALUE, q.playerCap(), "fifo 모드는 생성 칸을 따로 묶지 않는다(예전 동작)");
    }

    @Test
    void outOfViewPrefetch_isCancelledBeforeStart_pinnedIsNot() throws Exception {
        world(settings(true, 16, 0, 0), 1);
        join(1, 8, 8);
        CountDownLatch gate = block();
        GenQueue.Ticket far = new GenQueue.Ticket(true), pinned = new GenQueue.Ticket(false), near = new GenQueue.Ticket(true);
        CompletableFuture<String> fFar = CompletableFuture.supplyAsync(() -> gated(far, "far"), q.tagged(w, 40, 0, far));
        CompletableFuture<String> fPinned = CompletableFuture.supplyAsync(() -> gated(pinned, "pinned"), q.tagged(w, 40, 1, pinned));
        CompletableFuture<String> fNear = CompletableFuture.supplyAsync(() -> gated(near, "near"), q.tagged(w, VD + 1, 0, near));   // 바깥 한 겹 안
        q.reprioNow();
        ExecutionException ex = assertThrows(ExecutionException.class, () -> fFar.get(5, TimeUnit.SECONDS));
        assertInstanceOf(java.util.concurrent.CancellationException.class, ex.getCause());
        assertTrue(far.cancelled());
        assertFalse(far.pin(), "취소된 뒤에는 고정할 수 없다(다시 걸어야 한다)");
        assertFalse(fPinned.isDone());
        assertFalse(fNear.isDone(), "추적 범위(+바깥 한 겹) 안이면 남긴다");
        gate.countDown();
        assertEquals("pinned", fPinned.get(5, TimeUnit.SECONDS));
        assertEquals("near", fNear.get(5, TimeUnit.SECONDS));
    }

    @Test
    void cancelDelay_keepsRequestUntilStaleLongEnough() throws Exception {
        world(settings(true, 16, 0, 60_000), 1);
        join(1, 8, 8);
        CountDownLatch gate = block();
        GenQueue.Ticket far = new GenQueue.Ticket(true);
        CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> gated(far, "far"), q.tagged(w, 40, 0, far));
        q.reprioNow();
        assertFalse(far.cancelled(), "반납 지연 안에서는 취소하지 않는다");
        gate.countDown();
        assertEquals("far", f.get(5, TimeUnit.SECONDS));
    }

    private static String gated(GenQueue.Ticket t, String v) {
        if (!t.tryStart()) throw new java.util.concurrent.CancellationException();
        return v;
    }

    @Test
    void ticket_pinAndCancelRace_onlyOneWins() throws Exception {
        for (int i = 0; i < 2000; i++) {
            GenQueue.Ticket t = new GenQueue.Ticket(true);
            boolean[] r = new boolean[2];
            Thread a = new Thread(() -> r[0] = t.pin());
            Thread b = new Thread(() -> r[1] = t.tryCancel());
            a.start(); b.start(); a.join(); b.join();
            assertTrue(r[0] ^ r[1], "고정과 취소 중 정확히 하나만 이긴다");
            assertEquals(r[0], t.tryStart(), "고정됐으면 시작하고, 취소됐으면 시작하지 않는다");
        }
    }

    /** 취소된 미리 불러오기에 다른 요청자가 붙어도 그 요청자는 청크를 받는다(취소가 새지 않는다). */
    @Test
    void cancelledPrefetch_doesNotFailOtherCallers() throws Exception {
        world(settings(true, 16, 0, 0), 1);
        join(1, 8, 8);
        CountDownLatch gate = block();
        ChunkPos far = new ChunkPos(40, 0);
        @SuppressWarnings("unchecked") CompletableFuture<ChunkColumn>[] fs = new CompletableFuture[2];
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            fs[0] = r.loadChunk(far, true);
            q.reprioNow();                 // 시야 밖 — 취소(정리는 다음 틱에 이 region 이 한다)
            fs[1] = r.loadChunk(far);      // 고정하려다 이미 취소됨 → 실패가 끝난 뒤 새로 건다
        }).get();
        gate.countDown();
        for (int i = 0; i < 200 && !fs[1].isDone(); i++) { tick(); Thread.sleep(5); }
        assertTrue(fs[0].isCompletedExceptionally(), "미리 불러오기는 취소로 끝난다");
        assertNotNull(fs[1].get(5, TimeUnit.SECONDS), "고정 요청자는 청크를 받는다");
        assertTrue(w.isChunkLoaded(far.x(), far.z()));
    }

    /** 다른 요청자가 먼저 붙으면 시야 밖이어도 취소하지 않는다. */
    @Test
    void pinnedByOtherCaller_isNotCancelled() throws Exception {
        world(settings(true, 16, 0, 0), 1);
        join(1, 8, 8);
        CountDownLatch gate = block();
        ChunkPos far = new ChunkPos(40, 0);
        @SuppressWarnings("unchecked") CompletableFuture<ChunkColumn>[] fs = new CompletableFuture[2];
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            fs[0] = r.loadChunk(far, true);
            fs[1] = r.loadChunk(far);
        }).get();
        q.reprioNow();
        gate.countDown();
        for (int i = 0; i < 200 && !(fs[0].isDone() && fs[1].isDone()); i++) { tick(); Thread.sleep(5); }
        assertSame(fs[0].get(5, TimeUnit.SECONDS), fs[1].get(5, TimeUnit.SECONDS));
    }

    /** 생성 칸: 디스크에 없는 청크는 칸 수까지만 걸고, 그래도 시야 안 청크는 모두 온다(굶김 없음). */
    @Test
    void playerGenCap_limitsInFlightGeneration_andViewStillFills() throws Exception {
        world(settings(true, 16, 2, 0), 2);
        Player p = join(1, 8, 8);
        var want = ChunkTrackingViewTest.vanillaSet(0, 0, VD);
        int maxGen = 0;
        for (int i = 0; i < 600 && !want.stream().allMatch(c -> w.isChunkLoaded(c.x(), c.z())); i++) {
            tick();
            maxGen = Math.max(maxGen, p.genPrefetches().size());
            assertTrue(p.genPrefetches().size() <= 2, "생성 칸 2 를 넘으면 안 된다: " + p.genPrefetches().size());
            Thread.sleep(3);
        }
        assertTrue(maxGen > 0);
        for (ChunkPos c : want) assertTrue(w.isChunkLoaded(c.x(), c.z()), "안 온 청크 " + c);
    }

    @Test
    void playerCap_isBudgetSplitAcrossExploringPlayers() throws Exception {
        world(settings(true, 8, 0, 0), 1);
        Player a = join(1, 8, 8), b = join(2, 8, 8), c = join(3, 8, 8);
        CountDownLatch gate = block();
        q.reprioNow();
        assertEquals(8, q.playerCap(), "탐험 중인 사람이 없으면 예산 전부(혼자 탐험할 때 첫 시야가 느려지지 않게)");
        a.genPrefetches().add(new ChunkPos(1, 1));
        b.genPrefetches().add(new ChunkPos(2, 1));
        q.reprioNow();
        assertEquals(4, q.playerCap());
        c.genPrefetches().add(new ChunkPos(3, 1));
        q.reprioNow();
        assertEquals(2, q.playerCap());
        gate.countDown();
    }

    private static GenQueue.Settings turns(int owners, int quota) {
        return new GenQueue.Settings(true, 16, 0, 16, 0, 0, TimeUnit.SECONDS.toNanos(3), false, owners, quota, TimeUnit.SECONDS.toNanos(60));
    }

    /** 차례: 차례인 사람 요청이 더 가까운 남의 요청보다 먼저, turnQuota 개를 시작하면 다음 사람 차례. */
    @Test
    void turns_ownerOnTurnFirst_thenRotatesAfterQuota() throws Exception {
        world(turns(1, 2), 1);
        Player a = join(1, 8, 8), b = join(2, 8, 8);
        a.genWantNanos(System.nanoTime());
        b.genWantNanos(System.nanoTime());
        CountDownLatch gate = block();
        assertEquals(2, q.capFor(1, true), "첫 사람은 바로 차례 — 큰 칸");
        assertEquals(1, q.capFor(2, true), "줄 선 사람은 칸 하나");
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        String[][] at = {{"b", "0"}, {"b", "1"}, {"a", "2"}, {"a", "3"}, {"a", "4"}};
        CountDownLatch all = new CountDownLatch(at.length);
        for (String[] t : at) {
            int x = Integer.parseInt(t[1]);
            GenQueue.Ticket tk = new GenQueue.Ticket(true, null, t[0].equals("a") ? 1 : 2);
            q.tagged(w, x, 0, tk).execute(() -> { order.add(t[0] + x); all.countDown(); });
        }
        gate.countDown();
        assertTrue(all.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("a2", "a3", "b0", "b1", "a4"), order);
    }

    /** 차례인 사람이 더 걸 것이 없으면 잠시 뒤 다음 사람에게 넘긴다. */
    @Test
    void turns_idleOwnerPassesTurn() throws Exception {
        world(turns(1, 16), 1);
        Player a = join(1, 8, 8), b = join(2, 8, 8);
        a.genWantNanos(System.nanoTime());
        b.genWantNanos(System.nanoTime());
        assertEquals(16, q.capFor(1, true));
        assertEquals(1, q.capFor(2, true));
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(GenQueue.TURN_IDLE_NANOS) + 50);
        q.reprioNow();
        assertEquals(16, q.capFor(2, false), "요청 없는 차례는 넘어간다");
        assertEquals(1, q.capFor(1, false));
    }
}
