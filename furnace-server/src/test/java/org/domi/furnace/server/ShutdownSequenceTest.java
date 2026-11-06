// 종료 순서 계약: 단계는 선언 순서로만 돌고, 한 단계가 실패해도 다음 단계가 돌며, 두 번 돌지 않는다.
// 실제 서버 조각(ServerCore·PluginHost·스케줄러)으로: ServerShutdownEvent → onDisable(그 안에서 region 작업을 맡기고 기다릴 수 있다)
// → 스케줄러 정지 순서를 확인한다(Folia #353 — region 이 플러그인보다 먼저 꺼지는 문제가 없어야 한다).
package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.server.ServerShutdownEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginContext;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShutdownSequenceTest {

    @TempDir Path tmp;

    @Test
    void stepsRunInDeclaredOrderOnceAndSurviveFailures() {
        List<String> log = new CopyOnWriteArrayList<>();
        ShutdownSequence s = new ShutdownSequence();
        // 일부러 거꾸로 등록 — 실행 순서는 등록 순서가 아니라 Step 순서다
        s.on(ShutdownSequence.Step.CLOSE_PLUGINS, () -> log.add("close-plugins"));
        s.on(ShutdownSequence.Step.STOP_SCHEDULER, () -> log.add("scheduler"));
        s.on(ShutdownSequence.Step.FLUSH_WORLDS, () -> { log.add("flush"); throw new IllegalStateException("저장 실패 흉내"); });
        s.on(ShutdownSequence.Step.STOP_NETWORK, () -> log.add("network"));
        s.on(ShutdownSequence.Step.DISABLE_PLUGINS, () -> log.add("disable"));
        s.on(ShutdownSequence.Step.SHUTDOWN_EVENT, () -> log.add("event"));
        s.on(ShutdownSequence.Step.SHUTDOWN_EVENT, () -> log.add("event2"));   // 같은 단계는 이어 붙인다
        s.run();
        s.run();   // 두 번째는 아무것도 안 한다
        assertEquals(List.of("event", "event2", "disable", "network", "flush", "scheduler", "close-plugins"), log);
    }

    @Test
    void stepOrderIsTheDocumentedContract() {
        // 순서를 바꾸면 docs/api/lifecycle.md 와 ServerShutdownEvent 주석도 같이 고쳐야 한다
        assertEquals(List.of("SHUTDOWN_EVENT", "DISABLE_PLUGINS", "CLOSE_REMOTE_SERVICES", "STOP_NETWORK", "FLUSH_WORLDS",
                        "CLOSE_STORAGE", "STOP_SCHEDULER", "CLOSE_PLUGINS"),
                java.util.Arrays.stream(ShutdownSequence.Step.values()).map(Enum::name).toList());
    }

    /** onDisable 에서 region 작업을 맡기고 결과를 기다린다(종료 때 월드 정리하는 플러그인). */
    public static final class CleanupPlugin extends FurnacePlugin {
        final List<String> log = new CopyOnWriteArrayList<>();

        @Override
        public void onDisable() {
            log.add("onDisable stopping=" + server().isStopping());
            try {
                String where = server().scheduler().region(new RegionPos(0, 0)).submit(() -> Thread.currentThread().getName())
                        .get(10, TimeUnit.SECONDS);
                log.add("region-task-ran");
                if (where == null) log.add("?");
            } catch (Exception e) {
                log.add("region-task-failed " + e);
            }
        }
    }

    @Test
    void pluginsDisableWhileRegionsStillRun() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ServerCore core = TestServers.core(tmp, sched);
        core.world().startTicking();
        var host = core.plugins();
        CleanupPlugin plugin = new CleanupPlugin();
        plugin.init(new PluginContext() {
            final PluginDescription d = new PluginDescription("cleanup", "1", CleanupPlugin.class.getName(), "", List.of(), null, null,
                    List.of(), List.of(), List.of(), null, java.util.Map.of(), List.of(), null);
            public PluginDescription description() { return d; }
            public org.domi.furnace.api.Furnace server() { return host; }
            public java.util.logging.Logger logger() { return java.util.logging.Logger.getLogger("cleanup"); }
            public Path configFolder() { return tmp.resolve("cleanup"); }
            public Path dataFolder() { return tmp.resolve("cleanup"); }
            public Path file() { return tmp.resolve("cleanup.jar"); }
            public ClassLoader classLoader() { return getClass().getClassLoader(); }
            public org.domi.furnace.api.registry.PluginRegistries registries() { return null; }
        });
        plugin.setEnabled(true);
        List<String> log = plugin.log;
        host.eventsImpl().subscribe(plugin, ServerShutdownEvent.class, e -> log.add("ServerShutdownEvent stopping=" + host.isStopping()));
        assertFalse(host.isStopping());
        try {
            new ShutdownSequence()
                    .on(ShutdownSequence.Step.SHUTDOWN_EVENT, host::beginShutdown)
                    .on(ShutdownSequence.Step.DISABLE_PLUGINS, () -> plugin.setEnabled(false))
                    .on(ShutdownSequence.Step.STOP_SCHEDULER, () -> { log.add("scheduler-close"); sched.close(); })
                    .on(ShutdownSequence.Step.CLOSE_PLUGINS, host::close)
                    .run();
        } finally {
            sched.close();
            TestServers.drainIoPools();
        }
        assertEquals(List.of("ServerShutdownEvent stopping=true", "onDisable stopping=true", "region-task-ran", "scheduler-close"), log);
        assertTrue(host.isStopping());
        host.beginShutdown();   // 두 번째 호출은 이벤트를 다시 부르지 않는다
        assertEquals(1, log.stream().filter(s -> s.startsWith("ServerShutdownEvent")).count());
    }
}
