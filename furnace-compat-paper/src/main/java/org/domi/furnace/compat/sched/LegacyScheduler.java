// Bukkit BukkitScheduler 의 의미를 Furnace 스케줄러 위에 옮긴 것.
//
//  - "동기(sync)" 작업은 전역 스레드에서 돈다(주 스레드 대역). 옛 플러그인은 정적 필드·비스레드 안전 컬렉션을 주 스레드 하나가 쓴다고 믿으므로,
//    region 스레드에 나누지 않고 전역 하나에 줄 세운다(같은 틱 안에서는 예약한 차례대로). 동기 작업 안에서는 PrimaryThread 가 표시된다.
//    블록·엔티티처럼 region 이 가진 상태를 만지는 일은 H3 의 어댑터가 소유 region 으로 넘긴다 — 여기서는 스레드를 고른다.
//  - 비동기 작업은 틱에 맞춰 전역에서 "도착"을 알린 뒤 asyncExecutor(가상 스레드)로 넘어간다(Bukkit 도 틱 대기열이 먼저 돌고 풀로 넘긴다).
//  - 인자 규칙은 CraftScheduler 와 같다: 지연 < 0 은 0, 주기 0 은 1, 주기 < -1 은 -1(반복 없음). 지연 0 과 1 은 둘 다 다음 틱.
//  - 작업이 예외를 던져도 반복은 이어지고(로그 한 줄), 플러그인이 꺼지면 그 플러그인의 작업은 모두 취소된다.
package org.domi.furnace.compat.sched;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.profiler.PluginProfiler;
import org.domi.furnace.api.profiler.Timing;
import org.domi.furnace.compat.thread.PrimaryThread;

public final class LegacyScheduler {

    /** CraftTask.NO_REPEATING. */
    private static final long NO_REPEATING = -1L;

    private final Furnace server;
    private final AtomicInteger ids = new AtomicInteger();
    private final Map<Integer, Task> tasks = new ConcurrentHashMap<>();
    private final PluginTaskBook<Task> book = new PluginTaskBook<>(Task::cancel);

    public LegacyScheduler(Furnace server) { this.server = server; }

    // ---- 예약

    /** 다음 틱에 동기 실행. */
    public LegacyTask runTask(FurnacePlugin plugin, Runnable task) { return schedule(plugin, task, true, 0, NO_REPEATING, null); }

    public LegacyTask runTaskLater(FurnacePlugin plugin, Runnable task, long delayTicks) {
        return schedule(plugin, task, true, delayTicks, NO_REPEATING, null);
    }

    public LegacyTask runTaskTimer(FurnacePlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return schedule(plugin, task, true, delayTicks, periodTicks, null);
    }

    public LegacyTask runTaskAsynchronously(FurnacePlugin plugin, Runnable task) { return schedule(plugin, task, false, 0, NO_REPEATING, null); }

    public LegacyTask runTaskLaterAsynchronously(FurnacePlugin plugin, Runnable task, long delayTicks) {
        return schedule(plugin, task, false, delayTicks, NO_REPEATING, null);
    }

    public LegacyTask runTaskTimerAsynchronously(FurnacePlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return schedule(plugin, task, false, delayTicks, periodTicks, null);
    }

    /** 번호만 돌려주는 옛 이름들(Bukkit 은 실패 때 -1 을 돌려주지만 검증 실패는 예외다). */
    public int scheduleSyncDelayedTask(FurnacePlugin plugin, Runnable task, long delayTicks) { return runTaskLater(plugin, task, delayTicks).taskId(); }

    public int scheduleSyncDelayedTask(FurnacePlugin plugin, Runnable task) { return runTask(plugin, task).taskId(); }

    public int scheduleSyncRepeatingTask(FurnacePlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return runTaskTimer(plugin, task, delayTicks, periodTicks).taskId();
    }

    public int scheduleAsyncDelayedTask(FurnacePlugin plugin, Runnable task, long delayTicks) {
        return runTaskLaterAsynchronously(plugin, task, delayTicks).taskId();
    }

    public int scheduleAsyncDelayedTask(FurnacePlugin plugin, Runnable task) { return runTaskAsynchronously(plugin, task).taskId(); }

