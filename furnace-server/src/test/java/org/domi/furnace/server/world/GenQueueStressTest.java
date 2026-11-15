// T-170 (PR #371 검토에서 옮김): 생성 대기열 스트레스(교착·누수) + 빠진 시험 3개(차례 한 바퀴, 시간 티켓 keepAlive, 오래 기다린 고정 요청).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GenQueueStressTest {

    private FurnaceSchedulerImpl sched;
    private ExecutorService io;
    private GenQueue q;
    private World w;

    @AfterEach
    void close() throws Exception {
        if (q != null) { q.shutdown(); q.awaitTermination(10, TimeUnit.SECONDS); }
        if (io != null) io.shutdown();
        if (sched != null) sched.close();
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object o, String name) throws Exception {
        var f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(o);
    }

    private static long traceCount(String stage) throws Exception {
        if (!ChunkTrace.ON) return -1;
        Map<String, ?> stages = fieldStatic(ChunkTrace.class, "STAGES");
        Object st = stages.get(stage);
        if (st == null) return 0;
        var nf = st.getClass().getDeclaredField("n");
        nf.setAccessible(true);
        return ((java.util.concurrent.atomic.LongAdder) nf.get(st)).sum();
    }

    @SuppressWarnings("unchecked")
    private static <T> T fieldStatic(Class<?> c, String name) throws Exception {
        var f = c.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(null);
    }

    private static int genWaitThreadsAlive() {
        int n = 0;
        for (Thread t : Thread.getAllStackTraces().keySet()) if (t.getName().equals("furnace-gen-wait") && t.isAlive()) n++;
        return n;
    }

    /**
     * 여러 region(구역 shift 2, region 스레드 3)에서 플레이어 6명이 걷고 순간 이동하고 접속을 끊는 동안 고정 로드·시간 티켓을 섞는다.
     * 끝나면: 고정 요청은 모두 청크를 받고, 생성 대기열·진행 중 표·생성 요청 표·플레이어 칸이 비고, 일하는 스레드가 살아 있다.
     */
    @Test
    void stress_movesTeleportsPinsTicketsDisconnects_noLeakNoDeadlock() throws Exception {
        long seed = Long.getLong("furnace.test.genq.seed", 371L);
        int rounds = Integer.getInteger("furnace.test.genq.rounds", 400);
        Random rnd = new Random(seed);
        int threadsBefore = genWaitThreadsAlive();
        if (ChunkTrace.ON) for (Thread t : Thread.getAllStackTraces().keySet()) if (t.getName().equals("furnace-chunktrace")) t.interrupt();   // 수를 지우지 않게
        sched = new FurnaceSchedulerImpl(10_000_000L, 3);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        io = Executors.newFixedThreadPool(2);
        // 차례 2명 × 칸 4, 고정 요청 0.2초 뒤 먼저, 차례 최대 0.3초 — 짧게 잡아 회전·취소를 자주 일으킨다
        q = new GenQueue(3, new GenQueue.Settings(true, 8, 0, 16, 0, 0, TimeUnit.MILLISECONDS.toNanos(200), false, 2, 4,
                TimeUnit.MILLISECONDS.toNanos(300)));
        WorldSource flat = new SuperflatWorldSource(reg, 0);
        AtomicInteger generated = new AtomicInteger();
        WorldSource slow = (cx, cz) -> {   // 생성이 수요를 못 따라가게(대기열이 쌓이게)
            generated.incrementAndGet();
            try { Thread.sleep(Integer.getInteger("furnace.test.genq.genMs", 4)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return flat.generate(cx, cz);
        };
        int vd = 4;
        w = new World(slow, new ChunkWorkerPool(2), sched, 3, 2, 1, vd, vd, reg.directBits(), 7, io, q);
        w.configureStreaming(0, org.domi.furnace.server.config.Config.CHUNKS_PER_TICK_AUTO);   // 서버 기본(auto) — 미리 불러오기 경로
        List<Player> ps = new ArrayList<>();
        List<ConcurrentEmbeddedChannel> chs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ConcurrentEmbeddedChannel ch = new ConcurrentEmbeddedChannel();
            Player p = new Player(100 + i, UUID.randomUUID(), "S" + i, ch);
            p.viewDistance(vd);
            double x = rnd.nextInt(-40, 40) * 16 + 8, z = rnd.nextInt(-40, 40) * 16 + 8;
            p.publishPosition(x, -60, z, 0, 0, true);
            p.drainPending();
            w.playerOnline(p);
            ChunkPos cp = p.chunkPos();
            w.submitAtChunk(cp, r -> { r.adopt(p); return null; }).get(10, TimeUnit.SECONDS);
            ps.add(p);
            chs.add(ch);
        }
        w.startTicking();
        List<CompletableFuture<ChunkColumn>> pinned = Collections.synchronizedList(new ArrayList<>());
        List<ChunkPos> pinnedAt = Collections.synchronizedList(new ArrayList<>());
        double[][] pos = new double[ps.size()][2];
        for (int i = 0; i < ps.size(); i++) { pos[i][0] = ps.get(i).x(); pos[i][1] = ps.get(i).z(); }
        for (int step = 0; step < rounds; step++) {
            for (int i = 0; i < ps.size(); i++) {
                Player p = ps.get(i);
                if (p.isDisconnected()) continue;
                if (rnd.nextInt(10) < 3) {   // 순간 이동(이전 미리 불러오기는 시야 밖이 된다)
                    pos[i][0] = rnd.nextInt(-60, 60) * 16 + 8;
                    pos[i][1] = rnd.nextInt(-60, 60) * 16 + 8;
                } else {   // 걷기(한 청크)
                    pos[i][0] += (rnd.nextInt(3) - 1) * 16;
                    pos[i][1] += (rnd.nextInt(3) - 1) * 16;
                }
                p.publishPosition(pos[i][0], -60, pos[i][1], 0, 0, true);
            }
            // 고정 요청: 아무 플레이어 시야 가장자리 청크 — 그 플레이어의 미리 불러오기와 겹치게(곧 순간 이동하면 취소와 겨룬다)
            if (step % 2 == 0) {
                Player p = ps.get(rnd.nextInt(ps.size()));
                ChunkPos c = p.chunkPos();
                ChunkPos cp = new ChunkPos(c.x() + rnd.nextInt(-vd, vd + 1), c.z() + rnd.nextInt(-vd, vd + 1));
                pinnedAt.add(cp);
                pinned.add(w.submitAtChunk(cp, r -> r.loadChunk(cp)).thenCompose(f -> f));
            }
            if (step % 7 == 0) {   // 시간 티켓(5틱, 순간 이동 뒤와 같은 꼴)
                Player p = ps.get(rnd.nextInt(ps.size()));
                w.addTicket(p.chunkPos().x() + rnd.nextInt(-8, 9), p.chunkPos().z() + rnd.nextInt(-8, 9), 1, 5);
            }
            if (step == rounds / 2) {   // 두 명 접속 끊기(그 사람 미리 불러오기는 아무 시야에도 없게 된다)
                ps.get(0).markDisconnected();
                ps.get(1).markDisconnected();
            }
            for (ConcurrentEmbeddedChannel ch : chs) {
                ch.runPendingTasks();
                Object o;
                while ((o = ch.readOutbound()) != null) ReferenceCountUtil.release(o);
            }
            Thread.sleep(5);
        }
        // 고정 요청은 모두 청크를 받는다(취소가 새지 않는다, 교착 없음)
        int failed = 0;
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < pinned.size(); i++) {
            try {
                assertNotNull(pinned.get(i).get(60, TimeUnit.SECONDS));
            } catch (Exception e) {
                failed++;
                if (failures.size() < 5) failures.add(pinnedAt.get(i) + " " + e);
            }
        }
        assertEquals(0, failed, "고정 요청 실패/시간 초과 " + failed + "/" + pinned.size() + " " + failures);
        // 남은 플레이어도 끊고 잦아들기를 기다린다 — 끊긴 사람 요청은 취소되고 진행 중인 것은 끝난다
        for (Player p : ps) p.markDisconnected();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        Map<Long, ?> loading = field(w, "loading");
        Map<Long, ?> genTickets = field(w, "genTickets");
        while (System.nanoTime() < deadline) {
            boolean quiet = q.queuedGen() == 0 && loading.isEmpty() && genTickets.isEmpty();
            for (Player p : ps) quiet &= p.chunkPrefetches().isEmpty() && p.genPrefetches().isEmpty();
            if (quiet) break;
            for (ConcurrentEmbeddedChannel ch : chs) ch.runPendingTasks();
            Thread.sleep(20);
        }
        StringBuilder left = new StringBuilder();
        for (Player p : ps) left.append(p.entityId()).append(':').append(p.chunkPrefetches().size()).append('/').append(p.genPrefetches().size()).append(' ');
        assertEquals(0, q.queuedGen(), "대기열에 남은 생성 요청");
        assertEquals(0, loading.size(), "진행 중 로드 표에 남은 것");
        assertEquals(0, genTickets.size(), "생성 요청 표(genTickets)에 남은 것 — 누수");
        for (Player p : ps) {
            assertEquals(0, p.chunkPrefetches().size(), "chunkPrefetches 누수 " + left);
            assertEquals(0, p.genPrefetches().size(), "genPrefetches 누수 " + left);
        }
        assertEquals(threadsBefore + 3, genWaitThreadsAlive(), "생성 대기열 일하는 스레드가 죽지 않았다");
        System.out.println("[REVIEW371] seed=" + seed + " rounds=" + rounds + " generated=" + generated.get() + " pinned=" + pinned.size()
                + " cancel=" + traceCount("gen.cancel") + " run=" + traceCount("gen.run") + " queue=" + traceCount("gen.queue"));
    }

    // ------------------------------------------------------------------ 빠진 시험(검토 권고)

    private static final int VD = 4;
    private final RegionPos rp = new RegionPos(0, 0);

    private World single(GenQueue.Settings s) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        io = Executors.newSingleThreadExecutor();
        q = new GenQueue(1, s);
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, VD, VD,
                reg.directBits(), 7, io, q);
        return w;
    }

    private Player join(int id, double x, double z) throws Exception {
        Player p = new Player(id, UUID.randomUUID(), "P" + id, new ConcurrentEmbeddedChannel());
        p.viewDistance(VD);
        p.applyMove(x, -60, z, 0, 0, true);
        sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
        return p;
    }

    private CountDownLatch block() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1), gate = new CountDownLatch(1);
        q.execute(() -> {
            started.countDown();
            try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        return gate;
    }

    private static String gated(GenQueue.Ticket t, String v) {
        if (!t.tryStart()) throw new java.util.concurrent.CancellationException();
        return v;
    }

    /** 차례가 한 바퀴 돌면 처음 사람에게 돌아온다(줄 뒤로 다시 서는지). */
    @Test
    void turns_roundRobin_returnsToFirstOwner() throws Exception {
        single(new GenQueue.Settings(true, 16, 0, 16, 0, 0, TimeUnit.SECONDS.toNanos(3), false, 1, 2, TimeUnit.SECONDS.toNanos(60)));
        Player[] pl = {join(1, 8, 8), join(2, 8, 8), join(3, 8, 8)};
        for (Player p : pl) p.genWantNanos(System.nanoTime());
        assertEquals(2, q.capFor(1, true));
        assertEquals(1, q.capFor(2, true));
        assertEquals(1, q.capFor(3, true));
        int[] owners = {1, 2, 3, 1};
        for (int k = 0; k < owners.length; k++) {
            int o = owners[k];
            for (Player p : pl) p.genWantNanos(System.nanoTime());
            assertEquals(2, q.capFor(o, false), "차례 " + k + " 는 " + o);
            CountDownLatch two = new CountDownLatch(2);
            for (int i = 0; i < 2; i++) {
                GenQueue.Ticket tk = new GenQueue.Ticket(true, null, o);
                q.tagged(w, i, 0, tk).execute(() -> { tk.tryStart(); two.countDown(); });
            }
            assertTrue(two.await(5, TimeUnit.SECONDS));
            Thread.sleep(20);
        }
    }

    /** 시간 티켓 로드: 티켓이 살아 있는 동안은 시야 밖이어도 남기고, 끝나면 취소한다. */
    @Test
    void timedTicketLoad_keptWhileAlive_cancelledAfter() throws Exception {
        single(new GenQueue.Settings(true, 16, 0, 16, 0, 0, TimeUnit.SECONDS.toNanos(3), false));
        join(1, 8, 8);
        CountDownLatch gate = block();
        AtomicBoolean alive = new AtomicBoolean(true);
        GenQueue.Ticket t = new GenQueue.Ticket(true, alive::get, -1);
        CompletableFuture<String> f = CompletableFuture.supplyAsync(() -> gated(t, "x"), q.tagged(w, 40, 0, t));
        q.reprioNow();
        assertFalse(t.cancelled(), "티켓이 살아 있으면 시야 밖이어도 남긴다");
        alive.set(false);
        q.reprioNow();
        assertTrue(t.cancelled(), "티켓이 끝나면 시야 밖 요청은 취소");
        gate.countDown();
        assertThrows(Exception.class, () -> f.get(5, TimeUnit.SECONDS));
    }

    /** 오래 기다린 고정 요청은 거리와 상관없이 먼저. */
    @Test
    void pinnedRequest_afterMaxWait_jumpsAhead() throws Exception {
        single(new GenQueue.Settings(true, 16, 0, 16, 0, 0, TimeUnit.MILLISECONDS.toNanos(100), false));
        join(1, 8, 8);
        CountDownLatch gate = block();
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch all = new CountDownLatch(2);
        q.tagged(w, 3, 0, null).execute(() -> { order.add("far-pinned"); all.countDown(); });   // ticket null = 고정
        Thread.sleep(150);
        GenQueue.Ticket near = new GenQueue.Ticket(true);
        q.tagged(w, 1, 0, near).execute(() -> { order.add("near"); all.countDown(); });
        gate.countDown();
        assertTrue(all.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("far-pinned", "near"), order);
    }
}
