package io.papermc.paper.threadedregions.scheduler;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

/** Paper 비동기 스케줄러(벽시계 시간 단위, 서버 스레드 밖). */
public interface AsyncScheduler {

    ScheduledTask runNow(Plugin plugin, Consumer<ScheduledTask> task);

    ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delay, TimeUnit unit);

    ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, long initialDelay, long period, TimeUnit unit);

    void cancelTasks(Plugin plugin);
}
