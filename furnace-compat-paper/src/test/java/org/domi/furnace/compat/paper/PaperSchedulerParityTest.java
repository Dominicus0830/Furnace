// PR #208 독립 검토 재현을 회귀 시험으로: Paper 1.21.1 Folia 스케줄러 구현(FoliaAsyncScheduler·FoliaGlobalRegionScheduler·FoliaEntityScheduler·EntityScheduler.schedule)과 같은 동작.
// 각 시험은 Paper 동작을 기대값으로 적었다.
package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperSchedulerParityTest {

    private static final String YML = "name: Sched\nversion: '1'\nmain: sched.SchedPlugin\napi-version: '1.21'\n";

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

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("sched.jar"), YML, Map.of("sched.SchedPlugin", SRC));
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    /** 접속한 플레이어의 엔티티 작업이 실제로 한 번 돌 때까지 틱을 낸다(다른 시험과 섞여 돌 때 region 에 오르는 데 걸리는 틱 수가 달라 흔들렸다 — 검토 R5). */
    private static void joinAndWarm(FurnaceTestServer server) {
        server.join("Steve");
        var player = Bukkit.getPlayerExact("Steve");
        AtomicInteger warm = new AtomicInteger();
        ScheduledTask w = player.getScheduler().runAtFixedRate(WiringProbe.plugin, t -> warm.incrementAndGet(), null, 1, 1);
        for (int i = 0; i < 200 && warm.get() == 0; i++) server.tick();
        assertTrue(warm.get() > 0, "플레이어의 엔티티 작업이 돌지 않는다");
        w.cancel();
    }

    /** Paper FoliaAsyncScheduler.cancelTasks 는 자기 tasks 집합만 돈다 — 전역 작업은 그대로. */
    @Test
    void asyncCancelTasksDoesNotCancelGlobalTasks() throws Exception {
        try (FurnaceTestServer server = start()) {
            Plugin p = WiringProbe.plugin;
            AtomicInteger runs = new AtomicInteger();
            ScheduledTask global = Bukkit.getGlobalRegionScheduler().runAtFixedRate(p, t -> runs.incrementAndGet(), 1, 1);
            Bukkit.getAsyncScheduler().cancelTasks(p);
            server.tick(3);
            assertFalse(global.isCancelled(), "비동기 cancelTasks 가 전역 작업까지 취소했다");
            assertEquals(3, runs.get());
        }
    }

    /** 반대로 FoliaGlobalRegionScheduler.cancelTasks 는 tasksByDeadline 만 — 비동기 반복 작업은 그대로. */
    @Test
    void globalCancelTasksDoesNotCancelAsyncTasks() throws Exception {
        try (FurnaceTestServer server = start()) {
            Plugin p = WiringProbe.plugin;
            CountDownLatch ran = new CountDownLatch(3);
            ScheduledTask async = Bukkit.getAsyncScheduler().runAtFixedRate(p, t -> ran.countDown(), 50, 10, TimeUnit.MILLISECONDS);
            Bukkit.getGlobalRegionScheduler().cancelTasks(p);
            assertFalse(async.isCancelled(), "전역 cancelTasks 가 비동기 작업까지 취소했다");
            assertTrue(ran.await(3, TimeUnit.SECONDS));
            async.cancel();
        }
    }

    /** 엔티티 작업도: Paper 는 두 cancelTasks 어느 쪽도 엔티티 작업을 건드리지 않는다. */
    @Test
    void globalCancelTasksDoesNotCancelEntityTasks() throws Exception {
        try (FurnaceTestServer server = start()) {
            Plugin p = WiringProbe.plugin;
            joinAndWarm(server);
            var player = Bukkit.getPlayerExact("Steve");
            AtomicInteger runs = new AtomicInteger();
            ScheduledTask t = player.getScheduler().runAtFixedRate(p, x -> runs.incrementAndGet(), null, 1, 1);
            Bukkit.getGlobalRegionScheduler().cancelTasks(p);
            server.tick(3);
            assertFalse(t.isCancelled(), "전역 cancelTasks 가 엔티티 작업까지 취소했다");
        }
    }

    /** Paper EntityScheduler.schedule: "Any value less-than 1 is treated as 1" — execute(…, 0) 은 예외 없이 다음 틱. */
    @Test
    void entityExecuteWithZeroDelayRunsNextTick() throws Exception {
        try (FurnaceTestServer server = start()) {
            Plugin p = WiringProbe.plugin;
            joinAndWarm(server);
            var player = Bukkit.getPlayerExact("Steve");
            AtomicInteger runs = new AtomicInteger();
            boolean ok;
            try {
                ok = player.getScheduler().execute(p, runs::incrementAndGet, null, 0);
            } catch (IllegalArgumentException e) {
                throw new AssertionError("Paper 는 delay 0 을 1 로 본다: " + e.getMessage(), e);
            }
            assertTrue(ok);
            server.tick(2);
            assertEquals(1, runs.get());
        }
    }

    /** Paper FoliaEntityScheduler.execute 는 등록 때 isEnabled 를 보지 않는다(실행 때 건너뜀) — 꺼진 플러그인도 예외 없이 true. */
    @Test
    void entityExecuteOnDisabledPluginDoesNotThrow() throws Exception {
        try (FurnaceTestServer server = start()) {
            Plugin p = WiringProbe.plugin;
            joinAndWarm(server);
            var player = Bukkit.getPlayerExact("Steve");
            Bukkit.getPluginManager().disablePlugin(p);
            AtomicInteger runs = new AtomicInteger();
            boolean ok;
            try {
                ok = player.getScheduler().execute(p, runs::incrementAndGet, null, 1);
            } catch (RuntimeException e) {
                throw new AssertionError("Paper 는 여기서 던지지 않는다: " + e, e);
            }
            assertTrue(ok);
            server.tick(3);
            assertEquals(0, runs.get());
        }
    }

    /**
     * Paper AsyncScheduledTask.run: 끝난 뒤 delay = max(0, target + period - now), target = now + delay — 늦으면 한 번 바로 돌고 그 시각부터 다시 센다(몰아 돌지 않는다).
     * 첫 회차가 400ms 걸리면(주기 50ms) Paper 는 끝난 직후 1회만 바로 돈다.
     */
    @Test
    void asyncFixedRateDoesNotBurstAfterSlowRun() throws Exception {
        try (FurnaceTestServer server = start()) {
            Plugin p = WiringProbe.plugin;
            List<Long> starts = new CopyOnWriteArrayList<>();
            AtomicInteger n = new AtomicInteger();
            ScheduledTask t = Bukkit.getAsyncScheduler().runAtFixedRate(p, x -> {
                starts.add(System.nanoTime());
                if (n.getAndIncrement() == 0) {
                    try { Thread.sleep(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }, 0, 50, TimeUnit.MILLISECONDS);
            Thread.sleep(700);
            t.cancel();
            long firstEnd = starts.get(0) + TimeUnit.MILLISECONDS.toNanos(400);
            long burst = starts.stream().skip(1).filter(s -> s - firstEnd < TimeUnit.MILLISECONDS.toNanos(30)).count();
            assertEquals(1, burst, "느린 회차 뒤 30ms 안에 돈 회차 수(Paper 는 1): " + starts.size() + "회");
        }
    }

    /** Paper CraftEntity.getScheduler 는 엔티티마다 한 객체(apiScheduler 필드). */
    @Test
    void entitySchedulerIsOneObjectPerEntity() throws Exception {
        try (FurnaceTestServer server = start()) {
            joinAndWarm(server);
            var player = Bukkit.getPlayerExact("Steve");
            assertSame(player.getScheduler(), player.getScheduler());
        }
    }
}
