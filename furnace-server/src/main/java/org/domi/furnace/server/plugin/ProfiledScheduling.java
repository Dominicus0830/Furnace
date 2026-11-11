// 플러그인이 받는 스케줄러·실행기를 감싸 작업 시간을 그 작업을 만든 플러그인 몫으로 잰다.
// 플러그인은 PluginTasks 를 거치지 않고 server().scheduler()·entity.scheduler()·asyncExecutor() 를 바로 쓸 수도 있어서,
// 작업 객체의 클래스로더(람다는 정의한 클래스의 로더)로 플러그인을 찾는다. 플러그인 것이 아니면 감싸지 않는다(서버·API 의 작업,
// PluginTasks 처럼 이미 플러그인을 알고 재는 작업). 감싸기는 늘 한다 — 기동 때 예약한 반복 작업도 나중에 켠 프로파일러에 잡히게.
// 꺼져 있을 때 감싼 작업이 더 쓰는 것은 volatile 읽기 하나다(구간은 처음 켜진 채로 돌 때 만든다).
// 감싼 예약 작업(TASK)은 소유 플러그인이 꺼졌으면 돌지 않고, 반복 작업이면 스스로 취소한다(A14-02) — 어느 스케줄러로 건 작업이든
// 꺼진 플러그인 코드가 더 돌지 않고 옛 클래스로더를 붙잡지 않는다(Paper 는 disablePlugin 때 그 플러그인 작업을 모두 취소한다).
// 비동기 실행기(ASYNC)는 그대로 돌린다 — onDisable 에서 넘긴 저장 같은 블로킹 작업을 잃지 않게.
package org.domi.furnace.server.plugin;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.profiler.PluginProfiler.Kind;
import org.domi.furnace.api.profiler.Timing;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.GlobalScheduler;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.sched.ScheduledTask;
import org.domi.furnace.api.sched.TaskScheduler;

public final class ProfiledScheduling {

    private final PluginProfilerImpl profiler;
    /** 클래스 → 그 클래스를 정의한 플러그인(없으면 null). */
    private final Function<Class<?>, FurnacePlugin> owner;

    public ProfiledScheduling(PluginProfilerImpl profiler, Function<Class<?>, FurnacePlugin> owner) {
        this.profiler = profiler;
        this.owner = owner;
    }

    FurnacePlugin ownerOf(Object task) { return task == null ? null : owner.apply(task.getClass()); }

    /**
     * 플러그인 한 번의 "켜짐" 동안 유효한 표. 감싼 작업은 만들 때의 표를 쥐고, 플러그인이 꺼지면(retire) 그 표가 죽는다 —
     * 같은 플러그인 객체를 껐다 다시 켜도 꺼지기 전에 건 작업은 되살아나지 않는다(Paper 는 disablePlugin 때 취소한다).
     */
    private static final class Epoch {
        volatile boolean dead;
    }

    private final java.util.concurrent.ConcurrentHashMap<FurnacePlugin, Epoch> epochs = new java.util.concurrent.ConcurrentHashMap<>();

    private Epoch epochOf(FurnacePlugin p) { return epochs.computeIfAbsent(p, k -> new Epoch()); }

    /** 플러그인이 꺼질 때(PluginManagerImpl.disable): 그때까지 감싼 작업을 모두 죽은 것으로 한다. */
    void retire(FurnacePlugin p) {
        Epoch e = epochs.remove(p);
        if (e != null) e.dead = true;
    }

    // ------------------------------------------------------------------ 작업

    /** 플러그인 작업이면 재는 작업으로 감싼다(소유 플러그인이 꺼졌으면 돌지 않는다). */
    public Runnable wrap(Runnable task, Kind kind) {
        FurnacePlugin p = ownerOf(task);
        return p == null ? task : new MeasuredRunnable(this, p, kind, task);
    }

