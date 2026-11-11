// Paper API 의 스케줄러 넷(Server#getAsyncScheduler·getGlobalRegionScheduler·getRegionScheduler, Entity#getScheduler)이 실제 Bukkit jar 에서 PaperSchedulers 코어에 닿는지 확인한다.
// 규칙 자체(지연 하한·상태·취소 결과)는 PaperSchedulersTest 가 덮으므로 여기서는 연결(스레드·손잡이 동일성·꺼진 플러그인·예약 이름 거부)만 본다.
package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperSchedulerApiTest {

    private static String yml(String name) {
        return "name: " + name + "\nversion: '1'\nmain: sched.SchedPlugin\napi-version: '1.21'\nauthors: [Ann]\n";
    }

    private static final String SRC = """
            package sched;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class SchedPlugin extends JavaPlugin {
                @Override public void onEnable() { WiringProbe.plugin = this; }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    private FurnaceTestServer start(String name) throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("sched.jar"), yml(name), Map.of("sched.SchedPlugin", SRC));
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    @Test
    void globalSchedulerRunsOnTheGlobalThreadAndHandsTheSameTaskObject() throws Exception {
        try (FurnaceTestServer server = start("Sched")) {
            Plugin p = WiringProbe.plugin;
            Thread global = server.onGlobal(Thread::currentThread);
            AtomicReference<Thread> on = new AtomicReference<>();
            AtomicReference<ScheduledTask> inside = new AtomicReference<>();
            ScheduledTask returned = Bukkit.getGlobalRegionScheduler().runDelayed(p, t -> {
                on.set(Thread.currentThread());
                inside.set(t);
            }, 2);
            assertSame(p, returned.getOwningPlugin());
            assertFalse(returned.isRepeatingTask());
            assertEquals(ScheduledTask.ExecutionState.IDLE, returned.getExecutionState());
            server.tick();
            assertNull(on.get(), "지연 2틱 — 아직");
            server.tick();
            assertSame(global, on.get());
            assertSame(returned, inside.get(), "작업 안에서 받은 손잡이와 돌려받은 손잡이가 같다");
            assertEquals(ScheduledTask.ExecutionState.FINISHED, returned.getExecutionState());
            assertEquals(ScheduledTask.CancelledState.ALREADY_EXECUTED, returned.cancel());
            assertThrows(IllegalArgumentException.class, () -> Bukkit.getGlobalRegionScheduler().runDelayed(p, t -> {}, 0));
            AtomicInteger runs = new AtomicInteger();
            Bukkit.getGlobalRegionScheduler().execute(p, runs::incrementAndGet);
            ScheduledTask rep = Bukkit.getGlobalRegionScheduler().runAtFixedRate(p, t -> runs.incrementAndGet(), 1, 1);
            assertTrue(rep.isRepeatingTask());
            server.tick(3);
            assertEquals(4, runs.get());
            assertEquals(ScheduledTask.CancelledState.CANCELLED_BY_CALLER, rep.cancel());
            assertTrue(rep.isCancelled());
            assertEquals(ScheduledTask.CancelledState.CANCELLED_ALREADY, rep.cancel());
            server.tick(2);
            assertEquals(4, runs.get());
        }
    }

    @Test
    void regionSchedulerRunsForTheChunkByLocationAndByWorldCoordinates() throws Exception {
        try (FurnaceTestServer server = start("Sched")) {
            Plugin p = WiringProbe.plugin;
            var world = Bukkit.getWorlds().get(0);
            List<String> log = new CopyOnWriteArrayList<>();
            Bukkit.getRegionScheduler().run(p, world, 0, 0, t -> log.add("chunk"));
            Bukkit.getRegionScheduler().run(p, new Location(world, 40, 70, 40), t -> log.add("loc"));
            Bukkit.getRegionScheduler().execute(p, new Location(world, 1, 70, 1), () -> log.add("exec"));
            server.tick(2);
            assertEquals(3, log.size(), log.toString());
            assertThrows(IllegalArgumentException.class, () -> Bukkit.getRegionScheduler().runDelayed(p, world, 0, 0, t -> {}, 0));
            assertThrows(IllegalArgumentException.class, () -> Bukkit.getRegionScheduler().run(p, (org.bukkit.World) null, 0, 0, t -> {}));
        }
    }

    @Test
    void asyncSchedulerRunsOnTheFoliaStylePoolOffTheTickThread() throws Exception {
        try (FurnaceTestServer server = start("Sched")) {
            Plugin p = WiringProbe.plugin;
            CountDownLatch now = new CountDownLatch(1), delayed = new CountDownLatch(1), rate = new CountDownLatch(3);
            AtomicReference<String> name = new AtomicReference<>();
            AtomicReference<Boolean> primary = new AtomicReference<>();
            Bukkit.getAsyncScheduler().runNow(p, t -> {
                name.set(Thread.currentThread().getName());
                primary.set(Bukkit.isPrimaryThread());
                now.countDown();
            });
            assertTrue(now.await(5, TimeUnit.SECONDS), "틱을 내지 않아도 돈다");
            assertTrue(name.get().startsWith("Folia Async Scheduler Thread #"), name.get());
            assertEquals(Boolean.FALSE, primary.get());
            Bukkit.getAsyncScheduler().runDelayed(p, t -> delayed.countDown(), 20, TimeUnit.MILLISECONDS);
            assertTrue(delayed.await(5, TimeUnit.SECONDS));
            ScheduledTask rep = Bukkit.getAsyncScheduler().runAtFixedRate(p, t -> rate.countDown(), 0, 10, TimeUnit.MILLISECONDS);
            assertTrue(rate.await(5, TimeUnit.SECONDS));
            assertTrue(rep.isRepeatingTask());
            Bukkit.getAsyncScheduler().cancelTasks(p);
            assertTrue(rep.isCancelled());
            assertThrows(IllegalArgumentException.class, () -> Bukkit.getAsyncScheduler().runDelayed(p, t -> {}, -1, TimeUnit.SECONDS));
        }
    }

    @Test
    void entitySchedulerFollowsTheEntityAndRetiresOnDisconnect() throws Exception {
        try (FurnaceTestServer server = start("Sched")) {
            Plugin p = WiringProbe.plugin;
            var steve = server.join("Steve");
            server.tick(10);
            org.bukkit.entity.Player player = Bukkit.getPlayerExact("Steve");
            assertNotNull(player);
            AtomicInteger runs = new AtomicInteger(), retired = new AtomicInteger();
            ScheduledTask task = player.getScheduler().runAtFixedRate(p, t -> runs.incrementAndGet(), retired::incrementAndGet, 1, 1);
            assertNotNull(task);
            assertSame(p, task.getOwningPlugin());
            server.tick(3);
            assertEquals(3, runs.get());
            steve.close();
            server.tick(2);
            assertEquals(1, retired.get());
            assertTrue(task.isCancelled());
            assertNull(player.getScheduler().run(p, t -> {}, null), "사라진 엔티티는 예약하지 않는다");
            assertFalse(player.getScheduler().execute(p, () -> {}, null, 1));
        }
    }

    @Test
    void disabledPluginCannotScheduleAndItsTasksAreCancelled() throws Exception {
        try (FurnaceTestServer server = start("Sched")) {
            Plugin p = WiringProbe.plugin;
            AtomicInteger runs = new AtomicInteger();
            ScheduledTask rep = Bukkit.getGlobalRegionScheduler().runAtFixedRate(p, t -> runs.incrementAndGet(), 1, 1);
            server.tick();
            assertEquals(1, runs.get());
            Bukkit.getPluginManager().disablePlugin(p);
            server.tick(3);
            assertEquals(1, runs.get(), "꺼진 뒤에는 돌지 않는다");
            assertTrue(rep.isCancelled() || rep.getExecutionState() == ScheduledTask.ExecutionState.IDLE);
            assertThrows(IllegalPluginAccessException.class, () -> Bukkit.getGlobalRegionScheduler().run(p, t -> {}));
            assertThrows(IllegalPluginAccessException.class, () -> Bukkit.getAsyncScheduler().runNow(p, t -> {}));
            assertThrows(IllegalPluginAccessException.class, () -> Bukkit.getRegionScheduler().execute(p, Bukkit.getWorlds().get(0), 0, 0, () -> {}));
        }
    }

    // ---- 예약 이름(Paper SpigotPluginProviderFactory.build)

    private boolean loaded(String name) throws Exception {
        try (FurnaceTestServer server = start(name)) {
            return WiringProbe.plugin != null;
        }
    }

    @Test
    void reservedAndSpacedPluginNamesAreRejected() throws Exception {
        assertTrue(loaded("Sched"));
        for (String bad : List.of("paper", "Spigot", "BUKKIT", "Minecraft", "mojang")) {
            WiringProbe.reset();
            assertFalse(loaded(bad), bad + " 는 예약 이름이라 불러오지 않는다");
        }
        WiringProbe.reset();
        assertFalse(loaded("My Plugin"), "공백 든 이름은 Spigot 형식에서 거부");
        WiringProbe.reset();
        assertTrue(loaded("PaperLike"), "예약어를 포함할 뿐인 이름은 된다");
    }
}
