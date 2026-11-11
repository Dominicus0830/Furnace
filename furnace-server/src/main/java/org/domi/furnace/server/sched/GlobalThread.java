// 단일 스레드에서 전역 작업을 비동기 실행하며 그 스레드의 소유권을 보유하는 스케줄러
package org.domi.furnace.server.sched;

import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.sched.GlobalScheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class GlobalThread implements GlobalScheduler, ThreadOwner, AutoCloseable {

    private final String name;
    /** 단일 스레드. 지연 작업용 틱 박동(beat)과 벽시계 지연(executeAfterMillis)도 같은 스레드의 예약으로 돈다 — 별도 타이머 스레드를 두지 않는다. */
    private final ScheduledThreadPoolExecutor exec;
    private volatile Thread thread;

    public GlobalThread(String name) {
        this.name = name;
        this.exec = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(() -> {
                GLOBAL.set(true);
                r.run();
            }, name);
            this.thread = t;
            return t;
        }) {
            // 작업 하나가 끝날 때 닫지 않은 region 빌림 구간이 남았으면 경고와 함께 닫는다 — 빌린 region 이 영영 멈추거나 뒤의 무관한 작업이 빌리지 않게
            @Override
            protected void afterExecute(Runnable r, Throwable t) { org.domi.furnace.server.world.RegionBorrow.closeLeaked(); }
        };
        exec.setRemoveOnCancelPolicy(true);
        exec.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);   // 닫으면 벽시계 지연(keep-alive 등)은 버린다 — 예전 delayedExecutor 경로와 같게
        // 스레드를 미리 생성해 isOwnerThread가 즉시 신뢰 가능하도록 prime.
        try {
            exec.submit(() -> {}).get();
        } catch (Exception e) {
            throw new RuntimeException("GlobalThread prime 실패", e);
        }
        // JDK 의 CompletableFuture 지연 실행기 스레드는 처음 쓰는 쪽 호출 스택(AccessControlContext·문맥 로더)을 평생 쥔다(A14-09).
        // 서버가 먼저 만들어 두면 플러그인이 처음 쓰는 경우(구조물 재시도 등)에도 플러그인 클래스로더가 붙잡히지 않는다.
        CompletableFuture.delayedExecutor(1, TimeUnit.MILLISECONDS).execute(() -> {});
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

    /** 관측: 실행을 기다리는 작업 수(박동·벽시계 지연처럼 아직 때가 안 된 예약은 세지 않는다). */
    public int queueLength() {
        int n = 0;
        for (Runnable r : exec.getQueue()) if (!(r instanceof Delayed d) || d.getDelay(TimeUnit.NANOSECONDS) <= 0) n++;
        return n;
    }

    @Override
    public String ownerName() {
        return name;
    }

    @Override
    public CompletableFuture<Void> execute(Runnable task) {
        return run(() -> {
            task.run();
            return null;
        }, exec);
    }

    @Override
    public <T> CompletableFuture<T> submit(Supplier<T> task) { return run(task, exec); }

    /** supplyAsync 와 같은 완료(실패는 CompletionException 으로 싼다)지만, 기다리는 쪽에 GlobalWait 표시를 다는 Future 를 돌려준다. */
    private static <T> GlobalFuture<T> run(Supplier<T> task, Executor on) {
        GlobalFuture<T> f = new GlobalFuture<>();
        on.execute(() -> {
            if (f.isDone()) return;
            try {
                f.complete(task.get());
            } catch (Throwable t) {
                f.completeExceptionally(t instanceof java.util.concurrent.CompletionException ? t : new java.util.concurrent.CompletionException(t));
            }
        });
        return f;
    }

    /**
     * 전역 작업의 결과. 끝나지 않은 채 get/join 으로 기다리는 동안 그 스레드에 GlobalWait 표시를 단다 — 전역이 그 스레드의 region 을
     * 빌리려고 기다리면 서로 기다리는 것이라 전역이 곧바로 포기한다(RegionBorrow).
     */
    static final class GlobalFuture<T> extends CompletableFuture<T> {
        @Override
        public T get() throws InterruptedException, java.util.concurrent.ExecutionException {
            if (isDone()) return super.get();
            try (var m = org.domi.furnace.api.concurrent.GlobalWait.begin()) {
                return super.get();
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
            if (isDone()) return super.get(timeout, unit);
            try (var m = org.domi.furnace.api.concurrent.GlobalWait.begin()) {
                return super.get(timeout, unit);
            }
        }

        @Override
        public T join() {
            if (isDone()) return super.join();
            try (var m = org.domi.furnace.api.concurrent.GlobalWait.begin()) {
                return super.join();
            }
        }

        @Override
        public <U> CompletableFuture<U> newIncompleteFuture() { return new GlobalFuture<>(); }
    }

    // ------------------------------------------------------------------ 지연 작업: 서버 틱으로 센다(A14-04)
    // TaskScheduler 계약대로 delayTicks 는 서버 틱 단위다. 틱 하나마다 때가 된 지연 작업을 예약 순서대로 돌린다 —
    // Paper 의 runTaskLater 가 tickServer 마다 한 번 도는 heartbeat 로 세는 것과 같다. 0 과 1 은 다음 틱, n 은 n 틱 뒤.
    // 틱을 내는 쪽:
    //  - 운영 서버(driveByServerTicks): ServerCore.scheduledTick 이 서버 틱 자리마다 serverTick() 을 부른다 — 스케줄러 틱이 늘 서버 틱과 같다
    //    (rate·freeze·sprint, 그리고 "Can't keep up" 으로 건너뛴 틱은 스케줄러도 건너뛴다).
    //  - 그 밖(서버 박동이 없는 시험): 첫 지연 작업 때 거는 대체 박동이 50ms 마다 ticksPerBeat(ServerCore 가 rate/20, sprint 중 0)만큼 낸다.
    //    막혔다 풀리면 밀린 박동을 몰아 돌리지 않고 지금부터 다시 센다.
    //  - 수동 시계(헤드리스 시험 서버 furnace-test 전용): advanceManualTick.

    /** 대체 박동 하나에 낼 틱 수. 기본은 1(초당 20틱) — ServerCore 가 /tick rate·sprint 를 보고 바꾼다. */
    private volatile java.util.function.DoubleSupplier ticksPerBeat = () -> 1.0;
    private static final long BEAT_NANOS = 50_000_000L;
    /** 대체 박동 하나에 낼 수 있는 최대 틱(/tick rate 상한 10000 이면 500). 비정상 값이 박동을 붙잡지 않게. */
    private static final int MAX_TICKS_PER_BEAT = 1000;

    private final Object delayLock = new Object();
    /** 지금까지 낸 스케줄러 틱 수. delayLock 아래에서 읽고 쓴다. */
    private long tick;
    private long seq;
    private final java.util.PriorityQueue<Delayed0> delayedQueue = new java.util.PriorityQueue<>(
            (a, b) -> a.due != b.due ? Long.compare(a.due, b.due) : Long.compare(a.seq, b.seq));
    /** 전역 스레드 전용: 대체 박동마다 쌓는 틱 몫의 소수 부분과 다음 박동 마감. */
    private double credit;
    private long nextBeat;
    private volatile boolean beatArmed;
    /** 참이면 서버 틱(serverTick)만 스케줄러 틱을 낸다 — 대체 박동은 세지 않는다. */
    private volatile boolean serverDriven;

    private record Delayed0(long due, long seq, Runnable task, CompletableFuture<Void> future) {}

    /** 대체 박동 하나에 낼 틱 수를 정한다(ServerCore: sprint 중이면 0, 아니면 rate/20). */
    public void ticksPerBeat(java.util.function.DoubleSupplier s) { this.ticksPerBeat = s == null ? () -> 1.0 : s; }

    /** 운영 서버: 이제부터 스케줄러 틱은 서버 틱 박동(ServerCore.scheduledTick → serverTick)만 낸다. 박동을 시작하기 전에 부른다. */
    public void driveByServerTicks() { this.serverDriven = true; }

    public boolean serverDriven() { return serverDriven; }

    /** 관측: 지금까지 낸 스케줄러 틱 수. */
    public long schedulerTick() {
        synchronized (delayLock) { return tick; }
    }

    @Override
    public CompletableFuture<Void> executeDelayed(Runnable task, long delayTicks) {
        CompletableFuture<Void> f = new GlobalFuture<>();
        synchronized (delayLock) {
            delayedQueue.add(new Delayed0(tick + Math.max(1, delayTicks), seq++, task, f));
        }
        if (!manual && !serverDriven && !beatArmed) armBeat();
        return f;
    }

    /** 첫 지연 작업 때 대체 박동을 건다(지연 작업을 쓰지 않는 스케줄러는 박동 없이 조용하다). */
    private synchronized void armBeat() {
        if (beatArmed || exec.isShutdown()) return;
        beatArmed = true;
        nextBeat = System.nanoTime() + BEAT_NANOS;   // 첫 onBeat 전에(같은 스레드 예약의 happens-before)
        scheduleBeat();
    }

    private void scheduleBeat() {
        try {
            exec.schedule(this::onBeat, Math.max(0L, nextBeat - System.nanoTime()), TimeUnit.NANOSECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // 닫히는 중
        }
    }

    /** 대체 박동: 고정 박자(마감을 주기만큼 옮긴다). 두 박동 넘게 밀렸으면 몰아 돌리지 않고 지금부터 다시 센다. */
    private void onBeat() {
        try {
            long now = System.nanoTime();
            nextBeat = now - nextBeat > 2 * BEAT_NANOS ? now + BEAT_NANOS : nextBeat + BEAT_NANOS;
            if (manual || serverDriven) return;
            double per;
            try {
                per = ticksPerBeat.getAsDouble();
            } catch (Throwable t) {
                per = 1.0;   // 기동·종료 중 월드가 없을 때 등
            }
            if (!(per > 0)) return;   // sprint 중(0)·NaN
            credit += per;
            int n = (int) Math.min(credit, MAX_TICKS_PER_BEAT);
            credit -= (int) credit;
            for (int i = 0; i < n; i++) advanceTick();
        } finally {
            if (!manual && !serverDriven) scheduleBeat();
            else beatArmed = false;
        }
    }

    /**
     * 서버 틱 하나 — 스케줄러 틱도 하나 낸다(Paper 는 tickServer 마다 heartbeat). 운영 서버의 박동과 /tick sprint 틱이 부른다.
     * 전역 스레드 밖에서 부르면(시험) 전역 스레드로 넘긴다 — 작업은 늘 전역 스레드에서 돈다.
     */
    public void serverTick() {
        if (manual) return;
        if (isOwnerThread()) advanceTick();
        else exec.execute(this::advanceTick);
    }

    /** 틱 번호를 올리고 때가 된 지연 작업을 예약 순서대로 돌린다. 돌던 중 새로 건 작업(0·1)은 다음 틱으로 간다. */
    private long advanceTick() {
        long now;
        synchronized (delayLock) { now = ++tick; }
        while (true) {
            Delayed0 d;
            synchronized (delayLock) {
                d = delayedQueue.peek();
                if (d == null || d.due > now) break;
                delayedQueue.poll();
            }
            if (d.future.isDone()) continue;
            try {
                d.task.run();
                d.future.complete(null);
            } catch (Throwable t) {
                d.future.completeExceptionally(t instanceof java.util.concurrent.CompletionException ? t : new java.util.concurrent.CompletionException(t));
            } finally {
                org.domi.furnace.server.world.RegionBorrow.closeLeaked();   // 작업 하나마다(따로 큐 작업이던 때와 같게)
            }
        }
        return now;
    }

    /**
     * 서버 내부용 벽시계 지연: 틱 속도와 무관하게 millis 뒤 전역 스레드에서 돈다(바닐라 keep-alive 처럼 실제 시간으로 재는 것).
     * 수동 시계에서는 50ms 를 한 틱으로 바꿔 센다(헤드리스 시험이 결정적이게).
     */
    public CompletableFuture<Void> executeAfterMillis(Runnable task, long millis) { return executeAfterNanos(task, TimeUnit.MILLISECONDS.toNanos(millis)); }

    /** {@link #executeAfterMillis} 의 나노초판. */
    public CompletableFuture<Void> executeAfterNanos(Runnable task, long nanos) {
        if (manual) return executeDelayed(task, Math.max(1, (nanos + BEAT_NANOS - 1) / BEAT_NANOS));
        GlobalFuture<Void> f = new GlobalFuture<>();
        Runnable r = () -> {
            if (f.isDone()) return;
            try {
                task.run();
                f.complete(null);
            } catch (Throwable t) {
                f.completeExceptionally(t instanceof java.util.concurrent.CompletionException ? t : new java.util.concurrent.CompletionException(t));
            }
        };
        exec.schedule(r, Math.max(0, nanos), TimeUnit.NANOSECONDS);
        return f;
    }

    /** 전역 스케줄러가 GlobalThread 면 벽시계 지연, 아니면(감싼 스케줄러 등) 50ms = 1틱으로 바꿔 executeDelayed. */
    public static CompletableFuture<Void> executeAfterMillis(GlobalScheduler s, Runnable task, long millis) {
        if (s instanceof GlobalThread g) return g.executeAfterMillis(task, millis);
        return s.executeDelayed(task, Math.max(1, (millis + 49) / 50));
    }

    // ------------------------------------------------------------------ 수동 시계(헤드리스 시험 서버 furnace-test 전용)

    private volatile boolean manual;

    /** 지연 작업을 advanceManualTick 이 센 틱으로 돌린다(박동은 멈춘다). 작업을 넣기 전에 한 번 부른다. */
    public void useManualClock() { this.manual = true; }

    public boolean manualClock() { return manual; }

    /** 전역 스레드에서: 시험 틱 하나 — 틱 번호를 올리고 때가 된 지연 작업을 돌린다. 지금 틱 번호를 돌려준다. */
    public long advanceManualTick() {
        if (!isOwnerThread()) throw new IllegalStateException("전역 스레드에서 불러야 한다");
        return advanceTick();
    }

    @Override
    public void close() {
        exec.shutdown();
    }
}
