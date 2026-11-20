// 청크 생성+직렬화를 IO 스레드 밖에서 병렬 수행하는 전용 고정 스레드풀
package org.domi.furnace.server.net;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ChunkWorkerPool {

    private final ThreadPoolExecutor executor;

    public ChunkWorkerPool(int threads) {
        AtomicInteger n = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "chunk-worker-" + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        // Executors.newFixedThreadPool 과 같은 구성(무제한 FIFO 큐 하나) — 큐 깊이를 읽으려 구체 타입으로 둔다.
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), factory);
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    /** 아직 시작 못 한 일 수(진단용 — chunktrace 가 켜졌을 때만 부른다). */
    public int queueDepth() { return executor.getQueue().size(); }

    public void shutdown() {
        executor.shutdownNow();
    }
}
