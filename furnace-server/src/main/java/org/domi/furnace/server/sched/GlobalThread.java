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
            Thread t = new Thread(() -> {
                GLOBAL.set(true);
                r.run();
            }, name);
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

    private static final ThreadLocal<Boolean> GLOBAL = ThreadLocal.withInitial(() -> false);

    /** 지금 스레드가 (어느 서버든) 전역 스레드인가 — 명령이 region 에 보내는 작업을 가려낼 때 쓴다. */
    public static boolean isGlobalThread() { return GLOBAL.get(); }

    @Override
    public boolean isOwnerThread() {
        return Thread.currentThread() == thread;
    }

    /** 전역 스레드에서 region 빌림 구간(RegionBorrow)을 연다. 전역 스레드가 아니면 빈 손잡이(어느 서버의 전역이든 — 빌림은 region 의 차원이 맡는다). */
    @Override
    public RegionAccess openRegionAccess() { return org.domi.furnace.server.world.RegionBorrow.open(); }

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
        if (manual) {
            CompletableFuture<Void> f = new CompletableFuture<>();
            synchronized (manualQueue) {
                // 바닐라·Bukkit 처럼 0 과 1 은 다음 틱, n 은 n 틱 뒤
                manualQueue.add(new ManualDelayed(manualTick + Math.max(1, delayTicks), manualSeq++, task, f));
            }
            return f;
        }
        Executor delayed = CompletableFuture.delayedExecutor(
                delayTicks * 50, TimeUnit.MILLISECONDS, exec);
        return CompletableFuture.runAsync(task, delayed);
    }

    // ------------------------------------------------------------------ 수동 시계(헤드리스 시험 서버 furnace-test 전용)
    // 켜면 지연 작업은 벽시계가 아니라 advanceManualTick 이 센 틱으로 돈다. 같은 틱에 때가 된 작업은 예약 순서대로.

    private volatile boolean manual;
    private long manualTick;
    private long manualSeq;
    private final java.util.PriorityQueue<ManualDelayed> manualQueue = new java.util.PriorityQueue<>(
            (a, b) -> a.due != b.due ? Long.compare(a.due, b.due) : Long.compare(a.seq, b.seq));

    private record ManualDelayed(long due, long seq, Runnable task, CompletableFuture<Void> future) {}

    /** 지연 작업을 틱 수로 센다. 작업을 넣기 전에 한 번 부른다. */
    public void useManualClock() { this.manual = true; }

    public boolean manualClock() { return manual; }

    /** 전역 스레드에서: 시험 틱 하나 — 틱 번호를 올리고 때가 된 지연 작업을 돌린다. 지금 틱 번호를 돌려준다. */
    public long advanceManualTick() {
        if (!isOwnerThread()) throw new IllegalStateException("전역 스레드에서 불러야 한다");
        long tick;
        synchronized (manualQueue) { tick = ++manualTick; }
        while (true) {
            ManualDelayed d;
            synchronized (manualQueue) {
                d = manualQueue.peek();
                if (d == null || d.due > tick) break;
                manualQueue.poll();
            }
            try {
                d.task.run();
                d.future.complete(null);
            } catch (Throwable t) {
                d.future.completeExceptionally(t);
            }
        }
        return tick;
    }

    @Override
    public void close() {
        exec.shutdown();
    }
}
