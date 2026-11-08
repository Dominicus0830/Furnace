package org.bukkit.scheduler;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

/**
 * 옛 스케줄러(Bukkit BukkitScheduler). 시간은 틱(1/20초)이고, "동기" 작업은 전역(주) 스레드에서 한 줄로 돈다.
 * 블록·엔티티처럼 region 이 가진 상태를 만지는 코드는 region 스케줄러로 넘겨야 안전하다 — docs/feature-parity/compat-h2-2026-11-07.md.
 */
public interface BukkitScheduler {

    int scheduleSyncDelayedTask(Plugin plugin, Runnable task, long delay);

    @Deprecated
    int scheduleSyncDelayedTask(Plugin plugin, BukkitRunnable task, long delay);

    int scheduleSyncDelayedTask(Plugin plugin, Runnable task);

    @Deprecated
    int scheduleSyncDelayedTask(Plugin plugin, BukkitRunnable task);

    int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period);

    @Deprecated
    int scheduleSyncRepeatingTask(Plugin plugin, BukkitRunnable task, long delay, long period);

    @Deprecated
    int scheduleAsyncDelayedTask(Plugin plugin, Runnable task, long delay);

    @Deprecated
    int scheduleAsyncDelayedTask(Plugin plugin, Runnable task);

    @Deprecated
    int scheduleAsyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period);

    <T> Future<T> callSyncMethod(Plugin plugin, Callable<T> task);

    void cancelTask(int taskId);

    void cancelTasks(Plugin plugin);

    boolean isCurrentlyRunning(int taskId);

    boolean isQueued(int taskId);

    List<BukkitWorker> getActiveWorkers();

    List<BukkitTask> getPendingTasks();

    BukkitTask runTask(Plugin plugin, Runnable task) throws IllegalArgumentException;

    void runTask(Plugin plugin, Consumer<? super BukkitTask> task) throws IllegalArgumentException;

    @Deprecated
    BukkitTask runTask(Plugin plugin, BukkitRunnable task) throws IllegalArgumentException;

    BukkitTask runTaskAsynchronously(Plugin plugin, Runnable task) throws IllegalArgumentException;

    void runTaskAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task) throws IllegalArgumentException;

    @Deprecated
    BukkitTask runTaskAsynchronously(Plugin plugin, BukkitRunnable task) throws IllegalArgumentException;

    BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) throws IllegalArgumentException;

    void runTaskLater(Plugin plugin, Consumer<? super BukkitTask> task, long delay) throws IllegalArgumentException;

    @Deprecated
    BukkitTask runTaskLater(Plugin plugin, BukkitRunnable task, long delay) throws IllegalArgumentException;

    BukkitTask runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay) throws IllegalArgumentException;

    void runTaskLaterAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task, long delay) throws IllegalArgumentException;

    @Deprecated
    BukkitTask runTaskLaterAsynchronously(Plugin plugin, BukkitRunnable task, long delay) throws IllegalArgumentException;

    BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) throws IllegalArgumentException;

    void runTaskTimer(Plugin plugin, Consumer<? super BukkitTask> task, long delay, long period) throws IllegalArgumentException;

    @Deprecated
    BukkitTask runTaskTimer(Plugin plugin, BukkitRunnable task, long delay, long period) throws IllegalArgumentException;

    BukkitTask runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period) throws IllegalArgumentException;

    void runTaskTimerAsynchronously(Plugin plugin, Consumer<? super BukkitTask> task, long delay, long period) throws IllegalArgumentException;

    @Deprecated
    BukkitTask runTaskTimerAsynchronously(Plugin plugin, BukkitRunnable task, long delay, long period) throws IllegalArgumentException;

    /**
     * Paper 전용 API: 실행하면 다음 틱에 그 플러그인의 동기 작업으로 도는 실행기.
     *
     * @param plugin 작업을 거는 플러그인
     */
    java.util.concurrent.Executor getMainThreadExecutor(Plugin plugin);
}
