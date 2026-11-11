// 플러그인 EntityScheduler — 서버 엔티티(또는 플레이어)에 붙은 EntityTasks 대기열을 감싼다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.entity.EntityScheduler;
import org.domi.furnace.api.profiler.PluginProfiler;
import org.domi.furnace.api.sched.ScheduledTask;
import org.domi.furnace.server.entity.EntityTasks;

final class EntitySchedulerImpl implements EntityScheduler {

    private final EntityTasks tasks;
    /** 작업 시간을 만든 플러그인 몫으로 잰다(/timings). */
    private final ProfiledScheduling profiled;

    EntitySchedulerImpl(EntityTasks tasks, ProfiledScheduling profiled) {
        this.tasks = tasks;
        this.profiled = profiled;
    }

    private Runnable w(Runnable r) { return r == null ? null : profiled.wrap(r, PluginProfiler.Kind.TASK); }

    @Override
    public boolean execute(Runnable task, Runnable retired, long delayTicks) {
        return tasks.schedule(w(task), w(retired), delayTicks, 0) != null;
    }

    @Override
    public ScheduledTask runDelayed(Runnable task, Runnable retired, long delayTicks) {
        return handle(tasks.schedule(w(task), w(retired), delayTicks, 0));
    }

    @Override
    public ScheduledTask runAtFixedRate(Runnable task, Runnable retired, long initialDelayTicks, long periodTicks) {
        if (periodTicks <= 0) throw new IllegalArgumentException("periodTicks 는 1 이상: " + periodTicks);
        Runnable r = w(retired);
        return handle(profiled.scheduleRepeating(task, PluginProfiler.Kind.TASK,
                t -> tasks.schedule(t, r, initialDelayTicks, periodTicks), e -> e::cancel));
    }

    @Override
    public boolean isRetired() { return tasks.isRetired(); }

    private static ScheduledTask handle(EntityTasks.Entry e) {
        if (e == null) return null;
        return new ScheduledTask() {
            @Override
            public void cancel() { e.cancel(); }

            @Override
            public boolean isCancelled() { return e.isCancelled(); }
        };
    }
}
