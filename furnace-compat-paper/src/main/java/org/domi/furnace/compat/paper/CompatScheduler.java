// Bukkit BukkitScheduler — H2 의 LegacyScheduler(CraftScheduler 의미) 위의 얇은 껍데기. Plugin ↔ FurnacePlugin, LegacyTask ↔ BukkitTask 를 옮긴다.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.compat.sched.LegacyScheduler;
import org.domi.furnace.compat.sched.LegacyTask;
import org.domi.furnace.compat.sched.PluginDisabledException;

public final class CompatScheduler implements BukkitScheduler {

    public static final CompatScheduler INSTANCE = new CompatScheduler();

    private CompatScheduler() {}

    private static LegacyScheduler core() { return Compat.scheduler(); }

    /** Bukkit Plugin → 그 플러그인의 Furnace 껍데기. */
    private static FurnacePlugin owner(Plugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        return CompatPluginManager.adapter(plugin);
    }

    /** 꺼진 플러그인의 예약은 Bukkit 처럼 IllegalPluginAccessException. */
    private static <T> T guard(Supplier<T> call) {
        try {
            return call.get();
        } catch (PluginDisabledException e) {
            throw new IllegalPluginAccessException(e.getMessage());
        }
    }

    private static BukkitTask wrap(LegacyTask t) { return new Handle(t); }