    /**
     * 반복 작업을 감싸 건다: schedule 이 돌려준 손잡이를 감싼 작업에 알려 두어, 소유 플러그인이 꺼진 뒤 차례가 오면 스스로 취소한다
     * (다음 예약을 하지 않아 작업 객체·클래스로더가 풀린다). 플러그인 작업이 아니면 그대로 건다.
     */
    <H> H scheduleRepeating(Runnable task, Kind kind, Function<Runnable, H> schedule, Function<H, Runnable> canceller) {
        FurnacePlugin p = ownerOf(task);
        if (p == null) return schedule.apply(task);
        MeasuredRunnable m = new MeasuredRunnable(this, p, kind, task);
        H h = schedule.apply(m);
        if (h != null) m.stop = canceller.apply(h);
        return h;
    }

    <T> Supplier<T> wrap(Supplier<T> task, Kind kind) {
        FurnacePlugin p = ownerOf(task);
        if (p == null) return task;
        Lazy lazy = new Lazy(this, p, kind, taskName(task));
        return () -> {
            if (lazy.dead()) throw new java.util.concurrent.CancellationException("플러그인이 꺼짐: " + p.name());
            Timing t = lazy.timing();
            long t0 = t.start();
            try {
                return task.get();
            } finally {
                t.stop(t0);
            }
        };
    }

    <T> Callable<T> wrap(Callable<T> task, Kind kind) {
        FurnacePlugin p = ownerOf(task);
        if (p == null) return task;
        Lazy lazy = new Lazy(this, p, kind, taskName(task));
        return () -> {
            if (lazy.dead()) throw new java.util.concurrent.CancellationException("플러그인이 꺼짐: " + p.name());
            Timing t = lazy.timing();
            long t0 = t.start();
            try {
                return task.call();
            } finally {
                t.stop(t0);
            }
        };
    }

    static String taskName(Object task) {
        String n = task.getClass().getName();
        int slash = n.indexOf('/');
        int dot = n.lastIndexOf('.', slash < 0 ? n.length() : slash);
        return dot < 0 ? n : n.substring(dot + 1);
    }

    /** 구간을 처음 켜진 채로 돌 때 만든다 — 꺼져 있는 동안 예약만 하는 작업은 맵을 건드리지 않는다. */
    private static final class Lazy {
        private final ProfiledScheduling s;
        private final FurnacePlugin plugin;
        private final Kind kind;
        private final String name;
        /** TASK 만: 만들 때의 켜짐 표(ASYNC 는 꺼진 뒤에도 돈다 — onDisable 에서 넘긴 저장을 잃지 않게). */
        private final Epoch epoch;
        private volatile Timing timing;

        Lazy(ProfiledScheduling s, FurnacePlugin plugin, Kind kind, String name) {
            this.s = s;
            this.plugin = plugin;
            this.kind = kind;
            this.name = name;
            this.epoch = kind == Kind.TASK ? s.epochOf(plugin) : null;
        }

        /** 이 작업을 더는 돌리지 않는가: 소유 플러그인이 꺼졌거나, 만든 뒤 한 번이라도 꺼졌다 켜졌다. */
        boolean dead() { return epoch != null && (epoch.dead || !plugin.isEnabled()); }

        Timing timing() {
            Timing t = timing;
            if (t != null) return t;
            if (!s.profiler.isRunning()) return Timing.NONE;
            return timing = s.profiler.timing(plugin, kind, name);
        }
    }

    private static final class MeasuredRunnable implements Runnable {
        private final Lazy lazy;
        /** 꺼진 뒤 손을 놓으려고 null 로 바꾼다(반복 작업 손잡이가 남아 있어도 플러그인 람다는 풀린다). */
        private volatile Runnable task;
        /** 반복 작업이면 그 손잡이의 취소. 소유 플러그인이 꺼진 뒤 차례가 오면 부른다. */
        volatile Runnable stop;

        MeasuredRunnable(ProfiledScheduling s, FurnacePlugin p, Kind kind, Runnable task) {
            this.lazy = new Lazy(s, p, kind, taskName(task));
            this.task = task;
        }

