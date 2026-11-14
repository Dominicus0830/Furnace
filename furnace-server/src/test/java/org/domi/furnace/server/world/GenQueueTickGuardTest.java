// T-172 생성 조절기: 끄면(기본) 동시 생성 수가 스레드 수 그대로(예전 비조절 경로), 켜면 TPS 가 낮을 때 줄고 회복하면 늘며,
// 위치 없는 작업(이어 하기)은 상한에 막히지 않는다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GenQueueTickGuardTest {

    private static final int THREADS = 4;
    private GenQueue q;
    private final CountDownLatch gate = new CountDownLatch(1);
    private final AtomicInteger running = new AtomicInteger(), peak = new AtomicInteger(), finished = new AtomicInteger();

    private static GenQueue.Settings settings() {
        return GenQueue.Settings.fromSystem(THREADS, 16);
    }

    @Test
    void guardThreadRaisesLimitWhenNoRegionIsSlow() throws Exception {
        q = new GenQueue(THREADS, settings(), true);   // 조절기 스레드가 돈다 — 잴 region 이 없으면 20 으로 보고 늘린다
        for (int i = 0; i < THREADS; i++) q.adjustLimit(0.0);
        assertEquals(1, q.genLimit());
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (q.genLimit() < THREADS && System.nanoTime() < end) Thread.sleep(20);
        assertEquals(THREADS, q.genLimit());
    }

    @AfterEach
    void close() throws Exception {
        gate.countDown();
        if (q != null) { q.shutdown(); assertTrue(q.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    /** 생성 요청 n 개(위치 없는 월드 = 플레이어 없는 차원, 고정). 문이 열릴 때까지 막혀 있다. */
    private void submitGen(int n) {
        for (int i = 0; i < n; i++) {
            q.tagged(null, i, 0, null).execute(() -> {
                peak.accumulateAndGet(running.incrementAndGet(), Math::max);
                try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                running.decrementAndGet();
                finished.incrementAndGet();
            });
        }
    }

    private void awaitRunning(int n) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (running.get() < n && System.nanoTime() < end) Thread.sleep(5);
        assertEquals(n, running.get());
    }

    /** 조금 기다려도 더 시작하지 않는지. */
    private void assertStaysAt(int n) throws InterruptedException {
        Thread.sleep(200);
        assertEquals(n, running.get());
    }

    @Test
    void offByDefault_allThreadsGenerate_sameAsUnthrottled() throws Exception {
        q = new GenQueue(THREADS, settings());
        assertFalse(q.tickGuard());
        assertEquals(THREADS, q.genLimit());
        assertEquals(THREADS, q.adjustLimit(1.0), "꺼져 있으면 TPS 가 낮아도 상한을 바꾸지 않는다");
        submitGen(THREADS * 2);
        awaitRunning(THREADS);
        assertStaysAt(THREADS);
        assertEquals(THREADS, q.genLimit());
        gate.countDown();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (finished.get() < THREADS * 2 && System.nanoTime() < end) Thread.sleep(5);
        assertEquals(THREADS * 2, finished.get());
        assertEquals(THREADS, peak.get());
    }

    @Test
    void onButHealthy_keepsFullConcurrency() throws Exception {
        q = new GenQueue(THREADS, settings(), true, false);
        assertTrue(q.tickGuard());
        assertEquals(THREADS, q.adjustLimit(20.0));
        assertEquals(THREADS, q.adjustLimit(19.5), "19 이상 19.8 미만이면 그대로");
        submitGen(THREADS * 2);
        awaitRunning(THREADS);
        assertStaysAt(THREADS);
    }

    @Test
    void lowTps_shrinksToOne_thenRecovers() throws Exception {
        q = new GenQueue(THREADS, settings(), true, false);
        for (int i = 0; i < 10; i++) q.adjustLimit(5.0);
        assertEquals(1, q.genLimit(), "1 아래로는 줄지 않는다");
        submitGen(THREADS * 2);
        awaitRunning(1);
        assertStaysAt(1);

        // 이어 하기(위치 없는 작업)는 상한에 막히지 않는다
        CountDownLatch plainRan = new CountDownLatch(1);
        q.execute(plainRan::countDown);
        assertTrue(plainRan.await(5, TimeUnit.SECONDS));

        q.adjustLimit(19.9);
        awaitRunning(2);
        assertStaysAt(2);
        for (int i = 0; i < 10; i++) q.adjustLimit(20.0);
        assertEquals(THREADS, q.genLimit(), "스레드 수 위로는 늘지 않는다");
        awaitRunning(THREADS);
        assertStaysAt(THREADS);

        gate.countDown();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (finished.get() < THREADS * 2 && System.nanoTime() < end) Thread.sleep(5);
        assertEquals(THREADS * 2, finished.get());
    }

    @Test
    void limitedQueue_drainsOneAtATime() throws Exception {
        q = new GenQueue(THREADS, settings(), true, false);
        for (int i = 0; i < THREADS; i++) q.adjustLimit(0.0);
        List<Integer> order = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger concurrent = new AtomicInteger(), max = new AtomicInteger();
        CountDownLatch all = new CountDownLatch(20);
        for (int i = 0; i < 20; i++) {
            int id = i;
            q.tagged(null, i, 0, null).execute(() -> {
                max.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
                try { Thread.sleep(2); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                order.add(id);
                concurrent.decrementAndGet();
                all.countDown();
            });
        }
        assertTrue(all.await(10, TimeUnit.SECONDS), "상한 1 이어도 대기열이 멈추지 않는다");
        assertEquals(1, max.get());
        assertEquals(20, order.size());
    }

    @Test
    void shutdownWhileLimited_finishesPinnedRequests() throws Exception {
        q = new GenQueue(THREADS, settings(), true, false);
        for (int i = 0; i < THREADS; i++) q.adjustLimit(0.0);
        submitGen(6);
        awaitRunning(1);
        q.shutdown();
        gate.countDown();
        assertTrue(q.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(6, finished.get(), "닫을 때 남은 고정 요청은 상한과 상관없이 끝까지 돈다");
    }
}