    // ---- 번호만 돌려주는 옛 이름들

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, Runnable task, long delay) { return guard(() -> core().scheduleSyncDelayedTask(owner(plugin), task, delay)); }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, BukkitRunnable task, long delay) { return scheduleSyncDelayedTask(plugin, (Runnable) task, delay); }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, Runnable task) { return guard(() -> core().scheduleSyncDelayedTask(owner(plugin), task)); }

    @Override
    public int scheduleSyncDelayedTask(Plugin plugin, BukkitRunnable task) { return scheduleSyncDelayedTask(plugin, (Runnable) task); }

    @Override
    public int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) {
        return guard(() -> core().scheduleSyncRepeatingTask(owner(plugin), task, delay, period));
    }

    @Override
    public int scheduleSyncRepeatingTask(Plugin plugin, BukkitRunnable task, long delay, long period) {
        return scheduleSyncRepeatingTask(plugin, (Runnable) task, delay, period);
    }

    @Override
    public int scheduleAsyncDelayedTask(Plugin plugin, Runnable task, long delay) { return guard(() -> core().scheduleAsyncDelayedTask(owner(plugin), task, delay)); }

    @Override
    public int scheduleAsyncDelayedTask(Plugin plugin, Runnable task) { return guard(() -> core().scheduleAsyncDelayedTask(owner(plugin), task)); }

    @Override
    public int scheduleAsyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) {
        return guard(() -> core().scheduleAsyncRepeatingTask(owner(plugin), task, delay, period));
    }

    @Override
    public <T> Future<T> callSyncMethod(Plugin plugin, Callable<T> task) { return guard(() -> core().callSyncMethod(owner(plugin), task)); }

    // ---- 조회·취소

    @Override
    public void cancelTask(int taskId) { core().cancelTask(taskId); }

    @Override
    public void cancelTasks(Plugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("Cannot cancel tasks of null plugin");
        core().cancelTasks(owner(plugin));
    }

    @Override
    public boolean isCurrentlyRunning(int taskId) { return core().isCurrentlyRunning(taskId); }

    @Override
    public boolean isQueued(int taskId) { return core().isQueued(taskId); }

    @Override
    public List<BukkitWorker> getActiveWorkers() {
        List<BukkitWorker> out = new ArrayList<>();
        for (LegacyScheduler.Worker w : core().activeWorkers()) out.add(new Worker(w));
        return out;
    }

    @Override
    public List<BukkitTask> getPendingTasks() {
        List<BukkitTask> out = new ArrayList<>();
        for (LegacyTask t : core().pendingTasks()) out.add(wrap(t));
        return out;
    }

    // ---- 핸들을 주는 이름들

    @Override
    public BukkitTask runTask(Plugin plugin, Runnable task) { return guard(() -> wrap(core().runTask(owner(plugin), task))); }

    @Override
    public BukkitTask runTask(Plugin plugin, BukkitRunnable task) { return runTask(plugin, (Runnable) task); }

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) { return guard(() -> wrap(core().runTaskAsynchronously(owner(plugin), task))); }

    @Override
    public BukkitTask runTaskAsynchronously(Plugin plugin, BukkitRunnable task) { return runTaskAsynchronously(plugin, (Runnable) task); }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) { return guard(() -> wrap(core().runTaskLater(owner(plugin), task, delay))); }

    @Override
    public BukkitTask runTaskLater(Plugin plugin, BukkitRunnable task, long delay) { return runTaskLater(plugin, (Runnable) task, delay); }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay) {
        return guard(() -> wrap(core().runTaskLaterAsynchronously(owner(plugin), task, delay)));
    }

    @Override
    public BukkitTask runTaskLaterAsynchronously(Plugin plugin, BukkitRunnable task, long delay) { return runTaskLaterAsynchronously(plugin, (Runnable) task, delay); }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) { return guard(() -> wrap(core().runTaskTimer(owner(plugin), task, delay, period))); }

    @Override
    public BukkitTask runTaskTimer(Plugin plugin, BukkitRunnable task, long delay, long period) { return runTaskTimer(plugin, (Runnable) task, delay, period); }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) {
        return guard(() -> wrap(core().runTaskTimerAsynchronously(owner(plugin), task, delay, period)));
    }

    @Override
    public BukkitTask runTaskTimerAsynchronously(Plugin plugin, BukkitRunnable task, long delay, long period) {
        return runTaskTimerAsynchronously(plugin, (Runnable) task, delay, period);
    }

    // ---- 핸들을 몸통에 넘기는 이름들 (Consumer<BukkitTask>)

    @Override
    public void runTask(Plugin plugin, Consumer<? super BukkitTask> task) { withHandle(task, r -> runTask(plugin, r)); }

    @Override
    public void runTaskAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task) { withHandle(task, r -> runTaskAsynchronously(plugin, r)); }

    @Override
    public void runTaskLater(Plugin plugin, Consumer<? super BukkitTask> task, long delay) { withHandle(task, r -> runTaskLater(plugin, r, delay)); }

    @Override
    public void runTaskLaterAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task, long delay) {
        withHandle(task, r -> runTaskLaterAsynchronously(plugin, r, delay));
    }

    @Override
    public void runTaskTimer(Plugin plugin, Consumer<? super BukkitTask> task, long delay, long period) { withHandle(task, r -> runTaskTimer(plugin, r, delay, period)); }

    @Override
    public void runTaskTimerAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task, long delay, long period) {
        withHandle(task, r -> runTaskTimerAsynchronously(plugin, r, delay, period));
    }

    /**
     * 몸통이 자기 핸들을 받는 변형: 예약이 끝나 핸들이 생겨야 몸통을 부른다. 핸들이 만들어지기 전에 박동이 먼저 도착할 수 있어(비동기 호출자와 박동은 다른 스레드)
     * 핸들 future 를 기다린다 — 호출자는 예약 직후 곧바로 채우므로 기다림은 마이크로초다.
     */
    private static void withHandle(Consumer<? super BukkitTask> body, java.util.function.Function<Runnable, BukkitTask> schedule) {
        if (body == null) throw new IllegalArgumentException("Task cannot be null");
        CompletableFuture<BukkitTask> handle = new CompletableFuture<>();
        BukkitTask t = schedule.apply(() -> body.accept(handle.join()));
        handle.complete(t);
    }

    // ---- 핸들

    private static final class Handle implements BukkitTask {
        private final LegacyTask task;

        Handle(LegacyTask task) { this.task = task; }

        @Override
        public int getTaskId() { return task.taskId(); }

        @Override
        public Plugin getOwner() { return task.owner() instanceof BukkitPluginAdapter a ? a.javaPlugin() : null; }

        @Override
        public boolean isSync() { return task.isSync(); }

        @Override
        public boolean isCancelled() { return task.isCancelled(); }

        @Override
        public void cancel() { task.cancel(); }

        @Override
        public boolean equals(Object o) { return o instanceof Handle h && h.task.taskId() == task.taskId(); }

        @Override
        public int hashCode() { return Integer.hashCode(task.taskId()); }

        @Override
        public String toString() { return "BukkitTask#" + task.taskId(); }
    }

    private record Worker(LegacyScheduler.Worker w) implements BukkitWorker {
        @Override
        public int getTaskId() { return w.taskId(); }

        @Override
        public Plugin getOwner() { return w.owner() instanceof BukkitPluginAdapter a ? a.javaPlugin() : null; }

        @Override
        public Thread getThread() { return w.thread(); }
    }
}
