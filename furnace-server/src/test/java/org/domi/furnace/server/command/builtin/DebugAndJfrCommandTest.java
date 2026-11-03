// /debug start·stop(틱 프로파일: 다음 틱 머리에서 시작, 멈추면 시간·틱 수·초당 틱 보고)와 /jfr start·stop(JFR 녹화 → debug/*.jfr).
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.profiling.JfrProfiler;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebugAndJfrCommandTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    EmbeddedChannel ch;
    CommandSource op;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        ch = new ConcurrentEmbeddedChannel();
        op = CommandSource.player(core, new Player(7, UUID.randomUUID(), "Op", ch)).withPermission(4);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private int run(CommandSource src, String cmd) { return core.commands().performPrefixedCommandAsync(src, cmd).join().result(); }

    private boolean ok(CommandSource src, String cmd) { return core.commands().performPrefixedCommandAsync(src, cmd).join().success(); }

    /** 나간 패킷 본문(ISO-8859-1)을 모두 이어서 돌려준다. */
    private String sent() {
        ch.runPendingTasks();
        StringBuilder sb = new StringBuilder();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            if (o instanceof ByteBuf bb) {
                sb.append(bb.toString(StandardCharsets.ISO_8859_1)).append('\n');
                bb.release();
            }
        }
        return sb.toString();
    }

    @Test
    void debugStartStopCountsTicksFromTheNextTick() {
        assertEquals(0, run(op, "debug start"));
        assertTrue(sent().contains("commands.debug.started"));
        assertTrue(core.isTickProfiling());
        // 이미 요청됨 → 실패 메시지
        assertFalse(ok(op, "debug start"));
        assertTrue(sent().contains("commands.debug.alreadyRunning"));
        // 아직 틱이 시작 전이면 0 초·0 틱, 초당 틱은 NaN(0/0.0)이고 반환 0. 요청은 그대로 남는다(바닐라 bk()가 E==null 이면 F 를 지우지 않는다)
        assertEquals(0, run(op, "debug stop"));
        String early = sent();
        assertTrue(early.contains("commands.debug.stopped"), early);
        assertTrue(early.contains("0.00") && early.contains("NaN"), early);
        assertTrue(core.isTickProfiling(), "시작 전에 멈춰도 요청은 남는다");
        // 틱을 돌리면 첫 틱 머리에서 시작해 센다
        for (int i = 0; i < 3; i++) core.scheduledTick();
        int tps = run(op, "debug stop");
        assertTrue(tps > 0, "초당 틱 " + tps);
        String stopped = sent();
        assertTrue(stopped.contains("commands.debug.stopped"), stopped);
        assertFalse(core.isTickProfiling());
        // 멈춘 뒤 다시 멈추면 실패
        assertFalse(ok(op, "debug stop"));
        assertTrue(sent().contains("commands.debug.notRunning"));
    }

    @Test
    void debugCountsExactlyTheTicksBetweenStartAndStop() {
        run(op, "debug start");
        core.scheduledTick();   // 시작 틱 머리에서 시작(틱 번호를 올리기 전)
        core.scheduledTick();
        core.scheduledTick();
        long[] r = core.stopTickProfile();
        assertEquals(3L, r[1]);
        assertTrue(r[0] > 0);
        assertFalse(core.isTickProfiling());
    }

    @Test
    void debugRequiresPermissionThree() {
        assertFalse(ok(op.withPermission(2), "debug start"));
        assertTrue(ok(op.withPermission(3), "debug start"));
        assertTrue(ok(op.withPermission(3), "debug stop"));
        // 없는 함수는 인자 단계에서 실패한다(추적 파일을 만들지 않는다) — /debug function 본체는 DebugFunctionTest
        assertFalse(ok(op, "debug function foo:bar"));
        assertTrue(sent().contains("arguments.function.unknown"));
    }

    @Test
    void jfrRecordsToDebugDirectory() throws IOException {
        if (!JfrProfiler.isAvailable()) {
            // JFR 을 못 쓰는 JVM 이면 바닐라처럼 등록하지 않는다
            assertFalse(ok(op, "jfr start"));
            return;
        }
        Path dir = Path.of("debug");
        boolean hadDir = Files.exists(dir);
        java.util.Set<Path> before = hadDir ? listJfr(dir) : java.util.Set.of();
        try {
            // 녹화 중이 아닐 때 stop: 바닐라 IllegalStateException 메시지가 dump.failed 의 인자
            assertFalse(ok(op, "jfr stop"));
            String notRunning = sent();
            assertTrue(notRunning.contains("commands.jfr.dump.failed") && notRunning.contains("Not currently profiling"), notRunning);
            assertEquals(1, run(op, "jfr start"));
            assertTrue(sent().contains("commands.jfr.started"));
            assertTrue(JfrProfiler.instance().isRunning());
            // 이미 녹화 중이면 start.failed
            assertFalse(ok(op, "jfr start"));
            assertTrue(sent().contains("commands.jfr.start.failed"));
            assertEquals(1, run(op, "jfr stop"));
            String stopped = sent();
            assertTrue(stopped.contains("commands.jfr.stopped"), stopped);
            assertTrue(stopped.contains("debug/server-") && stopped.contains(".jfr"), "상대 경로: " + stopped);
            assertTrue(stopped.contains("open_file"), "클릭 동작이 파일 열기: " + stopped);
            assertFalse(JfrProfiler.instance().isRunning());
            java.util.Set<Path> created = new java.util.HashSet<>(listJfr(dir));
            created.removeAll(before);
            assertEquals(1, created.size(), "녹화 파일 하나가 debug/ 에 생긴다");
            assertTrue(Files.size(created.iterator().next()) > 0);
            // 권한 4
            assertFalse(ok(op.withPermission(3), "jfr start"));
        } finally {
            if (JfrProfiler.instance().isRunning()) JfrProfiler.instance().stop();
            if (Files.exists(dir)) {
                java.util.Set<Path> now = listJfr(dir);
                for (Path f : now) if (!before.contains(f)) Files.deleteIfExists(f);
                if (!hadDir) {
                    try (Stream<Path> rest = Files.list(dir)) {
                        if (rest.findAny().isEmpty()) Files.deleteIfExists(dir);
                    }
                }
            }
        }
    }

    private static java.util.Set<Path> listJfr(Path dir) throws IOException {
        if (!Files.exists(dir)) return java.util.Set.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".jfr")).collect(java.util.stream.Collectors.toSet());
        }
    }
}
