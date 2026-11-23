package org.bukkit.craftbukkit.scheduler;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitWorker;
import org.domi.furnace.compat.sched.LegacyScheduler;
import org.domi.furnace.compat.sched.LegacyTask;

/** 예약된 작업의 손잡이(Paper 와 같은 이름 — T-264). 비동기 작업은 CraftAsyncTask, callSyncMethod 는 CraftFuture. */
public class CraftTask implements BukkitTask, Runnable {
    final LegacyTask task;

    CraftTask(LegacyTask task) { this.task = task; }

    @Override
    public int getTaskId() { return task.taskId(); }

    @Override
    public Plugin getOwner() { return CraftScheduler.ownerOf(task.owner()); }

    @Override
    public boolean isSync() { return task.isSync(); }

    @Override
    public boolean isCancelled() { return task.isCancelled(); }

    @Override
    public void cancel() { task.cancel(); }

    /** Paper 처럼 몸통을 이 스레드에서 곧바로 돌린다. */
    @Override
    public void run() { task.runBody(); }

    @Override
    public boolean equals(Object o) { return o instanceof CraftTask h && h.task.taskId() == task.taskId(); }

    @Override
    public int hashCode() { return Integer.hashCode(task.taskId()); }

    @Override
    public String toString() { return "BukkitTask#" + task.taskId(); }

    /** 일하는 비동기 작업 하나(Paper 처럼 이름 없는 BukkitWorker). */
    static BukkitWorker worker(LegacyScheduler.Worker w) {
        return new BukkitWorker() {
            @Override
            public int getTaskId() { return w.taskId(); }

            @Override
            public Plugin getOwner() { return CraftScheduler.ownerOf(w.owner()); }

            @Override
            public Thread getThread() { return w.thread(); }
        };
    }
}
