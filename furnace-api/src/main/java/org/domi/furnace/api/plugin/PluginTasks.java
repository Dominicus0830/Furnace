// 플러그인 전용 작업 예약 — 여기서 만든 작업은 플러그인이 꺼질 때 모두 취소된다.
package org.domi.furnace.api.plugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.sched.ScheduledTask;
import org.domi.furnace.api.sched.TaskScheduler;

public final class PluginTasks {

    private final FurnacePlugin plugin;
    private final FurnaceScheduler scheduler;
    private final Executor async;
    private final Set<ScheduledTask> tasks = ConcurrentHashMap.newKeySet();

    PluginTasks(FurnacePlugin plugin, FurnaceScheduler scheduler, Executor async) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.async = async;
    }

    /** 전역(어느 region 에도 속하지 않는) 스레드에서 delayTicks 뒤 실행. */
    public ScheduledTask runGlobal(Runnable task, long delayTicks) { return track(scheduler.global().runLater(guard(task), delayTicks)); }

    public ScheduledTask runGlobalRepeating(Runnable task, long delayTicks, long periodTicks) {
        return track(scheduler.global().runAtFixedRate(guard(task), delayTicks, periodTicks));
    }

    /** 그 region 을 소유한 스레드에서 실행 — 그 region 의 블록·엔티티를 안전하게 만질 수 있다. */
    public ScheduledTask runRegion(RegionPos region, Runnable task, long delayTicks) {
        return track(scheduler.region(region).runLater(guard(task), delayTicks));
    }

    public ScheduledTask runRegionRepeating(RegionPos region, Runnable task, long delayTicks, long periodTicks) {
        return track(scheduler.region(region).runAtFixedRate(guard(task), delayTicks, periodTicks));
    }

    /** 블록킹 작업(파일·DB·HTTP)용 비동기 실행(가상 스레드). */
    public void runAsync(Runnable task) { async.execute(guard(task)); }

    /** 원하는 스케줄러를 직접 쓸 때. 그렇게 만든 작업은 자동 취소되지 않는다. */
    public TaskScheduler global() { return scheduler.global(); }

    /** 이 플러그인이 만든 작업을 모두 취소(플러그인이 꺼질 때 서버가 부른다). */
    public void cancelAll() {
        for (ScheduledTask t : tasks) t.cancel();
        tasks.clear();
    }

    private ScheduledTask track(ScheduledTask t) {
        tasks.add(t);
        return t;
    }

    /** 꺼진 플러그인의 작업은 돌지 않는다. 예외는 플러그인 로거로. */
    private Runnable guard(Runnable task) {
        return () -> {
            if (!plugin.isEnabled()) return;
            try {
                task.run();
            } catch (Throwable t) {
                plugin.logger().log(java.util.logging.Level.SEVERE, "작업 실행 중 예외", t);
            }
        };
    }
}
