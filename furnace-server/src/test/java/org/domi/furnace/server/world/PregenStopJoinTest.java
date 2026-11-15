// T-175 검토(PR #380) 시험: 멈춘 미리 생성의 취소된 요청에 붙은 요청자가 대기열 맨 뒤까지 기다리지 않는다(R1), 플레이어 미리 불러오기도 promote, 멈추고 바로 이어 하기(R2).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PregenStopJoinTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final List<String> BIOMES = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");

    private static final class OrderGen implements WorldSource {
        final List<Long> order = Collections.synchronizedList(new ArrayList<>());
        final SuperflatWorldSource flat = new SuperflatWorldSource(REG, 0);

        @Override
        public ChunkColumn generate(int cx, int cz) {
            order.add(Regionizer.chunkKey(cx, cz));
            try { Thread.sleep(40); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }   // 생성이 region 틱(50ms)보다 오래 걸리게
            return flat.generate(cx, cz);
        }
    }

    private FurnaceSchedulerImpl sched;
    private ExecutorService io;
    private GenQueue q;
    private Pregen pregen;

    @AfterEach
    void close() throws Exception {
        if (pregen != null) pregen.shutdown();
        if (q != null) { q.shutdown(); q.awaitTermination(5, TimeUnit.SECONDS); }
        if (io != null) { io.shutdown(); io.awaitTermination(10, TimeUnit.SECONDS); }
        if (sched != null) sched.close();
    }

    private World world(Path dir, WorldSource base) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        io = Executors.newFixedThreadPool(2);
        q = new GenQueue(1, new GenQueue.Settings(true, 4, 0, 16, 0, 0, TimeUnit.SECONDS.toNanos(3), false));
        AnvilWorldSource src = new AnvilWorldSource(dir, new RegionFileStore(dir),
                new AnvilChunkCodec(REG, ItemRegistry.load("items/1.21.1.txt"), BIOMES, 0, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT), base);
        src.entityStore(new RegionFileStore(dir, "entities"));
        World w = new World(src, new ChunkWorkerPool(1), sched, 1, 16, 2, 2, REG.directBits(), 7, io, q);
        w.setRegistries(REG, ItemRegistry.load("items/1.21.1.txt"));
        w.configurePersistence(1_000_000, 1_000_000);
        w.worldDir(dir);
        pregen = new Pregen();
        return w;
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

    private static void awaitTicket(World w, ChunkPos cp) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (w.genTicket(Regionizer.chunkKey(cp.x(), cp.z())) == null) {
            assertTrue(System.nanoTime() < deadline, "생성 요청이 대기열에 안 섰다 " + cp);
            Thread.sleep(2);
        }
    }

    /** 일반 요청자(플러그인 getChunkAt 등, 고정)가 멈춘 미리 생성 청크에 붙는다 → 뒤에 들어온 요청 모두가 먼저 만들어진다. */
    @Test
    void stoppedPregen_pinningJoiner_waitsBehindLaterRequests(@TempDir Path dir) throws Exception {
        OrderGen gen = new OrderGen();
        World w = world(dir, gen);
        CountDownLatch gate = block();
        pregen.start(w, new Pregen.Area(0, 0, -1, -1, 1, 1, "t"), null);
        ChunkPos c0 = new ChunkPos(0, 0);
        awaitTicket(w, c0);
        awaitTicket(w, new ChunkPos(-1, -1));
        assertTrue(pregen.stop(w));
        assertTrue(w.genTicket(Regionizer.chunkKey(0, 0)).cancelled());

        // 멈춘 뒤 (0,0) 을 원하는 요청이 먼저, 그다음 다른 청크 요청 5개가 들어온다
        int[] genAtOldDone = {-1};
        w.loadingChunk(Regionizer.chunkKey(0, 0)).whenComplete((c, e) -> genAtOldDone[0] = gen.order.size());
        CompletableFuture<ChunkColumn> joined = w.submitAtChunk(c0, r -> r.loadChunk(c0)).thenCompose(x -> x);
        List<CompletableFuture<ChunkColumn>> later = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ChunkPos o = new ChunkPos(20 + i, 20);
            later.add(w.submitAtChunk(o, r -> r.loadChunk(o)).thenCompose(x -> x));
            awaitTicket(w, o);
        }
        gate.countDown();
        joined.get(30, TimeUnit.SECONDS);
        for (var f : later) f.get(30, TimeUnit.SECONDS);
        int at = gen.order.indexOf(Regionizer.chunkKey(0, 0));
        assertTrue(genAtOldDone[0] < 5, "취소된 미리 생성 요청이 뒤 요청 5개가 다 만들어진 뒤에야 빠졌다 → 붙은 요청자가 그동안 기다렸다");
    }

    /** 플레이어 미리 불러오기(고정 안 함)가 멈춘 미리 생성 청크에 붙는다 → 뒤에 들어온 요청이 다 끝난 뒤에야 취소 실패를 받는다. */
    @Test
    void stoppedPregen_playerPrefetchJoiner_failsOnlyAfterQueueDrains(@TempDir Path dir) throws Exception {
        OrderGen gen = new OrderGen();
        World w = world(dir, gen);
        CountDownLatch gate = block();
        pregen.start(w, new Pregen.Area(0, 0, -1, -1, 1, 1, "t"), null);
        ChunkPos c0 = new ChunkPos(0, 0);
        awaitTicket(w, c0);
        long dl = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (q.queuedGen() < 2) { assertTrue(System.nanoTime() < dl); Thread.sleep(2); }   // 생성 대기열에 선 뒤 멈춘다(실서버에서 흔한 상태)
        assertTrue(pregen.stop(w));

        CompletableFuture<ChunkColumn> prefetch = w.submitAtChunk(c0, r -> r.loadChunkForPlayer(c0, 7)).thenCompose(x -> x);
        List<CompletableFuture<ChunkColumn>> later = new ArrayList<>();
        List<Long> laterDone = Collections.synchronizedList(new ArrayList<>());
        long[] prefetchDoneAt = new long[1];
        for (int i = 0; i < 5; i++) {
            ChunkPos o = new ChunkPos(20 + i, 20);
            later.add(w.submitAtChunk(o, r -> r.loadChunk(o)).thenCompose(x -> x).whenComplete((c, e) -> laterDone.add(System.nanoTime())));
            awaitTicket(w, o);
        }
        prefetch.whenComplete((c, e) -> prefetchDoneAt[0] = gen.order.size());   // 끝날 때 이미 만들어진 청크 수
        gate.countDown();
        for (var f : later) f.get(30, TimeUnit.SECONDS);
        Throwable err = null;
        try { prefetch.get(30, TimeUnit.SECONDS); } catch (java.util.concurrent.ExecutionException e) { err = e.getCause(); }
        assertTrue(prefetchDoneAt[0] < 5,
                "플레이어 미리 불러오기가 뒤에 들어온 요청 5개가 모두 만들어진 뒤에야 끝났다(결과 " + err + ")");
    }

    /** 플레이어 미리 불러오기(고정 안 하는 요청자)도 미리 생성 요청을 맨 뒤에서 꺼낸다 — PregenTest 는 loadChunk(고정)만 본다. */
    @Test
    void playerPrefetchJoiner_promotesBackground(@TempDir Path dir) throws Exception {
        OrderGen gen = new OrderGen();
        World w = world(dir, gen);
        CountDownLatch gate = block();
        var j = pregen.start(w, new Pregen.Area(0, 0, -1, -1, 1, 1, "t"), null).job();
        ChunkPos second = new ChunkPos(-1, -1);
        awaitTicket(w, new ChunkPos(0, 0));
        awaitTicket(w, second);
        ChunkPos other = new ChunkPos(10, 10);
        var otherF = w.submitAtChunk(other, r -> r.loadChunk(other)).thenCompose(x -> x);
        awaitTicket(w, other);
        var joined = w.submitAtChunk(second, r -> r.loadChunkForPlayer(second, 7)).thenCompose(x -> x);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (w.genTicket(Regionizer.chunkKey(-1, -1)).isBackground()) {
            assertTrue(System.nanoTime() < deadline, "플레이어 미리 불러오기가 붙어도 맨 뒤에서 안 내려온다");
            Thread.sleep(2);
        }
        gate.countDown();
        otherF.get(30, TimeUnit.SECONDS);
        joined.get(30, TimeUnit.SECONDS);
        long dl = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (j.state() == Pregen.State.RUNNING) { assertTrue(System.nanoTime() < dl); Thread.sleep(10); }
        assertTrue(gen.order.indexOf(Regionizer.chunkKey(-1, -1)) < gen.order.indexOf(Regionizer.chunkKey(0, 0)), "붙은 요청이 남은 미리 생성보다 먼저");
    }

    /** 멈추고 곧바로 이어 하면 취소된 옛 요청에 붙는다 — 다시 걸어 끝까지 가야 한다(노트의 함정, MAX_RETRY 변이를 잡는다). */
    @Test
    void stopThenImmediateResume_finishes(@TempDir Path dir) throws Exception {
        OrderGen gen = new OrderGen();
        World w = world(dir, gen);
        CountDownLatch gate = block();
        pregen.start(w, new Pregen.Area(0, 0, -1, -1, 1, 1, "t"), null);
        awaitTicket(w, new ChunkPos(0, 0));
        awaitTicket(w, new ChunkPos(-1, -1));
        assertTrue(pregen.stop(w));
        var r = pregen.resume(w, null);
        assertNotNull(r.job(), r.refused());
        Thread.sleep(200);   // 새 작업이 취소된 옛 로드에 붙을 틈
        gate.countDown();
        long dl = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (r.job().state() == Pregen.State.RUNNING) { assertTrue(System.nanoTime() < dl); Thread.sleep(10); }
        assertEquals(Pregen.State.DONE, r.job().state(), r.job().progress().line());
        assertEquals(9, new java.util.HashSet<>(gen.order).size());
        var p = r.job().progress();
        assertEquals(9, p.generated() + p.skipped() + p.failed(), "이어 할 곳 뒤에서 먼저 끝난 청크를 두 번 세지 않는다(검토 R4): " + p.line());
    }
}
