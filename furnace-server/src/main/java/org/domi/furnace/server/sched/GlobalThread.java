// 단일 스레드에서 전역 작업을 비동기 실행하며 그 스레드의 소유권을 보유하는 스케줄러
package org.domi.furnace.server.sched;

import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.sched.GlobalScheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class GlobalThread implements GlobalScheduler, ThreadOwner, AutoCloseable {

    private final String name;
    private final ThreadPoolExecutor exec;
    private volatile Thread thread;

    public GlobalThread(String name) {
        this.name = name;
        // newSingleThreadExecutor 와 같은 단일 스레드·무한 큐 — 대기 작업 수를 관측하려고 큐를 직접 쥔다(/furnace threads)
        this.exec = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> {
            Thread t = new Thread(r, name);
            this.thread = t;
            return t;
        });
        // 스레드를 미리 생성해 isOwnerThread가 즉시 신뢰 가능하도록 prime.
        try {
            exec.submit(() -> {}).get();
        } catch (Exception e) {
            throw new RuntimeException("GlobalThread prime 실패", e);
        }
    }

    @Override
    public boolean isOwnerThread() {
        return Thread.currentThread() == thread;
    }

    /** 관측: 실행을 기다리는 작업 수(지연 작업은 때가 돼 큐에 들어온 뒤부터 센다). */
    public int queueLength() { return exec.getQueue().size(); }

    @Override
    public String ownerName() {
        return name;
    }

    @Override
    public CompletableFuture<Void> execute(Runnable task) {
        return CompletableFuture.runAsync(task, exec);
    }

    @Override
    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, exec);
    }

    @Override
    public CompletableFuture<Void> executeDelayed(Runnable task, long delayTicks) {
        Executor delayed = CompletableFuture.delayedExecutor(
                delayTicks * 50, TimeUnit.MILLISECONDS, exec);
        return CompletableFuture.runAsync(task, delayed);
    }

    @Override
    public void close() {
        exec.shutdown();
    }
}
