package org.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

/** 예약된 작업의 손잡이(Bukkit BukkitTask). */
public interface BukkitTask {

    int getTaskId();

    Plugin getOwner();

    /** 주(전역) 스레드에서 도는 작업이면 true, 비동기면 false. */
    boolean isSync();

    boolean isCancelled();

    void cancel();
}
