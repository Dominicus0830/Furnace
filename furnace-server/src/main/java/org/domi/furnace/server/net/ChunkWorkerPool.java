// 청크 생성+직렬화를 IO 스레드 밖에서 병렬 수행하는 전용 고정 스레드풀
package org.domi.furnace.server.net;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ChunkWorkerPool {

    private final ExecutorService executor;

    public ChunkWorkerPool(int threads) {
        AtomicInteger n = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "chunk-worker-" + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
        this.executor = Executors.newFixedThreadPool(threads, factory);
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
