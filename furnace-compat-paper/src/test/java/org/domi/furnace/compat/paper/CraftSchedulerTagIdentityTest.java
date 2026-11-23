// T-264 P2b: 스케줄러(대기 목록 포함)·태그 객체의 실제 클래스가 Paper 관측표(craft-identity-paper-1.21.1.tsv)와 같은지 실제 Bukkit jar 로 확인한다.
package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import org.bukkit.Bukkit;
import org.bukkit.GameEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CraftSchedulerTagIdentityTest {

    private static final String YML = """
            name: Probe
            version: '1'
            main: probe.ProbePlugin
            api-version: '1.21'
            """;

    private static final String MAIN = """
            package probe;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class ProbePlugin extends JavaPlugin {
                @Override public void onEnable() { WiringProbe.plugin = this; }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    /** Paper 관측표: 키 → 클래스 이름. */
    private static Map<String, String> paper() throws Exception {
        Map<String, String> out = new HashMap<>();
        try (var in = CraftSchedulerTagIdentityTest.class.getResourceAsStream("/craft-identity-paper-1.21.1.tsv")) {
            for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] t = line.split("\t");
                out.put(t[0], t[1]);
            }
        }
        return out;
    }

    @Test
    void schedulerAndTaskClassesMatchPaper() throws Exception {
        Map<String, String> paper = paper();
        Path jar = JarCompiler.jar(tmp.resolve("probe.jar"), YML, Map.of("probe.ProbePlugin", MAIN));
        try (FurnaceTestServer server = FurnaceTestServer.builder().pluginJar(jar).start()) {
            Plugin p = WiringProbe.plugin;
            var s = Bukkit.getScheduler();
            assertEquals(paper.get("server.getScheduler"), s.getClass().getName());
            BukkitTask task = s.runTask(p, () -> {});
            BukkitTask later = s.runTaskLater(p, () -> {}, 100);
            BukkitTask timer = s.runTaskTimer(p, () -> {}, 100, 100);
            BukkitTask async = s.runTaskLaterAsynchronously(p, () -> {}, 100);
            Future<String> call = s.callSyncMethod(p, () -> "x");
            assertEquals(paper.get("scheduler.task"), task.getClass().getName());
            assertEquals(paper.get("scheduler.taskLater"), later.getClass().getName());
            assertEquals(paper.get("scheduler.taskTimer"), timer.getClass().getName());
            assertEquals(paper.get("scheduler.async"), async.getClass().getName());
            assertEquals(paper.get("scheduler.callSync"), call.getClass().getName());
            var pending = new java.util.HashSet<String>();
            for (BukkitTask t : s.getPendingTasks()) pending.add(t.getClass().getName());
            for (String k : paper.keySet()) {
                if (k.startsWith("scheduler.pending.")) assertTrue(pending.contains(paper.get(k)), k + " ← " + pending);
            }
            // Paper 처럼 작업 하나에 손잡이 하나 — 대기 목록의 객체가 예약 때 받은 그 객체다
            assertTrue(s.getPendingTasks().stream().anyMatch(x -> x == task), "같은 CraftTask");
            assertTrue(s.getPendingTasks().stream().anyMatch(x -> x == call), "같은 CraftFuture");
            s.cancelTasks(p);

            // PR 584 검토: callSyncMethod 를 넣는 틈에 대기 목록을 보아도 손잡이는 처음부터 Future 다(보통 작업으로 굳으면 플러그인의 형 변환이 깨진다)
            var stop = new java.util.concurrent.atomic.AtomicBoolean();
            var wrong = new java.util.concurrent.ConcurrentLinkedQueue<String>();
            Thread watcher = new Thread(() -> {
                while (!stop.get()) {
                    for (BukkitTask t : s.getPendingTasks()) {
                        if (t.getOwner() == p && !(t instanceof Future<?>)) wrong.add(t.getClass().getName());
                    }
                }
            });
            watcher.start();
            try {
                for (int i = 0; i < 2000; i++) s.callSyncMethod(p, () -> "y");
            } finally {
                stop.set(true);
                watcher.join();
            }
            s.cancelTasks(p);
            assertTrue(wrong.isEmpty(), "callSyncMethod 손잡이가 보통 작업으로 보였다: " + wrong);
        }
    }

    @Test
    void tagClassesMatchPaper() throws Exception {
        Map<String, String> paper = paper();
        try (FurnaceTestServer server = FurnaceTestServer.builder().start()) {
            assertEquals(paper.get("tag.blocks"), Tag.WOOL.getClass().getName());
            assertEquals(paper.get("tag.items"), Tag.ITEMS_SWORDS.getClass().getName());
            assertEquals(paper.get("tag.fluids"), Bukkit.getTag(Tag.REGISTRY_FLUIDS, NamespacedKey.minecraft("water"), org.bukkit.Fluid.class).getClass().getName());
            assertEquals(paper.get("tag.entities"),
                    Bukkit.getTag(Tag.REGISTRY_ENTITY_TYPES, NamespacedKey.minecraft("skeletons"), org.bukkit.entity.EntityType.class).getClass().getName());
            // 관측표에 없는 게임 이벤트 태그: Paper 1.21.1 서명표의 io.papermc.paper.CraftGameEventTag
            assertEquals("io.papermc.paper.CraftGameEventTag",
                    Bukkit.getTag(Tag.REGISTRY_GAME_EVENTS, NamespacedKey.minecraft("vibrations"), GameEvent.class).getClass().getName());
        }
    }
}
