// 단일 스레드에서 20 TPS 틱 루프를 돌며 자기 region의 작업 큐를 매 틱 드레인하는 owner
package org.domi.furnace.server.sched;

import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.sched.TaskScheduler;

import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.concurrent.locks.LockSupport;

public final class RegionThread implements TaskScheduler, ThreadOwner, AutoCloseable {

    private final String name;
    private final long tickNanos;
    private final Runnable perTick;
    private final Thread thread;

    private final ConcurrentLinkedQueue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    // delayed는 region 스레드에서만 접근(단일 스레드 소유) — 동기화 불필요.
    private final PriorityQueue<Delayed> delayed =
            new PriorityQueue<>((a, b) -> Long.compare(a.dueTick, b.dueTick));

    private final AtomicLong tickCount = new AtomicLong();
    private volatile double measuredTps;
    private volatile boolean running = true;

    public RegionThread(String name, long tickNanos, Runnable perTick) {
        this.name = name;
        this.tickNanos = tickNanos;
        this.perTick = perTick;
        // 모든 필드를 start() 이전에 할당한다. Thread.start()는 happens-before라
        // loop()가 위 필드를 안전하게 본다. start() 뒤로 필드 할당을 옮기면 가시성 버그가 생긴다.
        this.thread = new Thread(this::loop, name);
        this.thread.start();
    }

    @Override
    public boolean isOwnerThread() {
        return Thread.currentThread() == thread;
    }

    @Override
    public String ownerName() {
        return name;
    }

    public long tickCount() {
        return tickCount.get();
    }

    public double measuredTps() {
        return measuredTps;
    }

    @Override
    public CompletableFuture<Void> execute(Runnable task) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        inbox.add(() -> {
            try { task.run(); f.complete(null); }
            catch (Throwable t) { f.completeExceptionally(t); }
        });
        return f;
    }

    @Override
    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        inbox.add(() -> {
            try { f.complete(task.get()); }
            catch (Throwable t) { f.completeExceptionally(t); }
        });
        return f;
    }

    @Override
    public CompletableFuture<Void> executeDelayed(Runnable task, long delayTicks) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        long due = tickCount.get() + Math.max(0, delayTicks);
        inbox.add(() -> delayed.add(new Delayed(due, task, f)));
        return f;
    }

    private void loop() {
        TickTimer timer = new TickTimer(tickNanos);
        timer.start(System.nanoTime());
        long lastSecond = System.nanoTime();
        int ticksThisSecond = 0;

        while (running) {
            long tick = tickCount.get();
            drainInbox();
            runDueDelayed(tick);
            perTick.run();
            tickCount.incrementAndGet();

            ticksThisSecond++;
            long now = System.nanoTime();
            if (now - lastSecond >= 1_000_000_000L) {
                measuredTps = ticksThisSecond * 1e9 / (now - lastSecond);
                ticksThisSecond = 0;
                lastSecond = now;
            }

            long sleep = timer.remaining(System.nanoTime());
            if (sleep > 0) {
                LockSupport.parkNanos(sleep);
            }
            timer.advance(System.nanoTime());
        }
    }

    private void drainInbox() {
        Runnable r;
        while ((r = inbox.poll()) != null) {
            r.run();
        }
    }

    private void runDueDelayed(long tick) {
        while (!delayed.isEmpty() && delayed.peek().dueTick <= tick) {
            Delayed d = delayed.poll();
            try { d.task.run(); d.future.complete(null); }
            catch (Throwable t) { d.future.completeExceptionally(t); }
        }
    }

    @Override
    public void close() {
        running = false;
        thread.interrupt();
        try { thread.join(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private record Delayed(long dueTick, Runnable task, CompletableFuture<Void> future) {}
}