        @Override
        public void run() {
            Runnable task = this.task;
            if (task == null || lazy.dead()) {
                this.task = null;
                Runnable s = stop;   // 손잡이를 알기 전에 꺼짐을 봤어도 다음 차례에 취소한다
                if (s != null) s.run();
                return;
            }
            Timing t = lazy.timing();
            long t0 = t.start();
            try {
                task.run();
            } finally {
                t.stop(t0);
            }
        }
    }

    // ------------------------------------------------------------------ 스케줄러

    public FurnaceScheduler wrap(FurnaceScheduler s) {
        GlobalScheduler g = new Global(this, s.global());
        return new FurnaceScheduler() {
            @Override
            public GlobalScheduler global() { return g; }

            @Override
            public TaskScheduler region(RegionPos pos) { return wrap(s.region(pos)); }
        };
    }

    public TaskScheduler wrap(TaskScheduler s) { return s == null ? null : new Scheduler(this, s); }

    /** 감싼 TaskScheduler. 받은 스케줄러가 ThreadOwner 면 그대로 대답한다. */
    static class Scheduler implements TaskScheduler, ThreadOwner {
        final ProfiledScheduling p;
        final TaskScheduler d;

        Scheduler(ProfiledScheduling p, TaskScheduler d) {
            this.p = p;
            this.d = d;
        }

        @Override
        public CompletableFuture<Void> execute(Runnable task) { return d.execute(p.wrap(task, Kind.TASK)); }

        @Override
        public <T> CompletableFuture<T> submit(Supplier<T> task) { return d.submit(p.wrap(task, Kind.TASK)); }

        @Override
        public CompletableFuture<Void> executeDelayed(Runnable task, long delayTicks) { return d.executeDelayed(p.wrap(task, Kind.TASK), delayTicks); }

        // 기본 구현은 작업을 Repeating/Once 로 감싼 뒤 executeDelayed 로 넘겨 클래스로더가 가려진다 — 먼저 감싸고 원래 것에 맡긴다
        @Override
        public ScheduledTask runAtFixedRate(Runnable task, long initialDelayTicks, long periodTicks) {
            return p.scheduleRepeating(task, Kind.TASK, w -> d.runAtFixedRate(w, initialDelayTicks, periodTicks), h -> h::cancel);
        }

        @Override
        public ScheduledTask runLater(Runnable task, long delayTicks) { return d.runLater(p.wrap(task, Kind.TASK), delayTicks); }

        @Override
        public boolean isOwnerThread() { return d instanceof ThreadOwner o && o.isOwnerThread(); }

        @Override
        public String ownerName() { return d instanceof ThreadOwner o ? o.ownerName() : d.toString(); }

        @Override
        public String toString() { return d.toString(); }
    }

    static final class Global extends Scheduler implements GlobalScheduler {
        Global(ProfiledScheduling p, GlobalScheduler d) { super(p, d); }

        @Override
        public RegionAccess openRegionAccess() { return ((GlobalScheduler) d).openRegionAccess(); }
    }

    // ------------------------------------------------------------------ 비동기 실행기

    /** 비동기 실행기: execute·submit 의 작업을 ASYNC 로 잰다. 닫기 등은 원래 것에 넘긴다. */
    public ExecutorService wrap(ExecutorService d) {
        ProfiledScheduling p = this;
        return new AbstractExecutorService() {
            @Override
            public void execute(Runnable command) { d.execute(p.wrap(command, Kind.ASYNC)); }

            // submit 은 작업을 FutureTask 로 싼 뒤 execute 로 넘겨 클래스로더가 가려진다 — 싸기 전에 감싼다
            @Override
            protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) { return super.newTaskFor(p.wrap(runnable, Kind.ASYNC), value); }

            @Override
            protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) { return super.newTaskFor(p.wrap(callable, Kind.ASYNC)); }

            @Override
            public void shutdown() { d.shutdown(); }

            @Override
            public List<Runnable> shutdownNow() { return d.shutdownNow(); }

            @Override
            public boolean isShutdown() { return d.isShutdown(); }

            @Override
            public boolean isTerminated() { return d.isTerminated(); }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException { return d.awaitTermination(timeout, unit); }
        };
    }
}
