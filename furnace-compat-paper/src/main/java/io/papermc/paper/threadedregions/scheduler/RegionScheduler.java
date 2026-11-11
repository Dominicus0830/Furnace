package io.papermc.paper.threadedregions.scheduler;

import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** Paper region 스케줄러: 청크를 가진 region 스레드에서 돈다. */
public interface RegionScheduler {

    void execute(Plugin plugin, World world, int chunkX, int chunkZ, Runnable run);

    default void execute(Plugin plugin, Location location, Runnable run) {
        execute(plugin, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, run);
    }

    ScheduledTask run(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task);

    default ScheduledTask run(Plugin plugin, Location location, Consumer<ScheduledTask> task) {
        return run(plugin, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, task);
    }

    ScheduledTask runDelayed(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task, long delayTicks);

    default ScheduledTask runDelayed(Plugin plugin, Location location, Consumer<ScheduledTask> task, long delayTicks) {
        return runDelayed(plugin, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, task, delayTicks);
    }

    ScheduledTask runAtFixedRate(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task,
                                 long initialDelayTicks, long periodTicks);

    default ScheduledTask runAtFixedRate(Plugin plugin, Location location, Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks) {
        return runAtFixedRate(plugin, location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4, task, initialDelayTicks, periodTicks);
    }
}
