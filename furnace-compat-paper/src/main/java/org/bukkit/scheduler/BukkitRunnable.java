package org.bukkit.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** 스스로 예약하고 취소하는 Runnable(Bukkit BukkitRunnable). 한 번만 예약할 수 있다. */
public abstract class BukkitRunnable implements Runnable {

    private BukkitTask task;

    public synchronized boolean isCancelled() throws IllegalStateException {
        checkScheduled();
        return task.isCancelled();
    }

    public synchronized void cancel() throws IllegalStateException {
        Bukkit.getScheduler().cancelTask(getTaskId());
    }

    public synchronized BukkitTask runTask(Plugin plugin) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTask(plugin, (Runnable) this));
    }

    public synchronized BukkitTask runTaskAsynchronously(Plugin plugin) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskAsynchronously(plugin, (Runnable) this));
    }

    public synchronized BukkitTask runTaskLater(Plugin plugin, long delay) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskLater(plugin, (Runnable) this, delay));
    }

    public synchronized BukkitTask runTaskLaterAsynchronously(Plugin plugin, long delay) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, (Runnable) this, delay));
    }

    public synchronized BukkitTask runTaskTimer(Plugin plugin, long delay, long period) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this, delay, period));
    }

    public synchronized BukkitTask runTaskTimerAsynchronously(Plugin plugin, long delay, long period) throws IllegalArgumentException, IllegalStateException {
        checkNotYetScheduled();
        return setupTask(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, (Runnable) this, delay, period));
    }

    public synchronized int getTaskId() throws IllegalStateException {
        checkScheduled();
        return task.getTaskId();
    }

    private void checkScheduled() {
        if (task == null) throw new IllegalStateException("Not scheduled yet");
    }

    private void checkNotYetScheduled() {
        if (task != null) throw new IllegalStateException("Already scheduled as " + task.getTaskId());
    }

    private BukkitTask setupTask(BukkitTask task) {
        this.task = task;
        return task;
    }
}
