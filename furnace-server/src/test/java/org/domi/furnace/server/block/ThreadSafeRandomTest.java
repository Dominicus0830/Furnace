// 스레드 안전 난수(O06 C3)는 한 스레드에서는 LegacyRandomSource 와 같은 수열이고, 여러 스레드가 써도 단계를 잃거나 겹치지 않는다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ThreadSafeRandomTest {

    @Test
    void threadSafeSourceMatchesLegacySequenceOnOneThread() {
        var legacy = new LegacyRandomSource(42L);
        var safe = new ThreadSafeLegacyRandomSource(42L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(legacy.nextInt(), safe.nextInt());
            assertEquals(legacy.nextInt(20), safe.nextInt(20));
            assertEquals(legacy.nextLong(), safe.nextLong());
            assertEquals(legacy.nextFloat(), safe.nextFloat());
            assertEquals(legacy.nextDouble(), safe.nextDouble());
            assertEquals(legacy.nextBoolean(), safe.nextBoolean());
            assertEquals(legacy.nextGaussian(), safe.nextGaussian());
        }
    }

    @Test
    void threadSafeSourceDoesNotLoseOrDuplicateStepsAcrossThreads() throws Exception {
        // 스레드 안전이면 8 스레드가 합쳐 N 번 뽑은 것 = 한 스레드가 N 번 뽑은 것(순서만 다름) — 겹침·누락 없이 같은 집합.
        int threads = 8, per = 20_000;
        var shared = new ThreadSafeLegacyRandomSource(7L);
        var single = new ThreadSafeLegacyRandomSource(7L);
        Set<Integer> expected = new HashSet<>();
        for (int i = 0; i < threads * per; i++) expected.add(single.nextInt());
        var got = java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        var go = new CountDownLatch(1);
        var failure = new AtomicLong();
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            ts[t] = new Thread(() -> {
                try { go.await(); } catch (InterruptedException e) { failure.incrementAndGet(); }
                for (int i = 0; i < per; i++) got.add(shared.nextInt());
            });
            ts[t].start();
        }
        go.countDown();
        for (Thread t : ts) t.join();
        assertEquals(0, failure.get());
        assertEquals(expected, got);
    }
}