    public int scheduleAsyncRepeatingTask(FurnacePlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks).taskId();
    }

    /**
     * 다음 틱에 전역(주) 스레드에서 호출하고 결과를 future 로 받는다. 이미 주 스레드에서 get() 으로 기다리면 교착이므로(Bukkit 도 같다)
     * 주 스레드에서는 부르지 말고 바로 호출한다. 작업이 취소되면(플러그인이 꺼짐) future 는 취소된다.
     */
    public <T> Future<T> callSyncMethod(FurnacePlugin plugin, Callable<T> call) {
        if (call == null) throw new IllegalArgumentException("Task cannot be null");
        CompletableFuture<T> future = new CompletableFuture<>();
        schedule(plugin, () -> {
            try {
                future.complete(call.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }, true, 0, NO_REPEATING, () -> future.completeExceptionally(new CancellationException("task cancelled")));
        return future;
    }

    // ---- 조회·취소

    public void cancelTask(int taskId) {
        Task t = tasks.get(taskId);
        if (t != null) t.cancel();
    }

    /** 그 플러그인의 작업을 모두 취소한다(동기·비동기 모두). */
    public void cancelTasks(FurnacePlugin plugin) { book.cancelAll(plugin); }

    /** 지금 실행 중인가(반복 작업은 한 번 도는 동안만 true). */
    public boolean isCurrentlyRunning(int taskId) {
        Task t = tasks.get(taskId);
        return t != null && t.running.get() > 0;
    }

    /** 앞으로 돌 실행이 남아 있는가(한 번 작업은 돌기 시작하면 false, 반복 작업은 취소 전까지 true). */
    public boolean isQueued(int taskId) {
        Task t = tasks.get(taskId);
        return t != null && !t.cancelled && (t.period > 0 || !t.started);
    }

    /** 대기 중이거나 반복 중인 작업(번호순). */
    public List<LegacyTask> pendingTasks() {
        List<LegacyTask> out = new ArrayList<>();
        for (Task t : tasks.values()) if (isQueued(t.id)) out.add(t);
        out.sort(Comparator.comparingInt(LegacyTask::taskId));
        return out;
    }

    /** 지금 비동기로 도는 작업과 그 스레드. */
    public List<Worker> activeWorkers() {
        List<Worker> out = new ArrayList<>();
        for (Task t : tasks.values()) {
            Thread th = t.asyncThread;
            if (!t.sync && t.running.get() > 0 && th != null) out.add(new Worker(t.id, t.plugin, th));
        }
        out.sort(Comparator.comparingInt(Worker::taskId));
        return out;
    }

    /** Bukkit BukkitWorker. */
    public record Worker(int taskId, FurnacePlugin owner, Thread thread) {}

    // ---- 내부

    private LegacyTask schedule(FurnacePlugin plugin, Runnable runnable, boolean sync, long delay, long period, Runnable onCancel) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (runnable == null) throw new IllegalArgumentException("Task cannot be null");
        if (!plugin.isEnabled()) throw new PluginDisabledException("Plugin attempted to register task while disabled");
        if (delay < 0L) delay = 0;
        if (period == 0L) period = 1L;
        else if (period < NO_REPEATING) period = NO_REPEATING;
        Task t = new Task(ids.incrementAndGet(), plugin, runnable, sync, period, onCancel);
        tasks.put(t.id, t);
        book.add(plugin, t);
        t.arm(delay);
        return t;
    }

    private final class Task implements LegacyTask, Runnable {
        final int id;
        final FurnacePlugin plugin;
        final Runnable body;
        final boolean sync;
        final long period;
        final Runnable onCancel;
        final Timing timing;
        final AtomicInteger running = new AtomicInteger();
        volatile boolean cancelled;
        volatile boolean started;
        volatile Thread asyncThread;

        Task(int id, FurnacePlugin plugin, Runnable body, boolean sync, long period, Runnable onCancel) {
            this.id = id;
            this.plugin = plugin;
            this.body = body;
            this.sync = sync;
            this.period = period;
            this.onCancel = onCancel;
            String n = body.getClass().getName();
            int cut = n.indexOf('/') < 0 ? n.length() : n.indexOf('/');
            this.timing = server.profiler().timing(plugin, sync ? PluginProfiler.Kind.TASK : PluginProfiler.Kind.ASYNC,
                    n.substring(n.lastIndexOf('.', cut) + 1));
        }

        /** 전역 틱 지연으로 다음 도착을 건다. */
        void arm(long delayTicks) {
            try {
                server.scheduler().global().executeDelayed(this, delayTicks);
            } catch (RejectedExecutionException e) {
                cancel();   // 서버 종료 중
            }
        }

        /** 전역 스레드에서 도착. */
        @Override
        public void run() {
            if (cancelled) return;
            started = true;
            if (sync) {
                execute();
                finishRound();
            } else {
                try {
                    server.asyncExecutor().execute(this::executeAsync);
                } catch (RejectedExecutionException e) {
                    cancel();
                    return;
                }
                finishRound();
            }
        }

        private void finishRound() {
            if (period > 0) {
                if (!cancelled) arm(period);
            } else if (sync) {
                retire();
            }
        }

        private void executeAsync() {
            asyncThread = Thread.currentThread();
            try {
                execute();
            } finally {
                asyncThread = null;
                if (period <= 0) retire();
            }
        }

        private void execute() {
            if (cancelled) return;
            if (!plugin.isEnabled()) {
                cancel();
                return;
            }
            running.incrementAndGet();
            long t0 = timing.start();
            try (PrimaryThread.Scope ignored = sync ? PrimaryThread.enter() : null) {
                body.run();
            } catch (Throwable t) {
                plugin.logger().log(Level.WARNING, "Task #" + id + " for " + plugin.description().fullName() + " generated an exception", t);
            } finally {
                timing.stop(t0);
                running.decrementAndGet();
            }
        }

        /** 한 번 작업이 끝났다 — 목록에서 뺀다. */
        private void retire() {
            tasks.remove(id);
            book.remove(plugin, this);
        }

        @Override
        public int taskId() { return id; }

        @Override
        public FurnacePlugin owner() { return plugin; }

        @Override
        public boolean isSync() { return sync; }

        @Override
        public boolean isCancelled() { return cancelled; }

        @Override
        public void cancel() {
            if (cancelled) return;
            cancelled = true;
            tasks.remove(id);
            book.remove(plugin, this);
            if (onCancel != null && !started) onCancel.run();
        }
    }
}
