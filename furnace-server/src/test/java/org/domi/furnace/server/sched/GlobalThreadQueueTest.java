package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** /furnace threads 가 보여 주는 전역 스레드 대기 작업 수. */
class GlobalThreadQueueTest {

    @Test
    void queueLengthCountsWaitingTasksNotTheRunningOne() throws Exception {
        try (GlobalThread g = new GlobalThread("furnace-global-test")) {
            CountDownLatch running = new CountDownLatch(1), release = new CountDownLatch(1);
            g.execute(() -> {
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(running.await(5, TimeUnit.SECONDS));
            assertEquals(0, g.queueLength());
            for (int i = 0; i < 3; i++) g.execute(() -> {});
            assertEquals(3, g.queueLength());
            release.countDown();
            g.submit(() -> 1).get(5, TimeUnit.SECONDS);   // FIFO: 앞선 작업이 모두 끝난 뒤
            assertEquals(0, g.queueLength());
        }
    }

    @Test
    void tickTimeRingIsThreadSafeUnderConcurrentWriters() throws Exception {
        TickTimeRing ring = new TickTimeRing(64);
        Thread[] ts = new Thread[4];
        for (int i = 0; i < ts.length; i++) {
            final int base = i;
            ts[i] = new Thread(() -> {
                for (int k = 1; k <= 10_000; k++) ring.record(base * 1000L + k);
            });
            ts[i].start();
        }
        for (Thread t : ts) t.join();
        var s = ring.stats();
        assertEquals(64, s.samples());
        assertEquals(3L * 1000 + 10_000, s.maxEverNanos());
        assertTrue(s.p50Nanos() <= s.p95Nanos() && s.p95Nanos() <= s.maxRecentNanos());
    }
}
