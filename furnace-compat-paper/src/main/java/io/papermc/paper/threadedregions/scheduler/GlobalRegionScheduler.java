package io.papermc.paper.threadedregions.scheduler;

import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

/** Paper 전역 스케줄러(서버 틱 단위, 전역 스레드). */
public interface GlobalRegionScheduler {

    void execute(Plugin plugin, Runnable run);

    ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task);

    ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delayTicks);

    ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks);

    void cancelTasks(Plugin plugin);
}
