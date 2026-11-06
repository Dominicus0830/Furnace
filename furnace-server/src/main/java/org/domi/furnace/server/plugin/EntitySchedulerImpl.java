// 플러그인 EntityScheduler — 서버 엔티티(또는 플레이어)에 붙은 EntityTasks 대기열을 감싼다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.entity.EntityScheduler;
import org.domi.furnace.api.sched.ScheduledTask;
import org.domi.furnace.server.entity.EntityTasks;

final class EntitySchedulerImpl implements EntityScheduler {

    private final EntityTasks tasks;

    EntitySchedulerImpl(EntityTasks tasks) { this.tasks = tasks; }

    @Override
    public boolean execute(Runnable task, Runnable retired, long delayTicks) {
        return tasks.schedule(task, retired, delayTicks, 0) != null;
    }

    @Override
    public ScheduledTask runDelayed(Runnable task, Runnable retired, long delayTicks) {
        return handle(tasks.schedule(task, retired, delayTicks, 0));
    }

    @Override
    public ScheduledTask runAtFixedRate(Runnable task, Runnable retired, long initialDelayTicks, long periodTicks) {
        if (periodTicks <= 0) throw new IllegalArgumentException("periodTicks 는 1 이상: " + periodTicks);
        return handle(tasks.schedule(task, retired, initialDelayTicks, periodTicks));
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
