// Bukkit BukkitScheduler 의 의미를 Furnace 스케줄러 위에 옮긴 것(CraftScheduler 를 따른다).
//
//  - "동기(sync)" 작업은 전역 스레드에서 돈다(주 스레드 대역). 옛 플러그인은 정적 필드·비스레드 안전 컬렉션을 주 스레드 하나가 쓴다고 믿으므로,
//    region 스레드에 나누지 않고 전역 하나에 줄 세운다. 동기 작업 안에서는 PrimaryThread 가 표시된다.
//    블록·엔티티처럼 region 이 가진 상태를 만지는 일은 H3 의 어댑터가 소유 region 으로 넘긴다 — 여기서는 스레드를 고른다.
//  - CraftScheduler 처럼 자기 대기열(nextRun, 번호 순)과 자기 틱 수를 갖고, 전역에 건 박동(heartbeat) 하나가 틱마다 비운다.
//    그래서 모든 옛 작업이 한 박자를 공유하고, 같은 틱 안의 순서가 Bukkit 과 같으며(반복 작업은 처음 만든 순서를 지킨다),
//    작업 안에서 runTask 를 하면 같은 틱에 이어서 돈다. 반복 작업의 다음 회차는 이번 비움이 끝난 뒤 들어간다.
//    운영 서버에서 박동의 간격은 서버의 전역 지연(벽시계 50ms, 느리면 밀림)을 따르고, 시험 서버에서는 tick() 한 번이 한 박동이다.
//  - 비동기 작업은 박동에서 "도착"한 뒤 asyncExecutor(가상 스레드)로 넘어간다(Bukkit 도 틱 대기열이 먼저 돌고 풀로 넘긴다).
//  - 인자 규칙은 CraftScheduler 와 같다: 지연 < 0 은 0, 주기 0 은 1, 주기 < -1 은 -1(반복 없음). 지연 0 과 1 은 둘 다 다음 틱.
//  - 작업이 예외를 던져도 반복은 이어지고(로그 한 줄), 플러그인이 꺼지면 그 플러그인의 작업은 모두 취소된다.
package org.domi.furnace.compat.sched;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.profiler.PluginProfiler;
import org.domi.furnace.api.profiler.Timing;
import org.domi.furnace.api.sched.ScheduledTask;
import org.domi.furnace.compat.thread.PrimaryThread;

public final class LegacyScheduler {

    /** CraftTask.NO_REPEATING. */
    private static final long NO_REPEATING = -1L;

    private final Furnace server;
    private final AtomicInteger ids = new AtomicInteger();
    private final Map<Integer, Task> tasks = new ConcurrentHashMap<>();
    private final PluginTaskBook<Task> book = new PluginTaskBook<>(Task::cancel);
    /** 대기열: (다음 실행 틱, 번호) 순. queue 의 모니터로 보호한다. */
    private final PriorityQueue<Task> queue = new PriorityQueue<>(
            Comparator.<Task>comparingLong(t -> t.nextRun).thenComparingInt(t -> t.id));
    /** 마지막으로 돈 박동의 틱(처음은 0). 새 작업의 nextRun = tick + 지연 이라 지연 0·1 은 다음 박동에 돈다. */
    private volatile long tick;
    private final ScheduledTask heartbeat;

    public LegacyScheduler(Furnace server) {
        this.server = server;
        this.heartbeat = server.scheduler().global().runAtFixedRate(this::heartbeat, 1, 1);
    }

