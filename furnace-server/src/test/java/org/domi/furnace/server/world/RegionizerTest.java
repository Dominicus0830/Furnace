// 동적 region: 떨어진 청크는 다른 region, 이어지면 합쳐지고, 가운데가 빠지면 다시 나뉜다. 작업은 합치기·나누기 중에도 사라지지 않는다.
// 구역 크기 shift 3(기본)·4(예전 기본) 둘 다로 돈다(T-004).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

@ParameterizedClass(name = "shift {0}")
@ValueSource(ints = {3, 4})
class RegionizerTest {

    @Parameter
    int shift;

    private FurnaceSchedulerImpl sched;
    private World w;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(10_000_000L, 3);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, 3, shift, 1, 2, 2,
                reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor(),
                java.util.concurrent.Executors.newSingleThreadExecutor());
        w.configurePersistence(Integer.MAX_VALUE, 2);
    }

    @AfterEach
    void tearDown() {
        sched.close();
    }

    /** nanoTime 오버플로 경계: 값으로는 a 가 작아 보여도 차이로는 b 가 이르다. 정상 범위에서는 Long.compare 와 같다. */
    @Test
    void compareDueIsOverflowSafe() {
        long nearMax = Long.MAX_VALUE - 5, wrapped = Long.MIN_VALUE + 5;   // wrapped 는 nearMax 보다 10ns 뒤
        assertTrue(Regionizer.compareDue(nearMax, wrapped) < 0);
        assertTrue(Regionizer.compareDue(wrapped, nearMax) > 0);
        assertEquals(0, Regionizer.compareDue(wrapped, wrapped));
        for (long a : new long[] {-7, 0, 3, 1_000_000_000L}) {
            for (long b : new long[] {-7, 0, 3, 1_000_000_000L}) assertEquals(Long.compare(a, b), Regionizer.compareDue(a, b));
        }
    }

    /** 구역 x 번호의 첫 청크 x(구역 = 2^shift 청크). */
    private int cx(int sectionX) {
        return sectionX << shift;
    }

    /** 구역 x 번호의 청크 하나를 이 region 의 있는 청크로 만든다. */
    private void load(int sectionX) throws Exception {
        ChunkPos cp = new ChunkPos(cx(sectionX), 0);
        try {
            w.submitAtChunk(cp, r -> r.chunkAtBlocking(cp)).get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("timeout\n" + w.regionizer().debugState());
        }
    }

    private void await(String what, java.util.function.BooleanSupplier cond) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < deadline) Thread.sleep(5);
        assertTrue(cond.getAsBoolean(), what);
    }

    private int ownerId(int sectionX) {
        Region r = w.regionizer().ownerOf(cx(sectionX), 0);
        return r == null ? -1 : r.id();
    }

    @Test
    void farChunks_areSeparateRegions_bridgeMerges_removingBridgeSplits() throws Exception {
        load(0);
        load(3);
        assertEquals(2, w.regionCount(), "떨어진 구역(거리 3)은 다른 region");
        assertNotEquals(ownerId(0), ownerId(3));

        load(2);   // 구역 3 의 이웃 → 3 쪽 region
        assertEquals(ownerId(3), ownerId(2));
        load(1);   // 0 과 2 둘 다의 이웃 → 합쳐진다
        await("다리가 생기면 하나로 합쳐진다", () -> w.regionCount() == 1);
        assertEquals(ownerId(0), ownerId(3));

        // 양 끝 청크는 구독자로 붙잡고, 다리(1·2)는 구독자가 없어 퇴출 → 다시 둘로
        Player keeper = new Player(99, UUID.randomUUID(), "k", new ConcurrentEmbeddedChannel());
        w.subscribeChunk(new ChunkPos(0, 0), keeper);
        w.subscribeChunk(new ChunkPos(cx(3), 0), keeper);
        w.startTicking();
        await("다리가 사라지면 나뉜다", () -> w.regionCount() == 2);
        assertNotEquals(ownerId(0), ownerId(3));
        assertEquals(-1, ownerId(1));
    }

    /** 띠: 구역 0 의 마지막 청크와 구역 2 의 첫 청크는 다른 region 이고, 두 region 의 가장 가까운 블록은 축으로 2^shift×16+1 떨어진다(기본 129). */
    @Test
    void separateRegions_areAtLeastBandPlusOneBlocksApart() throws Exception {
        ChunkPos lastOf0 = new ChunkPos(cx(1) - 1, 0), firstOf2 = new ChunkPos(cx(2), 0);
        for (ChunkPos cp : new ChunkPos[] {lastOf0, firstOf2}) w.submitAtChunk(cp, r -> r.chunkAtBlocking(cp)).get(5, TimeUnit.SECONDS);
        assertEquals(2, w.regionCount(), "가운데 구역이 비면 다른 region\n" + w.regionizer().debugState());
        assertNotEquals(w.regionizer().ownerOf(lastOf0.x(), 0).id(), w.regionizer().ownerOf(firstOf2.x(), 0).id());
        int gap = firstOf2.x() * 16 - (lastOf0.x() * 16 + 15);   // 두 region 의 가장 가까운 블록 x 차
        assertEquals(RegionBandRadiusTest.bandBlocks(shift, 1) + 1, gap);
        assertTrue(gap >= 129, "띠 128 이하 반경 읽기는 남의 region 에 닿지 않는다(shift " + shift + ": " + gap + ")");
        load(1);   // 가운데 구역에 청크가 생기면 합쳐진다
        await("합쳐진다", () -> w.regionCount() == 1);
    }

    @Test
    void tasks_areNotLost_whileRegionsMerge() throws Exception {
        load(0);
        load(2);
        assertEquals(2, w.regionCount());
        AtomicInteger ran = new AtomicInteger();
        int n = 2000;
        CompletableFuture<?>[] fs = new CompletableFuture<?>[n];
        for (int i = 0; i < n; i++) {
            ChunkPos cp = new ChunkPos(cx(i % 3), 0);   // 구역 0·1·2 로 번갈아
            fs[i] = w.submitAtChunk(cp, r -> {
                assertTrue(r.isOwnerThread());
                return ran.incrementAndGet();
            });
            if (i == n / 2) load(1);   // 도중에 다리 → 합치기
        }
        CompletableFuture.allOf(fs).get(10, TimeUnit.SECONDS);
        assertEquals(n, ran.get(), "합치기 중에도 작업이 하나도 사라지지 않는다");
        await("합쳐진다", () -> w.regionCount() == 1);
    }

    /** F8: 틱 사이(빛 계산 전)에 놓은 광원이 region 합치기를 거쳐도 계산된다(실제 Regionizer 합치기 경로). */
    @Test
    void pendingLightCheck_survivesRealMerge() throws Exception {
        load(0);
        load(2);
        assertEquals(2, w.regionCount());
        org.domi.furnace.server.block.Pos p = new org.domi.furnace.server.block.Pos(cx(2) * 16 + 5, -59, 5);
        int glow = org.domi.furnace.server.block.B.defaultState("glowstone");
        w.submitAtChunk(new ChunkPos(cx(2), 0), r -> {
            r.level().runLightUpdates();   // 올라온 청크 빛 정리
            r.level().setBlock(p, glow, 2);   // 틱 끝 계산 전
            return null;
        }).get(5, TimeUnit.SECONDS);
        load(1);   // 다리 → 합치기(둘 다 쉬는 중)
        await("합쳐진다", () -> w.regionCount() == 1);
        int light = w.submitAtChunk(new ChunkPos(cx(2), 0), r -> {
            r.level().runLightUpdates();
            return r.level().getBlockLight(p);
        }).get(5, TimeUnit.SECONDS);
        assertEquals(15, light, "합친 region 이 미처리 빛 검사를 이어받아 계산한다");
    }

    @Test
    void delayedTask_followsMerge() throws Exception {
        load(0);
        load(2);
        CompletableFuture<Void> f = sched.region(new org.domi.furnace.api.sched.RegionPos(cx(2), 0)).executeDelayed(() -> { }, 20);
        load(1);
        f.get(5, TimeUnit.SECONDS);
        await("합쳐진다", () -> w.regionCount() == 1);
    }

    @Test
    void separateRegions_tickInParallel() throws Exception {
        load(0);
        load(10);
        assertEquals(2, w.regionCount());
        java.util.concurrent.CountDownLatch both = new java.util.concurrent.CountDownLatch(2);
        CompletableFuture<Boolean> a = w.submitAtChunk(new ChunkPos(0, 0), r -> await(both));
        CompletableFuture<Boolean> b = w.submitAtChunk(new ChunkPos(cx(10), 0), r -> await(both));
        assertTrue(a.get(5, TimeUnit.SECONDS) && b.get(5, TimeUnit.SECONDS), "두 region 의 작업이 동시에 돈다");
    }

    @Test
    void regionForStrayTask_isRemoved_evenWithDelayedTaskOutlivingItsSection() throws Exception {
        // 아무 청크도 없는 곳의 작업은 빈 구역을 가진 region 을 만든다 — 지연 작업이 구역 유예(1초)보다 오래 남아도 끝나면 없어진다
        CompletableFuture<Void> f = sched.region(new org.domi.furnace.api.sched.RegionPos(1000, 1000)).executeDelayed(() -> { }, 300);   // 틱 10ms → 3초
        await("region 이 생긴다", () -> w.regionCount() == 1);
        f.get(5, TimeUnit.SECONDS);
        long deadline = System.currentTimeMillis() + 8000;
        while (w.regionCount() != 0 && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertEquals(0, w.regionCount(), "빈 region 이 남지 않는다\n" + w.regionizer().debugState());
    }

    @Test
    void forEachRegion_runsOncePerRegion_andFollowsMerge() throws Exception {
        load(0);
        load(2);
        AtomicInteger ran = new AtomicInteger();
        var fs = w.forEachRegion(r -> {
            assertTrue(r.isOwnerThread());
            ran.incrementAndGet();
        });
        assertEquals(2, fs.size());
        CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
        assertEquals(2, ran.get());
    }

    @Test
    void clientCoordinates_withoutOwner_doNotCreateRegions() throws Exception {
        load(0);
        assertEquals(1, w.regionCount());
        CompletableFuture<Integer> f = new CompletableFuture<>();
        w.runAtRequested(5_000_000, 5_000_000, 8, 8, r -> f.complete(r.id()));
        assertEquals(ownerId(0), f.get(5, TimeUnit.SECONDS), "주인 없는 좌표는 플레이어 청크 region 에서");
        assertEquals(1, w.regionCount());
    }

    private static boolean await(java.util.concurrent.CountDownLatch l) {
        l.countDown();
        try {
            return l.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }
}
