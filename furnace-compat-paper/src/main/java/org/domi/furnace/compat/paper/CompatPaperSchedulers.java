// Paper(Folia) 스케줄러 API(AsyncScheduler·GlobalRegionScheduler·RegionScheduler·EntityScheduler) — PaperSchedulers 코어 위의 얇은 껍데기.
// Plugin ↔ FurnacePlugin, CompatTask ↔ ScheduledTask 를 옮기고, 꺼진 플러그인의 예약은 Paper 처럼 IllegalPluginAccessException 으로 알린다.
package org.domi.furnace.compat.paper;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.compat.sched.CompatTask;
import org.domi.furnace.compat.sched.PaperSchedulers;
import org.domi.furnace.compat.sched.PluginDisabledException;

public final class CompatPaperSchedulers {

    private CompatPaperSchedulers() {}

    public static final AsyncScheduler ASYNC = new AsyncImpl();
    public static final GlobalRegionScheduler GLOBAL = new GlobalImpl();
    public static final RegionScheduler REGION = new RegionImpl();

    /** 엔티티 하나의 스케줄러(Entity#getScheduler). */
    public static EntityScheduler entity(org.bukkit.craftbukkit.entity.CraftEntity e) { return new EntityImpl(e); }

    private static PaperSchedulers core() { return Compat.paperSchedulers(); }

    private static FurnacePlugin owner(Plugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("Plugin may not be null");
        return CompatPluginManager.adapter(plugin);
    }

    /** 꺼진 플러그인의 예약은 Paper 처럼 IllegalPluginAccessException. */
    private static <T> T guard(Supplier<T> call) {
        try {
            return call.get();
        } catch (PluginDisabledException e) {
            throw new IllegalPluginAccessException(e.getMessage());
        }
    }

    /** 예약하고, 작업 안에서 받는 손잡이와 돌려주는 손잡이가 같은 객체가 되게 한다(첫 실행이 예약 호출이 끝나기 전에 일 수 있다). */
    private static ScheduledTask schedule(Plugin plugin, Consumer<ScheduledTask> task, Function<Consumer<CompatTask>, CompatTask> scheduling) {
        if (task == null) throw new IllegalArgumentException("Task may not be null");
        PaperTask pt = new PaperTask(plugin);
        CompatTask ct = guard(() -> scheduling.apply(t -> {
            pt.bind(t);   // 코어가 작업에 넘기는 손잡이가 곧 돌려줄 손잡이 — 첫 실행이 예약 호출 반환보다 앞서도 기다릴 필요가 없다
            task.accept(pt);
        }));
        if (ct == null) return null;   // 사라진 엔티티
        pt.bind(ct);
        return pt;
    }

    private static final class PaperTask implements ScheduledTask {
        private final Plugin plugin;
        private volatile CompatTask inner;

        PaperTask(Plugin plugin) { this.plugin = plugin; }

        void bind(CompatTask t) { inner = t; }

        private CompatTask task() { return inner; }

        @Override
        public Plugin getOwningPlugin() { return plugin; }

        @Override
        public boolean isRepeatingTask() { return task().isRepeating(); }

        @Override
        public CancelledState cancel() { return CancelledState.valueOf(task().cancel().name()); }

        @Override
        public ExecutionState getExecutionState() { return ExecutionState.valueOf(task().state().name()); }
    }

    // ---- 비동기

    private static final class AsyncImpl implements AsyncScheduler {
        @Override
        public ScheduledTask runNow(Plugin plugin, Consumer<ScheduledTask> task) {
            return schedule(plugin, task, c -> core().async().runNow(owner(plugin), c));
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delay, TimeUnit unit) {
            return schedule(plugin, task, c -> core().async().runDelayed(owner(plugin), c, delay, unit));
        }

        @Override
        public ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, long initialDelay, long period, TimeUnit unit) {
            return schedule(plugin, task, c -> core().async().runAtFixedRate(owner(plugin), c, initialDelay, period, unit));
        }

