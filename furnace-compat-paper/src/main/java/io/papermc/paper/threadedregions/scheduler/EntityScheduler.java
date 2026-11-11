package io.papermc.paper.threadedregions.scheduler;

import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;

/** Paper 엔티티 스케줄러: 엔티티를 따라다니는 region 스레드에서 돈다. 엔티티가 사라졌으면 예약하지 않고 retired 도 부르지 않는다(null/false). */
public interface EntityScheduler {

    boolean execute(Plugin plugin, Runnable run, Runnable retired, long delay);

    ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task, Runnable retired);

    ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, Runnable retired, long delayTicks);

    ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, Runnable retired, long initialDelayTicks, long periodTicks);
}
