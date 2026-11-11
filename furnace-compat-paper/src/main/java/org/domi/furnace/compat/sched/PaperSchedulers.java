// Paper(Folia) 의 스케줄러 넷 — GlobalRegionScheduler·RegionScheduler·AsyncScheduler·EntityScheduler — 를 Furnace 스케줄러에 1:1 로 얹는다.
// Folia 를 아는 플러그인이 쓰는 길이라 "소유 region 으로 넘기기"가 그대로 된다(전역은 전역 스레드, region 은 그 칸을 가진 region, 엔티티는 따라다님).
// 규칙은 Paper 와 같다: 지연·주기는 1 이상(execute·run 은 다음 틱), 비동기는 시간 단위. 작업이 예외를 던져도 반복은 이어지고(로그),
// 플러그인이 꺼지면 이 플러그인의 작업은 모두 취소된다. 한 작업이 자기 자신과 겹쳐 돌지는 않는다.
package org.domi.furnace.compat.sched;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.profiler.PluginProfiler;
import org.domi.furnace.api.profiler.Timing;
import org.domi.furnace.api.sched.ScheduledTask;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.api.world.World;

public final class PaperSchedulers implements AutoCloseable {

    private final Furnace server;
    private final PluginTaskBook<Handle> book = new PluginTaskBook<>(Handle::cancel);
    private final ScheduledExecutorService timer;
    /** 비동기 작업이 도는 곳(Paper FoliaAsyncScheduler.executors). virtual 옵션이면 서버의 가상 스레드 실행기, 아니면 아래 우리 풀. */
    private final Executor asyncPool;
    /** 우리가 만든 플랫폼 풀(virtual 이면 null — 서버 실행기는 서버가 닫는다). */
    private final ThreadPoolExecutor ownedPool;
    private final Global global = new Global();
    private final Region region = new Region();
    private final Async async = new Async();
    private final EntityTasks entity = new EntityTasks();

