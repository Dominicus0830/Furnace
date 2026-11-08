// region 빌림(RegionBorrow): 전역 스레드의 빌림 구간은 만진 region 만 틱 사이에 빌리고(도는 틱은 끝날 때까지 기다린다), 다른 region 은 계속 돈다.
// 구간이 닫히면 돌려주고 밀린 작업이 돈다. 구간 밖·전역이 아닌 스레드는 빌리지 않는다. 빌린 사이의 합치기는 돌려줄 때 끝난다.
// 실패 모양(PR #144 독립 검토 R1~R4·S1): 서로 기다림은 곧바로 포기, 멈춘 region 은 구간에서 한 번만 기다림, 닫지 않은 구간은 작업 끝에 닫힘.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.api.sched.GlobalScheduler;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegionBorrowTest {

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
    void tearDown() { sched.close(); }

    /** 구역(16 청크) x 번호의 청크 하나를 로드한다. */
    private void load(int sectionX) throws Exception {
        ChunkPos cp = new ChunkPos(sectionX * 16, 0);
        w.submitAtChunk(cp, r -> r.chunkAtBlocking(cp)).get(5, TimeUnit.SECONDS);
    }

    private GlobalScheduler global() { return sched.global(); }

    private <T> CompletableFuture<T> onGlobal(java.util.function.Supplier<T> task) { return global().submit(task); }

    private static void await(CountDownLatch l) {
        try {
            assertTrue(l.await(5, TimeUnit.SECONDS), "기다림 시간 초과");
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void borrowWaitsForTheRunningTick_thenOwnsIt_whileOtherRegionsKeepRunning() throws Exception {
        load(0);
        load(10);
        assertEquals(2, w.regionCount());
        CountDownLatch tickMayEnd = new CountDownLatch(1), tickStarted = new CountDownLatch(1);
        // region A 가 긴 틱 중(작업 큐 작업이 막고 있다)
        CompletableFuture<Boolean> longTick = w.submitAtChunk(new ChunkPos(0, 0), r -> {
            tickStarted.countDown();
            await(tickMayEnd);
            return true;
        });
        await(tickStarted);

        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean owned = new AtomicBoolean();
        AtomicLong waitedNanos = new AtomicLong();
        CompletableFuture<Void> borrower = onGlobal(() -> {
            try (var access = global().openRegionAccess()) {
                long t0 = System.nanoTime();
                boolean claimed = RegionBorrow.claimChunk(w, 0, 0);
                waitedNanos.set(System.nanoTime() - t0);
                owned.set(claimed && w.regionizer().ownerOf(0, 0).isOwnerThread() && w.chunkScheduler(0, 0) instanceof
                        org.domi.furnace.api.concurrent.ThreadOwner o && o.isOwnerThread());
                held.countDown();
                await(release);
            }
            return null;
        });
        Thread.sleep(80);
        assertEquals(1, held.getCount(), "도는 틱이 끝나기 전에는 빌리지 못한다");
        tickMayEnd.countDown();
        assertTrue(longTick.get(5, TimeUnit.SECONDS));
        await(held);
        assertTrue(owned.get(), "틱이 끝나면 전역 스레드가 그 region 의 소유 스레드가 된다");
        assertTrue(waitedNanos.get() >= TimeUnit.MILLISECONDS.toNanos(60), "도는 틱이 끝날 때까지 기다렸다: " + waitedNanos.get());

        // 빌린 동안: 다른 region 은 그대로 돌고, 빌린 region 의 작업은 돌려줄 때까지 밀린다
        assertTrue(w.submitAtChunk(new ChunkPos(160, 0), r -> r.isOwnerThread()).get(2, TimeUnit.SECONDS), "만지지 않은 region 은 계속 돈다");
        CompletableFuture<Boolean> queued = w.submitAtChunk(new ChunkPos(0, 0), r -> r.isOwnerThread());
        Thread.sleep(80);
        assertFalse(queued.isDone(), "빌린 region 은 틱(작업 큐)을 쉰다");
        release.countDown();
        borrower.get(5, TimeUnit.SECONDS);
        assertTrue(queued.get(5, TimeUnit.SECONDS), "돌려주면 밀린 작업이 그 region 스레드에서 돈다");
        assertFalse(w.regionizer().ownerOf(0, 0).isOwnerThread(), "돌려준 뒤 전역은 소유 스레드가 아니다");
    }

    @Test
    void noBorrowOutsideASection_orOffTheGlobalThread() throws Exception {
        load(0);
        assertFalse(onGlobal(() -> RegionBorrow.claimChunk(w, 0, 0)).get(5, TimeUnit.SECONDS), "구간 밖 전역 스레드는 빌리지 않는다(Furnace 플러그인 규칙 그대로)");
        assertSame(GlobalScheduler.RegionAccess.NONE, RegionBorrow.open(), "전역이 아닌 스레드는 빈 손잡이");
        assertSame(GlobalScheduler.RegionAccess.NONE, global().openRegionAccess());
        assertFalse(RegionBorrow.claimChunk(w, 0, 0));
        assertFalse(onGlobal(() -> {
            try (var access = global().openRegionAccess()) {
                return RegionBorrow.claimChunk(w, 9999, 9999);
            }
        }).get(5, TimeUnit.SECONDS), "주인이 없는(로드 안 된) 곳은 빌릴 것이 없다");
        // 바깥 구간 안에서 연 구간이 닫혀도 바깥이 닫힐 때까지 쥔다
        assertTrue(onGlobal(() -> {
            try (var outer = global().openRegionAccess()) {
                try (var inner = global().openRegionAccess()) {
                    if (!RegionBorrow.claimChunk(w, 0, 0)) return false;
                }
                if (!w.regionizer().ownerOf(0, 0).isOwnerThread()) return false;   // 안쪽이 닫혀도 아직 쥐고 있다
            }
            return !w.regionizer().ownerOf(0, 0).isOwnerThread();
        }).get(5, TimeUnit.SECONDS), "겹친 구간은 가장 바깥이 닫힐 때 돌려준다");
        assertTrue(w.submitAtChunk(new ChunkPos(0, 0), r -> true).get(5, TimeUnit.SECONDS), "돌려준 region 은 다시 돈다");
    }

    @Test
    void mergeRequestedWhileBorrowed_completesWithoutDeadlock() throws Exception {
        load(0);
        load(2);
        assertEquals(2, w.regionCount());
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicBoolean secondClaimed = new AtomicBoolean();
        AtomicLong secondWait = new AtomicLong();
        CompletableFuture<Void> borrower = onGlobal(() -> {
            try (var access = global().openRegionAccess()) {
                assertTrue(RegionBorrow.claimChunk(w, 0, 0));
                held.countDown();
                await(release);
                // 그 사이 구역 2 의 region 이 쥔 region 에 양보했을 수 있다 — 쥔 쪽이 흡수하거나 따로 빌려야지 서로 기다리면 안 된다
                long t0 = System.nanoTime();
                secondClaimed.set(RegionBorrow.claimChunk(w, 32, 0));
                secondWait.set(System.nanoTime() - t0);
            }
            return null;
        });
        await(held);
        // 다리(구역 1): 쥔 region 과 구역 2 region 을 합치게 한다. 작업은 빌린 region 이 돌려줄 때까지 밀릴 수 있어 기다리지 않는다
        ChunkPos bridge = new ChunkPos(16, 0);
        CompletableFuture<?> bridged = w.submitAtChunk(bridge, r -> r.chunkAtBlocking(bridge));
        Thread.sleep(150);
        release.countDown();
        borrower.get(10, TimeUnit.SECONDS);
        assertTrue(secondClaimed.get(), "합치기를 기다리는 region 도 빌린다");
        assertTrue(secondWait.get() < TimeUnit.SECONDS.toNanos(2), "서로 기다리지 않는다: " + secondWait.get());
        bridged.get(5, TimeUnit.SECONDS);
        long deadline = System.currentTimeMillis() + 5000;
        while (w.regionCount() != 1 && System.currentTimeMillis() < deadline) Thread.sleep(5);
        assertEquals(1, w.regionCount(), "돌려준 뒤 합치기가 끝난다");
        assertTrue(w.submitAtChunk(new ChunkPos(32, 0), r -> r.isOwnerThread()).get(5, TimeUnit.SECONDS), "합친 region 은 계속 돈다");
    }

    // ------------------------------------------------------------------ 실패 모양(독립 검토 재현을 고친 뒤의 기대)

    @Test
    void regionBlockedOnAGlobalResult_isNotWaitedFor() throws Exception {
        load(0);
        AtomicLong globalWait = new AtomicLong();
        AtomicBoolean claimed = new AtomicBoolean(true);
        CompletableFuture<Long> regionSide = w.submitAtChunk(new ChunkPos(0, 0), r -> {
            long s = System.nanoTime();
            CompletableFuture<Void> sync = global().submit(() -> {   // callSyncMethod 몸통: 같은 region 을 만진다
                try (var access = global().openRegionAccess()) {
                    long g = System.nanoTime();
                    claimed.set(RegionBorrow.claimChunk(w, 0, 0));
                    globalWait.set(System.nanoTime() - g);
                }
                return null;
            });
            try {
                sync.get(30, TimeUnit.SECONDS);   // region 스레드가 전역 결과를 막고 기다린다
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return System.nanoTime() - s;
        });
        long regionBlocked = regionSide.get(10, TimeUnit.SECONDS);
        assertFalse(claimed.get(), "서로 기다리면 빌리지 않는다(예전처럼 소유 스레드가 아니다)");
        assertTrue(globalWait.get() < TimeUnit.MILLISECONDS.toNanos(300), "곧바로 포기한다: " + globalWait.get() / 1_000_000 + "ms");
        assertTrue(regionBlocked < TimeUnit.MILLISECONDS.toNanos(500), "region 도 오래 서지 않는다: " + regionBlocked / 1_000_000 + "ms");
    }

    @Test
    void regionWaitingOnALockTheGlobalHolds_isNotWaitedFor() throws Exception {
        load(0);
        Object pluginLock = new Object();
        CountDownLatch regionInTick = new CountDownLatch(1), globalHasLock = new CountDownLatch(1);
        CompletableFuture<Long> region = w.submitAtChunk(new ChunkPos(0, 0), r -> {
            regionInTick.countDown();
            await(globalHasLock);
            long s = System.nanoTime();
            synchronized (pluginLock) { }   // 리스너가 캐시 잠금을 잡는다
            return System.nanoTime() - s;
        });
        await(regionInTick);
        long g = onGlobal(() -> {
            try (var access = global().openRegionAccess()) {
                synchronized (pluginLock) {
                    globalHasLock.countDown();
                    long s = System.nanoTime();
                    assertFalse(RegionBorrow.claimChunk(w, 0, 0));
                    return System.nanoTime() - s;
                }
            }
        }).get(10, TimeUnit.SECONDS);
        region.get(10, TimeUnit.SECONDS);
        assertTrue(g < TimeUnit.MILLISECONDS.toNanos(300), "전역이 쥔 잠금을 그 region 이 기다리면 곧바로 포기: " + g / 1_000_000 + "ms");
    }

    @Test
    void aStuckRegionIsWaitedForOncePerSection() throws Exception {
        load(0);
        load(10);
        CountDownLatch stuck = new CountDownLatch(1), started = new CountDownLatch(1);
        w.submitAtChunk(new ChunkPos(0, 0), r -> {
            started.countDown();
            try {
                stuck.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        await(started);
        long[] took = onGlobal(() -> {
            long s = System.nanoTime();
            boolean other;
            try (var access = global().openRegionAccess()) {
                for (int i = 0; i < 3; i++) assertFalse(RegionBorrow.claimChunk(w, 0, 0));   // 멈춘 region 의 칸 셋
                other = RegionBorrow.claimChunk(w, 160, 0);   // 쉬는 다른 region 은 한도를 다 쓴 뒤에도 곧바로 쥔다
            }
            return new long[] {System.nanoTime() - s, other ? 1 : 0};
        }).get(20, TimeUnit.SECONDS);
        stuck.countDown();
        long limit = RegionBorrow.WAIT_NANOS;
        assertTrue(took[0] < limit + TimeUnit.MILLISECONDS.toNanos(500), "구간 하나에서 한도를 한 번만 기다린다: " + took[0] / 1_000_000 + "ms");
        assertEquals(1, took[1], "쉬는 region 은 곧바로 빌린다");
    }

    @Test
    void anUnclosedSectionIsClosedWhenTheGlobalTaskEnds() throws Exception {
        load(0);
        load(10);
        AtomicReference<GlobalScheduler.RegionAccess> leaked = new AtomicReference<>();
        assertTrue(onGlobal(() -> {
            leaked.set(global().openRegionAccess());   // 닫지 않는다
            return RegionBorrow.claimChunk(w, 0, 0);
        }).get(5, TimeUnit.SECONDS));
        assertTrue(w.submitAtChunk(new ChunkPos(0, 0), r -> r.isOwnerThread()).get(2, TimeUnit.SECONDS), "작업이 끝나면 빌린 region 을 돌려받는다");
        assertFalse(onGlobal(() -> RegionBorrow.active() || RegionBorrow.claimChunk(w, 160, 0)).get(5, TimeUnit.SECONDS),
                "뒤의 무관한 전역 작업은 구간 밖이다");
        assertFalse(leaked.get().isOpen(), "서버가 닫은 손잡이");
        onGlobal(() -> {
            leaked.get().close();   // 늦게 닫아도 아무 일 없다
            return null;
        }).get(5, TimeUnit.SECONDS);
    }

    /** 전역이 구간을 열어 무작위 두 구역을 빌리는 동안 다리 청크로 연쇄 병합 — 못 빌림 0, 빌림 상태가 남지 않고 모든 region 이 돈다. */
    @Test
    void borrowingUnderChainedMerges_leavesNothingBehind() throws Exception {
        int sections = 12;
        for (int i = 0; i < sections; i += 2) load(i);
        long fails0 = RegionBorrow.stats()[2];
        Random rnd = new Random(144);
        AtomicReference<Throwable> err = new AtomicReference<>();
        AtomicInteger rounds = new AtomicInteger();
        List<CompletableFuture<?>> bridges = new ArrayList<>();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        int bridgeAt = 1;
        while (System.nanoTime() < end) {
            int a = rnd.nextInt(sections), b = rnd.nextInt(sections);
            CompletableFuture<Void> f = global().submit(() -> {
                try (var access = global().openRegionAccess()) {
                    for (int s : new int[] {a, b}) {
                        if (w.regionizer().ownerOf(s * 16, 0) == null) continue;
                        if (!RegionBorrow.claimChunk(w, s * 16, 0)) throw new AssertionError("못 빌림 " + s);
                        if (!w.regionizer().ownerOf(s * 16, 0).isOwnerThread()) throw new AssertionError("빌렸는데 주인이 아님 " + s);
                    }
                    Thread.sleep(rnd.nextInt(3));
                } catch (Throwable t) {
                    err.compareAndSet(null, t);
                }
                return null;
            });
            if (rounds.incrementAndGet() % 20 == 0 && bridgeAt < sections) {
                ChunkPos cp = new ChunkPos(bridgeAt * 16, 0);
                bridges.add(w.submitAtChunk(cp, r -> r.chunkAtBlocking(cp)));
                bridgeAt += 2;
            }
            f.get(20, TimeUnit.SECONDS);
        }
        for (var f : bridges) f.get(20, TimeUnit.SECONDS);
        if (err.get() != null) throw new AssertionError(err.get());
        for (int s = 0; s < sections; s++) {
            if (w.regionizer().ownerOf(s * 16, 0) == null) continue;
            assertTrue(w.submitAtChunk(new ChunkPos(s * 16, 0), r -> r.isOwnerThread()).get(5, TimeUnit.SECONDS), "구역 " + s + " region 이 돈다");
        }
        w.regionizer().lock.lock();
        try {
            for (int s = 0; s < sections; s++) {
                Region r = w.regionizer().ownerOf(s * 16, 0);
                if (r == null) continue;
                assertFalse(r.borrowed, "빌림 남음");
                assertEquals(0, r.borrowWanted, "borrowWanted 남음");
            }
        } finally {
            w.regionizer().lock.unlock();
        }
        assertEquals(fails0, RegionBorrow.stats()[2], "못 빌림(교착·한도 초과) 없음");
    }
}
