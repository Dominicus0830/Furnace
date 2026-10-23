// 동적 region: 떨어진 청크는 다른 region, 이어지면 합쳐지고, 가운데가 빠지면 다시 나뉜다. 작업은 합치기·나누기 중에도 사라지지 않는다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
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

class RegionizerTest {

    private FurnaceSchedulerImpl sched;
    private World w;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(10_000_000L, 3);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2), sched, 3, 4, 1, 2, 2,
                reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor(),
                java.util.concurrent.Executors.newSingleThreadExecutor());
        w.configurePersistence(Integer.MAX_VALUE, 2);
    }

    @AfterEach
    void tearDown() {
        sched.close();
    }

    /** 구역 = 16 청크. 구역 x 번호의 청크 하나를 이 region 의 있는 청크로 만든다. */
    private void load(int sectionX) throws Exception {
        ChunkPos cp = new ChunkPos(sectionX * 16, 0);
        try {
            w.submitAtChunk(cp, r -> r.chunkAt(cp)).get(5, TimeUnit.SECONDS);
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
        Region r = w.regionizer().ownerOf(sectionX * 16, 0);
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
        Player keeper = new Player(99, UUID.randomUUID(), "k", new EmbeddedChannel());
        w.subscribeChunk(new ChunkPos(0, 0), keeper);
        w.subscribeChunk(new ChunkPos(48, 0), keeper);
        w.startTicking();
        await("다리가 사라지면 나뉜다", () -> w.regionCount() == 2);
        assertNotEquals(ownerId(0), ownerId(3));
        assertEquals(-1, ownerId(1));
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
            ChunkPos cp = new ChunkPos((i % 3) * 16, 0);   // 구역 0·1·2 로 번갈아
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

    @Test
    void delayedTask_followsMerge() throws Exception {
        load(0);
        load(2);
        CompletableFuture<Void> f = sched.region(new org.domi.furnace.api.sched.RegionPos(32, 0)).executeDelayed(() -> { }, 20);
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
        CompletableFuture<Boolean> b = w.submitAtChunk(new ChunkPos(160, 0), r -> await(both));
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