    public PaperSchedulers(Furnace server) {
        this.server = server;
        // Paper FoliaAsyncScheduler: 시간 재는 스레드 하나(이름·우선순위가 Paper 와 같다)
        this.timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Folia Async Scheduler Thread Timer");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY + 1);
            t.setUncaughtExceptionHandler(PaperSchedulers::logUncaught);
            return t;
        });
        if ("virtual".equalsIgnoreCase(server.compatOption(LegacyScheduler.ASYNC_THREADS_OPTION))) {
            this.ownedPool = null;
            this.asyncPool = server.asyncExecutor();
        } else {
            this.ownedPool = newPlatformPool();
            this.asyncPool = ownedPool;
        }
    }

    /**
     * Paper 1.21.1 FoliaAsyncScheduler.executors: 코어 max(4, 프로세서/2), 최대 무제한, 30초, 대기열 없음(일이 들어올 때 놀고 있는 스레드가 없으면 새 스레드),
     * 이름 "Folia Async Scheduler Thread #N"(0부터), 우선순위 NORM-1. 코어 스레드가 놀아도 안 정리하고 미리 띄우지도 않는다(Paper 와 같다).
     * Paper 는 비데몬이지만 Furnace 는 멈춘 작업이 JVM 종료를 붙잡지 않게 데몬으로 한다. Bukkit 의 BukkitScheduler 풀(LegacyScheduler)과는 별개다(Paper 도 별개).
     */
    private static ThreadPoolExecutor newPlatformPool() {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        return new ThreadPoolExecutor(Math.max(4, Runtime.getRuntime().availableProcessors() / 2), Integer.MAX_VALUE, 30L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), r -> {
                    Thread t = new Thread(r, "Folia Async Scheduler Thread #" + n.getAndIncrement());
                    t.setDaemon(true);
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    t.setUncaughtExceptionHandler(PaperSchedulers::logUncaught);
                    return t;
                });
    }

    private static void logUncaught(Thread thread, Throwable thr) {
        org.slf4j.LoggerFactory.getLogger("Furnace").error("Uncaught exception in thread: " + thread.getName(), thr);
    }

    /** 비동기 작업이 도는 스레드 풀이 플랫폼 스레드 풀인가(시험·진단용). */
    public boolean usesPlatformThreads() { return ownedPool != null; }

    public Global global() { return global; }

    public Region region() { return region; }

    public Async async() { return async; }

    public EntityTasks entity() { return entity; }

    /** 이 플러그인이 이 객체로 예약한 작업(네 스케줄러 모두)을 취소한다. */
    public void cancelTasks(FurnacePlugin plugin) { book.cancelAll(plugin); }

    /**
     * 스케줄러 하나의 cancelTasks: 그 종류의 작업만 취소한다. Paper 1.21.1 에서 AsyncScheduler.cancelTasks 는 비동기만, GlobalRegionScheduler.cancelTasks 는
     * 전역 + region(비-Folia Paper 의 region 스케줄러는 전역으로 넘어간다)만 취소하고, 엔티티 작업은 어느 쪽도 건드리지 않는다(꺼질 때는 전부 — {@link #cancelTasks(FurnacePlugin)}).
     */
    private void cancelKinds(FurnacePlugin plugin, java.util.Set<String> kinds) {
        if (plugin == null) throw new IllegalArgumentException("Plugin may not be null");
        book.cancelWhere(plugin, h -> kinds.contains(h.kind));
    }

    @Override
    public void close() {
        timer.shutdownNow();
        if (ownedPool != null) ownedPool.shutdown();   // 도는 작업은 끝까지 간다(인터럽트 없음)
    }

    // ---- 전역

    /** Paper GlobalRegionScheduler. */
    public final class Global {
        private Global() {}

        public void execute(FurnacePlugin plugin, Runnable task) { run(plugin, t -> task.run()); }

        public CompatTask run(FurnacePlugin plugin, Consumer<CompatTask> task) { return submit(plugin, task, server.scheduler().global(), 1, 0, "global"); }

        public CompatTask runDelayed(FurnacePlugin plugin, Consumer<CompatTask> task, long delayTicks) {
            requirePositive(delayTicks, "Delay ticks");
            return submit(plugin, task, server.scheduler().global(), delayTicks, 0, "global");
        }

        public CompatTask runAtFixedRate(FurnacePlugin plugin, Consumer<CompatTask> task, long initialDelayTicks, long periodTicks) {
            requirePositive(initialDelayTicks, "Initial delay ticks");
            requirePositive(periodTicks, "Period ticks");
            return submit(plugin, task, server.scheduler().global(), initialDelayTicks, periodTicks, "global");
        }

        public void cancelTasks(FurnacePlugin plugin) { cancelKinds(plugin, java.util.Set.of("global", "region")); }
    }

    // ---- region

    /** Paper RegionScheduler: 칸(청크)을 가진 region 의 스레드에서 돈다. */
    public final class Region {
        private Region() {}

        public void execute(FurnacePlugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
            run(plugin, world, chunkX, chunkZ, t -> task.run());
        }

        public void execute(FurnacePlugin plugin, Location location, Runnable task) { run(plugin, location, t -> task.run()); }

        public CompatTask run(FurnacePlugin plugin, World world, int chunkX, int chunkZ, Consumer<CompatTask> task) {
            return submit(plugin, task, at(world, chunkX, chunkZ), 1, 0, "region");
        }

        public CompatTask run(FurnacePlugin plugin, Location location, Consumer<CompatTask> task) {
            return submit(plugin, task, at(location), 1, 0, "region");
        }

        public CompatTask runDelayed(FurnacePlugin plugin, World world, int chunkX, int chunkZ, Consumer<CompatTask> task, long delayTicks) {
            requirePositive(delayTicks, "Delay ticks");
            return submit(plugin, task, at(world, chunkX, chunkZ), delayTicks, 0, "region");
        }

        public CompatTask runDelayed(FurnacePlugin plugin, Location location, Consumer<CompatTask> task, long delayTicks) {
            requirePositive(delayTicks, "Delay ticks");
            return submit(plugin, task, at(location), delayTicks, 0, "region");
        }

        public CompatTask runAtFixedRate(FurnacePlugin plugin, World world, int chunkX, int chunkZ, Consumer<CompatTask> task,
                                         long initialDelayTicks, long periodTicks) {
            requirePositive(initialDelayTicks, "Initial delay ticks");
            requirePositive(periodTicks, "Period ticks");
            return submit(plugin, task, at(world, chunkX, chunkZ), initialDelayTicks, periodTicks, "region");
        }

        public CompatTask runAtFixedRate(FurnacePlugin plugin, Location location, Consumer<CompatTask> task, long initialDelayTicks, long periodTicks) {
            requirePositive(initialDelayTicks, "Initial delay ticks");
            requirePositive(periodTicks, "Period ticks");
            return submit(plugin, task, at(location), initialDelayTicks, periodTicks, "region");
        }

        private TaskScheduler at(World world, int chunkX, int chunkZ) {
            if (world == null) throw new IllegalArgumentException("World may not be null");
            return world.scheduler(new BlockPos(chunkX << 4, 0, chunkZ << 4));
        }

        private TaskScheduler at(Location l) {
            if (l == null) throw new IllegalArgumentException("Location may not be null");
            World w = server.world(l.world());
            if (w == null) throw new IllegalArgumentException("World not loaded: " + l.world());
            return w.scheduler(l.blockPos());
        }
    }

    // ---- 엔티티

    /** Paper Entity#getScheduler(). 엔티티가 사라졌으면 예약하지 않고 null/false(retired 는 불리지 않는다 — Paper 와 같다). */
    public final class EntityTasks {
        private EntityTasks() {}

        /**
         * Paper FoliaEntityScheduler.execute: 지연 1 미만은 1 로 보고(EntityScheduler.schedule), 등록 때 플러그인이 켜졌는지는 보지 않는다 — 꺼진 플러그인도
         * 엔티티가 살아 있으면 true 를 돌려주고 작업은 실행 때 건너뛴다(retired 도 안 불린다). 사라진 엔티티는 false.
         */
        public boolean execute(FurnacePlugin plugin, Entity entity, Runnable task, Runnable retired, long delayTicks) {
            if (plugin == null) throw new IllegalArgumentException("Plugin may not be null");
            if (task == null) throw new IllegalArgumentException("Task may not be null");
            long delay = Math.max(1L, delayTicks);
            if (!plugin.isEnabled()) return entity.entityScheduler().runDelayed(() -> {}, () -> {}, delay) != null;
            return runDelayed(plugin, entity, t -> task.run(), retired, delay) != null;
        }

        public CompatTask run(FurnacePlugin plugin, Entity entity, Consumer<CompatTask> task, Runnable retired) {
            return runDelayed(plugin, entity, task, retired, 1);
        }

        public CompatTask runDelayed(FurnacePlugin plugin, Entity entity, Consumer<CompatTask> task, Runnable retired, long delayTicks) {
            requirePositive(delayTicks, "Delay ticks");
            Handle h = newHandle(plugin, task, false, "entity");
            ScheduledTask st = entity.entityScheduler().runDelayed(h::run, retireHook(h, retired), delayTicks);
            return attach(h, st);
        }

        public CompatTask runAtFixedRate(FurnacePlugin plugin, Entity entity, Consumer<CompatTask> task, Runnable retired,
                                         long initialDelayTicks, long periodTicks) {
            requirePositive(initialDelayTicks, "Initial delay ticks");
            requirePositive(periodTicks, "Period ticks");
            Handle h = newHandle(plugin, task, true, "entity");
            ScheduledTask st = entity.entityScheduler().runAtFixedRate(h::run, retireHook(h, retired), initialDelayTicks, periodTicks);
            return attach(h, st);
        }

        private Runnable retireHook(Handle h, Runnable retired) {
            return () -> {
                h.cancel();
                if (retired != null) {
                    try {
                        retired.run();
                    } catch (Throwable t) {
                        h.plugin.logger().log(Level.WARNING, "retired 콜백 예외", t);
                    }
                }
            };
        }

        private CompatTask attach(Handle h, ScheduledTask st) {
            if (st == null) {   // 이미 사라진 엔티티
                h.dropSilently();
                return null;
            }
            h.stop = st::cancel;
            return h;
        }
    }

    // ---- 비동기

    /** Paper AsyncScheduler: 벽시계 시간 단위, 작업은 Paper 모양의 플랫폼 풀(plugins.compat.scheduler.async-threads=virtual 이면 서버의 가상 스레드)에서 돈다. */
    public final class Async {
        private Async() {}

        public CompatTask runNow(FurnacePlugin plugin, Consumer<CompatTask> task) {
            Handle h = newHandle(plugin, task, false, "async");
            execute(h);
            return h;
        }

        public CompatTask runDelayed(FurnacePlugin plugin, Consumer<CompatTask> task, long delay, TimeUnit unit) {
            if (delay < 0) throw new IllegalArgumentException("Delay may not be < 0");
            Handle h = newHandle(plugin, task, false, "async");
            AtomicReference<ScheduledFuture<?>> f = new AtomicReference<>();
            h.stop = () -> {
                ScheduledFuture<?> sf = f.get();
                if (sf != null) sf.cancel(false);
            };
            f.set(timer.schedule(() -> execute(h), delay, unit));
            return h;
        }

        /**
         * Paper AsyncScheduledTask 처럼: 한 회차가 끝난 뒤 다음을 max(지금, 목표 시각 + 주기) 로 건다 — 회차가 느리면 한 번 바로 이어서 돌고
         * 그 시각부터 다시 센다(밀린 회차를 몰아 돌지는 않는다).
         * 회차는 서로 겹치지 않는다(앞 회차가 끝나야 다음이 걸린다).
         */
        public CompatTask runAtFixedRate(FurnacePlugin plugin, Consumer<CompatTask> task, long initialDelay, long period, TimeUnit unit) {
            if (initialDelay < 0) throw new IllegalArgumentException("Initial delay may not be < 0");
            if (period <= 0) throw new IllegalArgumentException("Period may not be <= 0");
            Handle h = newHandle(plugin, task, true, "async");
            long periodNs = unit.toNanos(period);
            AtomicReference<ScheduledFuture<?>> current = new AtomicReference<>();
            h.stop = () -> {
                ScheduledFuture<?> f = current.get();
                if (f != null) f.cancel(false);
            };
            long firstTarget = System.nanoTime() + unit.toNanos(initialDelay);
            arm(h, current, firstTarget, periodNs);
            return h;
        }

        private void arm(Handle h, AtomicReference<ScheduledFuture<?>> current, long target, long periodNs) {
            if (h.isCancelled()) return;
            long delay = Math.max(0, target - System.nanoTime());
            try {
                current.set(timer.schedule(() -> {
                    Executor ex = asyncPool;
                    try {
                        ex.execute(() -> {
                            h.run();
                            arm(h, current, Math.max(System.nanoTime(), target + periodNs), periodNs);
                        });
                    } catch (RejectedExecutionException e) {
                        h.cancel();
                    }
                }, delay, TimeUnit.NANOSECONDS));
            } catch (RejectedExecutionException e) {
                h.cancel();   // 타이머가 닫혔다
            }
        }

        public void cancelTasks(FurnacePlugin plugin) { cancelKinds(plugin, java.util.Set.of("async")); }

        private void execute(Handle h) {
            Executor ex = asyncPool;
            try {
                ex.execute(h::run);
            } catch (RejectedExecutionException e) {
                h.cancel();
            }
        }
    }

    // ---- 공통

    private CompatTask submit(FurnacePlugin plugin, Consumer<CompatTask> task, TaskScheduler on, long delayTicks, long periodTicks, String kind) {
        Handle h = newHandle(plugin, task, periodTicks > 0, kind);
        ScheduledTask st;
        try {
            st = periodTicks > 0 ? on.runAtFixedRate(h::run, delayTicks, periodTicks) : on.runLater(h::run, delayTicks);
        } catch (RejectedExecutionException e) {
            h.cancel();
            return h;
        }
        h.stop = st::cancel;
        return h;
    }

    private Handle newHandle(FurnacePlugin plugin, Consumer<CompatTask> task, boolean repeating, String kind) {
        if (plugin == null) throw new IllegalArgumentException("Plugin may not be null");
        if (task == null) throw new IllegalArgumentException("Task may not be null");
        if (!plugin.isEnabled()) throw new PluginDisabledException("Plugin attempted to register task while disabled");
        Handle h = new Handle(plugin, task, repeating, kind,
                server.profiler().timing(plugin, "async".equals(kind) ? PluginProfiler.Kind.ASYNC : PluginProfiler.Kind.TASK, kind + " task"));
        book.add(plugin, h);
        return h;
    }

    private static void requirePositive(long v, String what) {
        if (v <= 0) throw new IllegalArgumentException(what + " may not be <= 0");
    }

    private final class Handle implements CompatTask {
        final FurnacePlugin plugin;
        private final Consumer<CompatTask> body;
        private final boolean repeating;
        private final Timing timing;
        private final AtomicReference<ExecutionState> state = new AtomicReference<>(ExecutionState.IDLE);
        volatile Runnable stop = () -> {};

        /** 로그 문장용(Paper: "Global task for …"). */
        private final String kind;

        Handle(FurnacePlugin plugin, Consumer<CompatTask> body, boolean repeating, String kind, Timing timing) {
            this.kind = kind;
            this.plugin = plugin;
            this.body = body;
            this.repeating = repeating;
            this.timing = timing;
        }

        /** 도착 — 스케줄러가 부른다. */
        void run() {
            if (!plugin.isEnabled()) {
                cancel();
                return;
            }
            if (!state.compareAndSet(ExecutionState.IDLE, ExecutionState.RUNNING)) return;   // 취소됐거나 겹침
            long t0 = timing.start();
            try {
                body.accept(this);
            } catch (Throwable t) {
                plugin.logger().log(Level.WARNING, Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + " task for "
                        + plugin.description().fullName() + " generated an exception", t);
            } finally {
                timing.stop(t0);
                ExecutionState after;
                while (true) {
                    ExecutionState s = state.get();
                    after = s == ExecutionState.CANCELLED_RUNNING ? ExecutionState.CANCELLED
                            : repeating ? ExecutionState.IDLE : ExecutionState.FINISHED;
                    if (state.compareAndSet(s, after)) break;
                }
                if (after != ExecutionState.IDLE) book.remove(plugin, this);
            }
        }

        @Override
        public FurnacePlugin owner() { return plugin; }

        @Override
        public boolean isRepeating() { return repeating; }

        @Override
        public boolean isCancelled() {
            ExecutionState s = state.get();
            return s == ExecutionState.CANCELLED || s == ExecutionState.CANCELLED_RUNNING;
        }

        @Override
        public ExecutionState state() { return state.get(); }

        @Override
        public CancelledState cancel() {
            while (true) {
                ExecutionState s = state.get();
                switch (s) {
                    case IDLE -> {
                        if (!state.compareAndSet(s, ExecutionState.CANCELLED)) continue;
                        stop.run();
                        book.remove(plugin, this);
                        return CancelledState.CANCELLED_BY_CALLER;
                    }
                    case RUNNING -> {
                        if (!repeating) return CancelledState.RUNNING;
                        if (!state.compareAndSet(s, ExecutionState.CANCELLED_RUNNING)) continue;
                        stop.run();
                        return CancelledState.NEXT_RUNS_CANCELLED;
                    }
                    case CANCELLED -> { return CancelledState.CANCELLED_ALREADY; }
                    case CANCELLED_RUNNING -> { return CancelledState.NEXT_RUNS_CANCELLED_ALREADY; }
                    default -> { return CancelledState.ALREADY_EXECUTED; }
                }
            }
        }

        /** 이미 사라진 엔티티에 예약을 못 한 손잡이를 치운다. */
        void dropSilently() {
            state.set(ExecutionState.CANCELLED);
            book.remove(plugin, this);
        }
    }
}
