// Paper 1.21.1 CraftAsyncScheduler 쪽 동작을 실제 Bukkit jar 로 확인한다: 이름 붙은 플랫폼 풀, 종료 때 최대 5초 기다리기, getMainThreadExecutor.
// 시험 jar 는 JarCompiler 로 그 자리에서 컴파일한다(JavaPluginWiringTest 와 같다).
package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AsyncSchedulerShutdownTest {

    private static final String YML = """
            name: Slow
            version: '1'
            main: slow.SlowPlugin
            api-version: '1.21'
            authors: [Ann]
            """;

    /** 켜질 때 비동기 작업 하나를 건다: sleepMillis 동안 자고, 끝나면 칠판에 적는다. 비동기 안에서 보이는 스레드 이름도 적는다. */
    private static String plugin(long sleepMillis) {
        return """
                package slow;
                import org.bukkit.plugin.java.JavaPlugin;
                import org.domi.furnace.compat.paper.fixtures.WiringProbe;
                public final class SlowPlugin extends JavaPlugin {
                    @Override public void onEnable() {
                        WiringProbe.LOG.add("enable");
                        WiringProbe.plugin = this;
                        getServer().getScheduler().runTaskAsynchronously(this, () -> {
                            WiringProbe.LOG.add("started|" + Thread.currentThread().getName() + "|virtual=" + Thread.currentThread().isVirtual());
                            try { Thread.sleep(%d); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                            WiringProbe.LOG.add("done");
                        });
                    }
                    @Override public void onDisable() { WiringProbe.LOG.add("disable"); }
                }
                """.formatted(sleepMillis);
    }

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    private FurnaceTestServer start(long sleepMillis) throws Exception { return start(sleepMillis, false); }

    private FurnaceTestServer start(long sleepMillis, boolean devMode) throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("slow.jar"), YML, Map.of("slow.SlowPlugin", plugin(sleepMillis)));
        return FurnaceTestServer.builder().failFast(false).devMode(devMode).pluginJar(jar).start();
    }

    private static void awaitLog(String prefix) throws InterruptedException {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (WiringProbe.LOG.stream().noneMatch(l -> l.startsWith(prefix)) && System.nanoTime() < until) Thread.sleep(10);
        assertTrue(WiringProbe.LOG.stream().anyMatch(l -> l.startsWith(prefix)), prefix + " ← " + WiringProbe.LOG);
    }

    @Test
    void bukkitAsyncTaskRunsOnANamedPlatformThreadOfThePool() throws Exception {
        try (FurnaceTestServer server = start(0)) {
            server.tick();
            awaitLog("started");
            String line = WiringProbe.LOG.stream().filter(l -> l.startsWith("started")).findFirst().orElseThrow();
            assertTrue(line.matches("started\\|Craft Scheduler Thread - \\d+ - Slow\\|virtual=false"), line);
        }
    }

    @Test
    void shutdownWaitsForARunningAsyncTaskInsteadOfCuttingItOff() throws Exception {
        FurnaceTestServer server = start(700);
        server.tick();
        awaitLog("started");
        assertFalse(WiringProbe.LOG.contains("done"));
        server.close();   // onDisable → 비동기 작업 취소 → 남은 일꾼을 기다린 뒤 닫는다
        assertTrue(WiringProbe.LOG.contains("disable"));
        assertTrue(WiringProbe.LOG.contains("done"), "끝날 때까지 기다린 뒤 종료가 이어졌다: " + WiringProbe.LOG);
        assertTrue(WiringProbe.LOG.indexOf("disable") < WiringProbe.LOG.indexOf("done"));
        assertNull(WiringProbe.failure);
    }

    @Test
    void mainThreadExecutorRunsOnTheNextTickOnThePrimaryThread() throws Exception {
        try (FurnaceTestServer server = start(0)) {
            var plugin = WiringProbe.plugin;
            var ex = org.bukkit.Bukkit.getScheduler().getMainThreadExecutor(plugin);
            java.util.concurrent.atomic.AtomicReference<Boolean> primary = new java.util.concurrent.atomic.AtomicReference<>();
            ex.execute(() -> primary.set(org.bukkit.Bukkit.isPrimaryThread()));
            assertNull(primary.get(), "틱 전에는 돌지 않는다");
            server.tick();
            assertEquals(Boolean.TRUE, primary.get());
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ex.execute(null));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> org.bukkit.Bukkit.getScheduler().getMainThreadExecutor(null));
        }
    }

    @Test
    void shutdownDoesNotWaitWhenNothingIsRunning() throws Exception {
        FurnaceTestServer server = start(0);
        server.tick();
        awaitLog("done");
        long t0 = System.nanoTime();
        server.close();
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertTrue(ms < 4_000, "일꾼이 없으면 기다리지 않는다: " + ms + "ms");
        assertEquals(1, WiringProbe.LOG.stream().filter("done"::equals).count());
    }

    /** 개발 모드 핫 리로드도 종료와 같은 규칙: 끈 플러그인의 비동기 작업이 끝난 뒤에 새 것이 켜진다(옛 작업이 새 클래스·자원과 겹치지 않는다). */
    @Test
    void hotReloadWaitsForTheDisabledPluginsAsyncTaskBeforeEnablingTheNewOne() throws Exception {
        try (FurnaceTestServer server = start(700, true)) {
            server.tick();
            awaitLog("started");
            assertFalse(WiringProbe.LOG.contains("done"));
            var reload = server.api().plugins().reload("Slow");
            for (int i = 0; i < 50 && !reload.isDone(); i++) server.tick();
            assertEquals(java.util.List.of("Slow"), reload.get(10, TimeUnit.SECONDS));
            var log = WiringProbe.LOG;
            assertTrue(log.contains("done"), "리로드가 끝났을 때 옛 작업은 이미 끝났다: " + log);
            assertTrue(log.indexOf("disable") < log.indexOf("done"), log.toString());
            assertTrue(log.indexOf("done") < log.lastIndexOf("enable"), "새 플러그인은 옛 작업이 끝난 뒤에 켜진다: " + log);
            assertEquals(2, log.stream().filter("enable"::equals).count());
        }
    }

    @Test
    void hotReloadDoesNotWaitWhenTheDisabledPluginHasNoAsyncWorker() throws Exception {
        try (FurnaceTestServer server = start(0, true)) {
            server.tick();
            awaitLog("done");
            long t0 = System.nanoTime();
            var reload = server.api().plugins().reload("Slow");
            for (int i = 0; i < 50 && !reload.isDone(); i++) server.tick();
            assertEquals(java.util.List.of("Slow"), reload.get(10, TimeUnit.SECONDS));
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            assertTrue(ms < 4_000, "일꾼이 없으면 기다리지 않는다: " + ms + "ms");
        }
    }
}