        @Override
        public void cancelTasks(Plugin plugin) { core().async().cancelTasks(owner(plugin)); }
    }

    // ---- 전역

    private static final class GlobalImpl implements GlobalRegionScheduler {
        @Override
        public void execute(Plugin plugin, Runnable run) {
            if (run == null) throw new IllegalArgumentException("Task may not be null");
            guard(() -> {
                core().global().execute(owner(plugin), run);
                return null;
            });
        }

        @Override
        public ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task) {
            return schedule(plugin, task, c -> core().global().run(owner(plugin), c));
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delayTicks) {
            return schedule(plugin, task, c -> core().global().runDelayed(owner(plugin), c, delayTicks));
        }

        @Override
        public ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks) {
            return schedule(plugin, task, c -> core().global().runAtFixedRate(owner(plugin), c, initialDelayTicks, periodTicks));
        }

        @Override
        public void cancelTasks(Plugin plugin) { core().global().cancelTasks(owner(plugin)); }
    }

    // ---- region

    private static org.domi.furnace.api.world.World furnaceWorld(World w) {
        if (w == null) throw new IllegalArgumentException("World may not be null");
        org.domi.furnace.api.world.World f = Compat.furnace().world(Compat.dimension(w));
        if (f == null) throw new IllegalArgumentException("World not loaded: " + w.getName());
        return f;
    }

    private static final class RegionImpl implements RegionScheduler {
        @Override
        public void execute(Plugin plugin, World world, int chunkX, int chunkZ, Runnable run) {
            if (run == null) throw new IllegalArgumentException("Task may not be null");
            guard(() -> {
                core().region().execute(owner(plugin), furnaceWorld(world), chunkX, chunkZ, run);
                return null;
            });
        }

        @Override
        public void execute(Plugin plugin, Location location, Runnable run) {
            if (run == null) throw new IllegalArgumentException("Task may not be null");
            guard(() -> {
                core().region().execute(owner(plugin), Compat.location(location), run);
                return null;
            });
        }

        @Override
        public ScheduledTask run(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task) {
            return schedule(plugin, task, c -> core().region().run(owner(plugin), furnaceWorld(world), chunkX, chunkZ, c));
        }

        @Override
        public ScheduledTask run(Plugin plugin, Location location, Consumer<ScheduledTask> task) {
            return schedule(plugin, task, c -> core().region().run(owner(plugin), Compat.location(location), c));
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task, long delayTicks) {
            return schedule(plugin, task, c -> core().region().runDelayed(owner(plugin), furnaceWorld(world), chunkX, chunkZ, c, delayTicks));
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Location location, Consumer<ScheduledTask> task, long delayTicks) {
            return schedule(plugin, task, c -> core().region().runDelayed(owner(plugin), Compat.location(location), c, delayTicks));
        }

        @Override
        public ScheduledTask runAtFixedRate(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task,
                                            long initialDelayTicks, long periodTicks) {
            return schedule(plugin, task, c -> core().region().runAtFixedRate(owner(plugin), furnaceWorld(world), chunkX, chunkZ, c, initialDelayTicks, periodTicks));
        }

        @Override
        public ScheduledTask runAtFixedRate(Plugin plugin, Location location, Consumer<ScheduledTask> task, long initialDelayTicks, long periodTicks) {
            return schedule(plugin, task, c -> core().region().runAtFixedRate(owner(plugin), Compat.location(location), c, initialDelayTicks, periodTicks));
        }
    }

    // ---- 엔티티

    private static final class EntityImpl implements EntityScheduler {
        private final org.bukkit.craftbukkit.entity.CraftEntity entity;

        EntityImpl(org.bukkit.craftbukkit.entity.CraftEntity entity) { this.entity = entity; }

        private org.domi.furnace.api.entity.Entity handle() { return entity.furnaceHandle(); }

        @Override
        public boolean execute(Plugin plugin, Runnable run, Runnable retired, long delay) {
            if (run == null) throw new IllegalArgumentException("Task may not be null");
            return guard(() -> core().entity().execute(owner(plugin), handle(), run, retired, delay));
        }

        @Override
        public ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task, Runnable retired) {
            return schedule(plugin, task, c -> core().entity().run(owner(plugin), handle(), c, retired));
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, Runnable retired, long delayTicks) {
            return schedule(plugin, task, c -> core().entity().runDelayed(owner(plugin), handle(), c, retired, delayTicks));
        }

        @Override
        public ScheduledTask runAtFixedRate(Plugin plugin, Consumer<ScheduledTask> task, Runnable retired, long initialDelayTicks, long periodTicks) {
            return schedule(plugin, task, c -> core().entity().runAtFixedRate(owner(plugin), handle(), c, retired, initialDelayTicks, periodTicks));
        }
    }
}
