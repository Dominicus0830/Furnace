// ChunkWorkerPool 큐 깊이 진단값(T-226 계측) 시험: 스레드가 다 막혔을 때 대기 일 수만큼 늘고, 풀려 비워지면 0 으로 돌아온다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ChunkWorkerPoolQueueDepthTest {
    @Test
    void queueDepthCountsWaitingJobs() throws Exception {
        ChunkWorkerPool pool = new ChunkWorkerPool(1);
        try {
            CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
            var first = pool.submit(() -> {
                started.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return 1;
            });
            assertTrue(started.await(10, TimeUnit.SECONDS));
            var second = pool.submit(() -> 2);
            var third = pool.submit(() -> 3);
            assertEquals(2, pool.queueDepth());
            release.countDown();
            assertEquals(1, first.get(10, TimeUnit.SECONDS));
            assertEquals(2, second.get(10, TimeUnit.SECONDS));
            assertEquals(3, third.get(10, TimeUnit.SECONDS));
            assertEquals(0, pool.queueDepth());
        } finally {
            pool.shutdown();
        }
    }
}
