// ConcurrentEmbeddedChannel: 여러 스레드가 execute 로 넣고 시험 스레드가 동시에 꺼내도 작업을 하나도 잃지 않는다.
package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConcurrentEmbeddedChannelTest {

    @Test
    void concurrentExecute_whileDraining_losesNoTask() throws Exception {
        ConcurrentEmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        int threads = 4, perThread = 50_000;
        AtomicInteger ran = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }
                for (int k = 0; k < perThread; k++) ch.eventLoop().execute(ran::incrementAndGet);
            });
            ts[i].start();
        }
        AtomicBoolean done = new AtomicBoolean();
        Thread drainer = new Thread(() -> { while (!done.get()) ch.runPendingTasks(); });
        drainer.start();
        start.countDown();
        for (Thread t : ts) t.join();
        done.set(true);
        drainer.join();
        ch.runPendingTasks();
        assertEquals(threads * perThread, ran.get());
        ch.finishAndReleaseAll();
    }

    @Test
    void tasksDoNotRunNestedInsideAnotherTask() {
        ConcurrentEmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        java.util.List<String> order = new java.util.ArrayList<>();
        ch.eventLoop().execute(() -> {
            order.add("A-start");
            ch.writeAndFlush("x");   // EmbeddedChannel 은 write·flush 뒤에 runPendingTasks 를 다시 부른다
            order.add("A-end");
        });
        ch.eventLoop().execute(() -> order.add("B"));
        ch.runPendingTasks();
        assertEquals(java.util.List.of("A-start", "A-end", "B"), order);
        ch.finishAndReleaseAll();
    }
}