    /** 박동을 멈추고 남은 작업을 모두 취소한다(서버 쪽 소유자가 닫을 때). */
    public void close() {
        heartbeat.cancel();
        List<Task> all;
        synchronized (queue) {
            all = new ArrayList<>(tasks.values());
            queue.clear();
        }
        for (Task t : all) t.cancel();
    }

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
        SyncFuture<T> future = new SyncFuture<>();
        future.task = (Task) schedule(plugin, () -> {
            try {
                future.complete(call.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }, true, 0, NO_REPEATING, () -> future.completeExceptionally(new CancellationException("task cancelled")));
        return future;
    }

    /** CraftFuture 처럼: 아직 안 돈 callable 은 cancel 하면 돌지 않는다. 이미 돌기 시작했으면 false. */
    private static final class SyncFuture<T> extends CompletableFuture<T> {
        volatile Task task;

        @Override
        public boolean cancel(boolean mayInterrupt) {
            Task t = task;
            if (isDone()) return false;
            if (t != null && !t.cancelUnclaimed()) return false;
            return super.cancel(mayInterrupt);
        }

        // 다른 스레드(region 스레드의 옛 리스너 등)가 막고 기다리는 동안 GlobalWait 표시 — 전역이 그 region 을 빌리려 하면 서로 기다리지 않고 곧바로 포기한다
        @Override
        public T get() throws InterruptedException, java.util.concurrent.ExecutionException {
            if (isDone()) return super.get();
            try (var m = org.domi.furnace.api.concurrent.GlobalWait.begin()) {
                return super.get();
            }
        }

        @Override
        public T get(long timeout, java.util.concurrent.TimeUnit unit)
                throws InterruptedException, java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
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

    /** 취소되지 않고 목록에 있는가(Bukkit 처럼 도는 중인 한 번 작업도 끝나기 전까지 true, 반복 작업은 취소 전까지 true). */
    public boolean isQueued(int taskId) {
        Task t = tasks.get(taskId);
        return t != null && !t.cancelled;
    }

    /** 대기 중이거나 반복 중인 작업(번호순). */
    public List<LegacyTask> pendingTasks() {
        List<LegacyTask> out = new ArrayList<>();
        for (Task t : tasks.values()) if (isQueued(t.id)) out.add(t);
        out.sort(Comparator.comparingInt(LegacyTask::taskId));
        return out;
    }

    /** 지금 비동기로 도는 작업과 그 스레드(회차가 겹치면 회차마다 하나). */
    public List<Worker> activeWorkers() {
        List<Worker> out = new ArrayList<>();
        for (Task t : tasks.values()) {
            if (t.sync) continue;
            for (Thread th : t.asyncThreads) out.add(new Worker(t.id, t.plugin, th));
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
        synchronized (queue) {
            t.nextRun = tick + delay;
            queue.add(t);
        }
        return t;
    }

    /** CraftScheduler.mainThreadHeartbeat: 이번 틱에 차례가 된 작업을 모두 비운다(그 사이 새로 들어온 지연 0 작업도 같은 틱에). */
    private void heartbeat() {
        long now = ++tick;
        List<Task> again = new ArrayList<>();
        // 한 박동의 동기 작업 전체를 한 표시 구간으로 — 작업이 빌린 region 은 이번 비움이 끝날 때 한꺼번에 돌려준다(작업마다 넘겨주고 받지 않는다)
        try (PrimaryThread.Scope ignored = PrimaryThread.enter()) {
            drain(now, again);
        }
        if (!again.isEmpty()) {
            synchronized (queue) {
                for (Task t : again) if (!t.cancelled) queue.add(t);
            }
        }
    }

    private void drain(long now, List<Task> again) {
        while (true) {
            Task t;
            synchronized (queue) {
                t = queue.peek();
                if (t == null || t.nextRun > now) break;
                queue.poll();
            }
            if (t.cancelled) continue;
            t.arrive(now);
            if (t.period > 0 && !t.cancelled) {
                t.nextRun = now + t.period;
                again.add(t);
            }
        }
    }

    private final class Task implements LegacyTask {
        final int id;
        final FurnacePlugin plugin;
        final Runnable body;
        final boolean sync;
        final long period;
        final Runnable onCancel;
        final Timing timing;
        final Set<Thread> asyncThreads = ConcurrentHashMap.newKeySet();
        /** 한 번 작업의 몸통을 "돌린다/취소한다" 중 먼저 잡은 쪽이 이긴다(취소와 도착이 엇갈려도 future 가 반드시 끝난다). */
        final AtomicBoolean claimed = new AtomicBoolean();
        volatile long nextRun;
        volatile boolean cancelled;

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

        /** 박동(전역 스레드)에서 차례가 됐다. */
        void arrive(long now) {
            if (sync) {
                execute();
                if (period <= 0) retire();
            } else {
                try {
                    server.asyncExecutor().execute(this::executeAsync);
                } catch (RejectedExecutionException e) {
                    cancel();
                }
            }
        }

        private void executeAsync() {
            Thread me = Thread.currentThread();
            asyncThreads.add(me);
            try {
                execute();
            } finally {
                asyncThreads.remove(me);
                if (period <= 0) retire();
            }
        }

        private void execute() {
            if (cancelled) return;
            if (!plugin.isEnabled()) {
                cancel();
                return;
            }
            if (period <= 0 && !claimed.compareAndSet(false, true)) return;   // 그 사이 취소가 먼저 잡았다
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

        final AtomicInteger running = new AtomicInteger();

        /** 한 번 작업이 끝났다 — 목록에서 뺀다. */
        private void retire() {
            tasks.remove(id);
            book.remove(plugin, this);
        }

        /** future.cancel 용: 몸통이 아직 시작 전이면 취소하고 true(onCancel 은 부르지 않는다 — future 가 직접 끝난다). */
        boolean cancelUnclaimed() {
            if (!claimed.compareAndSet(false, true)) return false;
            cancelled = true;
            tasks.remove(id);
            book.remove(plugin, this);
            return true;
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
            if (onCancel != null && claimed.compareAndSet(false, true)) onCancel.run();
        }
    }
}
