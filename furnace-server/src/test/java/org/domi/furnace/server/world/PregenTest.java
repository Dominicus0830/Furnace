// T-175 미리 생성: 고리 순서·영역, 생성 대기열 맨 뒤(다른 요청이 먼저), 붙은 요청은 맨 뒤에서 내려옴, 멈춤·이어 하기, 디스크 청크 건너뜀.
package org.domi.furnace.server.world;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.domi.furnace.server.i18n.Lang;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PregenTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");
    private static final List<String> BIOMES = RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt").entries("minecraft:worldgen/biome");

    // ------------------------------------------------------------------ 고리 순서·영역

    private static List<long[]> walk(Pregen.Area a) {
        List<long[]> out = new ArrayList<>();
        Pregen.Spiral s = new Pregen.Spiral(a);
        while (s.hasNext()) {
            out.add(new long[] {s.x(), s.z(), s.index()});
            s.next();
        }
        return out;
    }

    @Test
    void spiral_visitsEveryCellOnce_centerFirst_ringsOutward() {
        for (Pregen.Area a : List.of(new Pregen.Area(0, 0, -3, -3, 3, 3, "t"), new Pregen.Area(0, 0, -3, -3, 2, 2, "t"),
                new Pregen.Area(5, -2, 4, -6, 9, 0, "t"), new Pregen.Area(0, 0, 0, 0, 0, 0, "t"))) {
            List<long[]> cells = walk(a);
            assertEquals(a.total(), cells.size(), a.toString());
            Set<Long> seen = new HashSet<>();
            int last = -1;
            for (int i = 0; i < cells.size(); i++) {
                long[] c = cells.get(i);
                assertEquals(i, c[2], "번호는 영역 안 칸만 센다");
                assertTrue(a.contains((int) c[0], (int) c[1]));
                assertTrue(seen.add(Regionizer.chunkKey((int) c[0], (int) c[1])), "같은 칸 두 번");
                int ring = Math.max(Math.abs((int) c[0] - a.centerX()), Math.abs((int) c[1] - a.centerZ()));
                assertTrue(ring >= last, "고리는 안에서 밖으로");
                last = ring;
            }
            assertEquals(a.centerX(), cells.get(0)[0]);
            assertEquals(a.centerZ(), cells.get(0)[1]);
        }
    }

    @Test
    void spiral_consecutiveCellsAreNeighbours_withinRing() {
        List<long[]> cells = walk(new Pregen.Area(0, 0, -4, -4, 4, 4, "t"));
        for (int i = 2; i < cells.size(); i++) {
            long[] p = cells.get(i - 1), c = cells.get(i);
            int rp = (int) Math.max(Math.abs(p[0]), Math.abs(p[1])), rc = (int) Math.max(Math.abs(c[0]), Math.abs(c[1]));
            if (rp != rc) continue;
            assertEquals(1, Math.abs(p[0] - c[0]) + Math.abs(p[1] - c[1]), "고리 안에서는 이웃으로 이어 간다(이웃 원형을 함께 쓴다)");
        }
    }

    @Test
    void spiral_skipResumesAtSameCell() {
        Pregen.Area a = new Pregen.Area(1, 1, -5, -2, 6, 7, "t");
        List<long[]> cells = walk(a);
        for (int n : new int[] {0, 1, 7, 50, cells.size() - 1}) {
            Pregen.Spiral s = new Pregen.Spiral(a);
            s.skip(n);
            assertEquals(cells.get(n)[0], s.x());
            assertEquals(cells.get(n)[1], s.z());
            assertEquals(n, s.index());
        }
        Pregen.Spiral s = new Pregen.Spiral(a);
        s.skip(cells.size());
        assertFalse(s.hasNext());
    }

    @Test
    void area_ofRadius_coversHalfOpenBlockSquare() {
        Pregen.Area a = Pregen.Area.ofRadius(0, 0, 512, "t");   // 월드 경계 가운데 0,0 크기 1024 → 블록 −512..511
        assertEquals(-32, a.minX());
        assertEquals(31, a.maxX());
        assertEquals(64L * 64, a.total());
        Pregen.Area b = Pregen.Area.ofRadius(100.5, -20.5, 8, "t");   // 블록 92.5..108.5 → 92..108
        assertEquals(5, b.minX());
        assertEquals(6, b.maxX());
        assertEquals(-2, b.minZ());
        assertEquals(-1, b.maxZ());
        assertEquals(6, b.centerX());
        assertEquals(-2, b.centerZ());
    }

    // ------------------------------------------------------------------ 대기열 표

    @Test
    void ticket_background_promoteAndCancel() {
        GenQueue.Ticket t = GenQueue.Ticket.forBackground();
        assertTrue(t.isBackground());
        assertTrue(t.promote());
        assertFalse(t.isBackground(), "붙으면 맨 뒤에서 내려온다");
        assertFalse(t.cancelBackground(), "붙은 요청은 미리 생성을 멈춰도 취소 안 된다");
        assertTrue(t.tryStart());

        GenQueue.Ticket u = GenQueue.Ticket.forBackground();
        assertTrue(u.cancelBackground());
        assertFalse(u.promote(), "이미 취소됐으면 붙지 못한다(다시 걸어야 한다)");
        assertFalse(u.tryStart());

        GenQueue.Ticket player = new GenQueue.Ticket(true, null, 5);
        assertFalse(player.cancelBackground(), "플레이어 요청은 미리 생성 취소로 안 지운다");
    }

    // ------------------------------------------------------------------ 월드에서

    /** 생성 순서를 적는 평지 생성기. */
    private static final class OrderGen implements WorldSource {
        final List<Long> order = Collections.synchronizedList(new ArrayList<>());
        final SuperflatWorldSource flat = new SuperflatWorldSource(REG, 0);

        @Override
        public ChunkColumn generate(int cx, int cz) {
            order.add(Regionizer.chunkKey(cx, cz));
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
                new AnvilChunkCodec(REG, ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt"), BIOMES, 0, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT), base);
        src.entityStore(new RegionFileStore(dir, "entities"));
        World w = new World(src, new ChunkWorkerPool(1), sched, 1, 16, 2, 2, REG.directBits(), 7, io, q);
        w.setRegistries(REG, ItemRegistry.load("items/" + GameVersion.RESOURCE_TAG + ".txt"));
        w.configurePersistence(1_000_000, 1_000_000);
        w.worldDir(dir);
        pregen = new Pregen();
        return w;
    }

    private static Pregen.Progress awaitEnd(Pregen.Job j) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (j.state() == Pregen.State.RUNNING) {
            assertTrue(System.nanoTime() < deadline, "미리 생성이 끝나지 않는다: " + j.progress().line());
            Thread.sleep(10);
        }
        return j.progress();
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

    private static void awaitTicket(World w, ChunkPos cp) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (w.genTicket(Regionizer.chunkKey(cp.x(), cp.z())) == null) {
            assertTrue(System.nanoTime() < deadline, "생성 요청이 대기열에 안 섰다 " + cp);
            Thread.sleep(2);
        }
    }

    @Test
    void generatesWholeArea_thenSkipsWhatExists_evenAfterReopen(@TempDir Path dir) throws Exception {
        OrderGen gen = new OrderGen();
        World w = world(dir, gen);
        Pregen.Area area = new Pregen.Area(0, 0, -3, -3, 2, 2, "t");
        var r = pregen.start(w, area, null);
        assertNotNull(r.job(), r.refused());
        assertTrue(Files.exists(Pregen.file(w)), "시작하면 진행 파일");
        Pregen.Progress p = awaitEnd(r.job());
        assertEquals(Pregen.State.DONE, p.state());
        assertEquals(36, p.processed());
        assertEquals(36, p.generated());
        assertEquals(36, gen.order.size());
        assertTrue(gen.order.subList(0, 2).contains(Regionizer.chunkKey(0, 0)), "가운데부터(창 2 안에서는 디스크 확인이 끝난 순)");
        assertFalse(Files.exists(Pregen.file(w)), "끝나면 진행 파일을 지운다");
        assertNull(pregen.job(w));

        // 올라와 있는 청크는 다시 만들지 않는다
        Pregen.Progress again = awaitEnd(pregen.start(w, area, null).job());
        assertEquals(0, again.generated());
        assertEquals(36, again.skipped());
        assertEquals(36, gen.order.size());

        // 저장하고 다시 연 월드: 디스크 위치표만 보고 건너뛴다
        for (var f : w.flushAll()) f.get(30, TimeUnit.SECONDS);
        w.ioBarrier().get(30, TimeUnit.SECONDS);
        close();
        OrderGen gen2 = new OrderGen();
        World w2 = world(dir, gen2);
        Pregen.Progress reopened = awaitEnd(pregen.start(w2, new Pregen.Area(0, 0, -4, -3, 2, 2, "t"), null).job());
        assertEquals(42, reopened.processed());
        assertEquals(36, reopened.skipped(), "디스크에 있는 청크는 건너뛴다");
        assertEquals(6, reopened.generated(), "새 줄 x=−4 만 만든다");
        for (long k : gen2.order) assertEquals(-4, Regionizer.chunkX(k));
    }

    @Test
    void backgroundGoesLast_andJoinedRequestIsPromoted(@TempDir Path dir) throws Exception {
        OrderGen gen = new OrderGen();
        World w = world(dir, gen);
        CountDownLatch gate = block();
        Pregen.Area area = new Pregen.Area(0, 0, -1, -1, 1, 1, "t");   // 9 청크, 창 = 생성 스레드 1 × 2 → 2 개만 걸림
        var j = pregen.start(w, area, null).job();
        ChunkPos first = new ChunkPos(0, 0), second = new ChunkPos(-1, -1);
        awaitTicket(w, first);
        awaitTicket(w, second);
        assertTrue(w.genTicket(Regionizer.chunkKey(0, 0)).isBackground());
        // 다른 요청자(플러그인·명령 등)가 나중에 건 다른 청크와, 미리 생성이 걸어 둔 청크에 붙은 요청
        ChunkPos other = new ChunkPos(10, 10);
        var otherF = w.submitAtChunk(other, r -> r.loadChunk(other)).thenCompose(x -> x);
        awaitTicket(w, other);
        var joined = w.submitAtChunk(second, r -> r.loadChunk(second)).thenCompose(x -> x);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (w.genTicket(Regionizer.chunkKey(-1, -1)).isBackground()) {
            assertTrue(System.nanoTime() < deadline, "붙은 요청이 맨 뒤에서 안 내려온다");
            Thread.sleep(2);
        }
        gate.countDown();
        otherF.get(30, TimeUnit.SECONDS);
        joined.get(30, TimeUnit.SECONDS);
        awaitEnd(j);
        // 나중에 건 일반 요청과 미리 생성에 붙은 요청이 남은 미리 생성보다 먼저(둘끼리는 거리·넣은 순)
        assertEquals(Set.of(Regionizer.chunkKey(10, 10), Regionizer.chunkKey(-1, -1)), Set.of(gen.order.get(0), gen.order.get(1)));
        assertEquals(Regionizer.chunkKey(0, 0), gen.order.get(2), "미리 생성은 그 뒤 넣은 순");
        assertEquals(10, gen.order.size());
    }

    @Test
    void savedLabelKeepsKindAndNumbers_andReadsInTheServerLanguage(@TempDir Path dir) throws Exception {
        OrderGen gen = new OrderGen();
        World w = world(dir, gen);
        CountDownLatch gate = block();
        Pregen.Area area = Pregen.Area.ofRadius(0, 0, 40, Pregen.Label.radius(40, 0, 0));
        pregen.start(w, area, null);
        awaitTicket(w, new ChunkPos(0, 0));
        assertTrue(pregen.stop(w));
        Pregen.Saved saved = Pregen.loadSaved(w);
        assertNotNull(saved);
        assertEquals(area, saved.area(), "kind and numbers come back, not a sentence");
        try {
            assertEquals("radius 40 (center 0, 0)", saved.area().label().text());
            Lang.forceForTest("ko");
            assertEquals("반경 40 (가운데 0, 0)", saved.area().label().text(), "a file saved under one language reads in the other");
        } finally {
            Lang.forceForTest("en");
            gate.countDown();
        }
    }

    @Test
    void borderLabelRoundTripsAsKindAndNumbers(@TempDir Path dir) throws Exception {
        OrderGen gen = new OrderGen();
        World w = world(dir, gen);
        CountDownLatch gate = block();
        Pregen.Area area = Pregen.Area.ofRadius(0, 0, 512, Pregen.Label.border(1024, 0, 0));
        pregen.start(w, area, null);
        awaitTicket(w, new ChunkPos(0, 0));
        assertTrue(pregen.stop(w));
        try {
            Pregen.Saved saved = Pregen.loadSaved(w);
            assertNotNull(saved);
            assertEquals(area, saved.area());
            assertEquals("world border (center 0, 0, size 1024)", saved.area().label().text());
            Lang.forceForTest("ko");
            assertEquals("월드 경계 (가운데 0, 0, 크기 1024)", saved.area().label().text());
        } finally {
            Lang.forceForTest("en");
            gate.countDown();
        }
    }

    /** 이름표를 문장으로 적던 옛 진행 파일(label=…, 종류 칸 없음)도 읽고 이어 하며, 알아볼 수 있는 문장은 종류+숫자로 올려 읽는다. */
    @Test
    void legacySentenceLabelFileIsReadResumedAndUpgraded(@TempDir Path dir) throws Exception {
        OrderGen gen = new OrderGen();
        World w = world(dir, gen);
        Path file = Pregen.file(w);
        Files.createDirectories(file.getParent());
        writeLegacy(file, "반경 40 (가운데 0, 0)");
        Pregen.Saved saved = Pregen.loadSaved(w);
        assertNotNull(saved);
        assertEquals(Pregen.Label.radius(40, 0, 0), saved.area().label(), "a recognisable old sentence is upgraded");
        writeLegacy(file, "월드 경계 (가운데 10, -20, 크기 2000)");
        assertEquals(Pregen.Label.border(2000, 10, -20), Pregen.loadSaved(w).area().label());
        writeLegacy(file, "내 마음대로 적은 이름");
        assertEquals(Pregen.Label.literal("내 마음대로 적은 이름"), Pregen.loadSaved(w).area().label(), "anything else is shown as written");

        var r = pregen.resume(w, null);
        assertNotNull(r.job(), r.refused());
        Pregen.Progress p = awaitEnd(r.job());
        assertEquals(Pregen.State.DONE, p.state());
        assertEquals(25, p.processed());
    }

    @Test
    void unknownLabelKindIsShownAsTextNotNull() {
        assertEquals("weird", new Pregen.Label("weird", 0, 0, 0, null).text());
        assertEquals("kept", new Pregen.Label("weird", 0, 0, 0, "kept").text());
    }

    private static void writeLegacy(Path file, String label) throws java.io.IOException {
        java.util.Properties p = new java.util.Properties();
        p.setProperty("centerX", "0");
        p.setProperty("centerZ", "0");
        p.setProperty("minX", "-2");
        p.setProperty("minZ", "-2");
        p.setProperty("maxX", "2");
        p.setProperty("maxZ", "2");
        p.setProperty("label", label);
        p.setProperty("next", "0");
        try (var out = Files.newOutputStream(file)) {
            p.store(out, "legacy");
        }
    }

    @Test
    void stop_cancelsQueuedBackground_savesPosition_resumeFinishes(@TempDir Path dir) throws Exception {
        OrderGen gen = new OrderGen();
        World w = world(dir, gen);
        CountDownLatch gate = block();
        Pregen.Area area = new Pregen.Area(0, 0, -2, -2, 2, 2, "t");
        var j = pregen.start(w, area, null).job();
        awaitTicket(w, new ChunkPos(0, 0));
        assertNotNull(pregen.start(w, area, null).refused(), "한 차원에 하나만");
        assertTrue(pregen.stop(w));
        assertFalse(pregen.stop(w), "두 번 멈추지 않는다");
        assertEquals(Pregen.State.STOPPED, j.state());
        assertTrue(w.genTicket(Regionizer.chunkKey(0, 0)) == null || w.genTicket(Regionizer.chunkKey(0, 0)).cancelled(),
                "시작 전 미리 생성 요청은 취소한다");
        Pregen.Saved saved = Pregen.loadSaved(w);
        assertNotNull(saved);
        assertEquals(0, saved.next(), "아무것도 못 끝냈으면 처음부터");
        assertEquals(area, saved.area());
        gate.countDown();
        // 취소된 요청은 만들지 않는다
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (q.queuedGen() > 0) {
            assertTrue(System.nanoTime() < deadline);
            Thread.sleep(2);
        }
        Thread.sleep(50);
        assertEquals(0, gen.order.size());

        var r = pregen.resume(w, null);
        assertNotNull(r.job(), r.refused());
        Pregen.Progress p = awaitEnd(r.job());
        assertEquals(Pregen.State.DONE, p.state());
        assertEquals(25, p.processed());
        assertEquals(25, p.generated() + p.skipped() + p.failed(), p.line());
        assertEquals(25, gen.order.size());
        assertNull(Pregen.loadSaved(w));
        assertNotNull(pregen.resume(w, null).refused(), "끝난 뒤엔 이어 할 것이 없다");
    }

    @Test
    void cancel_deletesProgress(@TempDir Path dir) throws Exception {
        World w = world(dir, new OrderGen());
        CountDownLatch gate = block();
        pregen.start(w, new Pregen.Area(0, 0, -2, -2, 2, 2, "t"), null);
        assertTrue(pregen.cancel(w));
        assertNull(Pregen.loadSaved(w));
        assertNull(pregen.job(w));
        assertFalse(pregen.cancel(w), "지울 것이 없다");
        gate.countDown();
    }
}
