package org.bukkit.scheduler;

import org.bukkit.plugin.Plugin;

/** 지금 비동기 작업을 돌리고 있는 일꾼(Bukkit BukkitWorker). */
public interface BukkitWorker {

    int getTaskId();

    Plugin getOwner();

    Thread getThread();
}
