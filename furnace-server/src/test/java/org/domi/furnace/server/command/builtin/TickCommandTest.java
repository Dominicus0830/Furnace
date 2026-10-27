// /tick: 속도·정지·step·sprint 상태와 전역 틱(시간) 반영, 권한, 틱 시간 표본(query 백분위).
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.TickState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TickCommandTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    CommandSource src;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        src = CommandSource.console(core);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private int run(String cmd) throws CommandSyntaxException { return core.commands().performPrefixedCommandAsync(src, cmd).join().result(); }

    @Test
    void rateChangesTickPeriodAndGlobalTickCount() throws Exception {
        TickState ts = core.tickState();
        assertEquals(20.0f, ts.rate());
        assertEquals(50_000_000L, ts.nanosPerTick());
        long t0 = core.clock().gameTime();
        core.scheduledTick();
        assertEquals(t0 + 1, core.clock().gameTime(), "20 TPS 는 반복마다 한 틱");
        assertEquals(40, run("tick rate 40"));
        assertEquals(25_000_000L, ts.nanosPerTick());
        assertEquals(25_000_000L, core.world().tickNanos(), "region 틱 주기도 바뀐다");
        long t1 = core.clock().gameTime();
        core.scheduledTick();
        assertEquals(t1 + 2, core.clock().gameTime(), "40 TPS 는 반복마다 두 틱");
        assertEquals(10, run("tick rate 10"));
        long t2 = core.clock().gameTime();
        core.scheduledTick();
        core.scheduledTick();
        assertEquals(t2 + 1, core.clock().gameTime(), "10 TPS 는 두 번에 한 틱");
        assertEquals(20, run("tick rate 20"));
        assertEquals(50_000_000L, core.world().tickNanos());
        // 범위: 1..10000
        assertFailsWith("tick rate 0.5", src, "Float must not be less than 1.0, found 0.5");
        assertFailsWith("tick rate 10001", src, "Float must not be more than 10000.0, found 10001.0");
    }

    @Test
    void freezeStopsGlobalTicksAndStepAdvancesExactly() throws Exception {
        TickState ts = core.tickState();
        assertEquals(1, run("tick freeze"));
        assertTrue(ts.frozen());
        long t0 = core.clock().gameTime();
        core.scheduledTick();
        core.scheduledTick();
        assertEquals(t0, core.clock().gameTime(), "정지 중에는 시간이 가지 않는다");
        // 정지 중이 아니면 step 은 실패 메시지(명령 값은 1)
        assertEquals(1, run("tick step 3"));
        assertEquals(3, ts.globalStepsLeft());
        for (int i = 0; i < 5; i++) core.scheduledTick();
        assertEquals(t0 + 3, core.clock().gameTime(), "step 3 은 정확히 3 틱");
        assertEquals(0, ts.globalStepsLeft());
        assertEquals(0, run("tick step stop"), "진행 중인 step 이 없다");
        run("tick step 10");
        core.scheduledTick();
        assertEquals(1, run("tick step stop"));
        assertEquals(0, ts.globalStepsLeft());
        core.scheduledTick();
        assertEquals(t0 + 4, core.clock().gameTime());
        // 정지 해제하면 다시 흐른다
        assertEquals(0, run("tick unfreeze"));
        assertFalse(ts.frozen());
        long t1 = core.clock().gameTime();
        core.scheduledTick();
        assertEquals(t1 + 1, core.clock().gameTime());
    }

    @Test
    void stepWhenNotFrozenDoesNothing() throws Exception {
        assertEquals(1, run("tick step"));
        assertEquals(0, core.tickState().globalStepsLeft());
        long t0 = core.clock().gameTime();
        core.scheduledTick();
        assertEquals(t0 + 1, core.clock().gameTime());
    }

    @Test
    void queryReportsRateAndSamples() throws Exception {
        TickState ts = core.tickState();
        for (int i = 1; i <= 100; i++) ts.record(i * 1_000_000L);
        long[] s = ts.sampleCopy();
        assertEquals(100, s.length);
        assertEquals(20, run("tick query"));
        assertTrue(ts.averageTickNanos() > 0);
        // 표본은 100 개 고리 — 101 번째는 가장 오래된 것을 덮는다
        ts.record(500_000_000L);
        long[] after = ts.sampleCopy();
        assertEquals(500_000_000L, after[0]);
        assertEquals(2_000_000L, after[1]);
        assertEquals("1.5", TickCommand.nanosToMillis(1_500_000L));
        assertEquals("50.0", TickCommand.nanosToMillis(50_000_000L));
    }

    @Test
    void permissionThreeRequired() {
        assertFailsWith("tick query", src.withPermission(2), "Unknown or incomplete command, see below for error");
        assertTrue(core.commands().performPrefixedCommandAsync(src.withPermission(3), "tick query").join().success());
    }

    private java.util.List<org.domi.furnace.api.text.Text> console() {
        java.util.List<org.domi.furnace.api.text.Text> msgs = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        core.addConsoleListener(msgs::add);
        return msgs;
    }

    private static boolean has(java.util.List<org.domi.furnace.api.text.Text> msgs, String key) {
        synchronized (msgs) {
            for (var t : msgs) if (key.equals(t.translationKey())) return true;
        }
        return false;
    }

    @Test
    void sprintRunsGlobalAndRegionTicksWithoutWaiting_thenReportsAndRestoresFreeze() throws Exception {
        var world = core.world();
        world.submitAtChunk(new org.domi.furnace.server.world.ChunkPos(0, 0), r -> r.loadChunk(new org.domi.furnace.server.world.ChunkPos(0, 0))).get();
        world.startTicking();
        var msgs = console();
        TickState ts = core.tickState();
        assertEquals(1, run("tick freeze"));
        long t0 = core.clock().gameTime();
        long r0 = world.submitAtChunk(new org.domi.furnace.server.world.ChunkPos(0, 0), r -> r.tickCount()).get();
        long start = System.nanoTime();
        assertEquals(1, run("tick sprint 200"));
        assertTrue(ts.sprinting());
        assertFalse(ts.frozen(), "sprint 중에는 정지를 푼다");
        assertTrue(has(msgs, "commands.tick.status.sprinting"));
        assertEquals(20, run("tick query"));
        assertTrue(has(msgs, "commands.tick.query.rate.sprinting"));
        // 전역 틱 반복(원래 50ms 마다)을 쉬지 않고 불러 sprint 를 끝까지
        for (int i = 0; i < 2000 && ts.sprinting(); i++) {
            core.scheduledTick();
            Thread.sleep(1);
        }
        assertFalse(ts.sprinting(), "sprint 가 끝났다");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertEquals(t0 + 200, core.clock().gameTime(), "전역 시계는 정확히 200 틱");
        long r1 = world.submitAtChunk(new org.domi.furnace.server.world.ChunkPos(0, 0), r -> r.tickCount()).get();
        assertTrue(r1 - r0 >= 200, "region 도 200 틱 이상 돌았다: " + (r1 - r0));
        assertTrue(elapsedMs < 8_000, "20 TPS 라면 10초 — 기다리지 않고 돌았다: " + elapsedMs + "ms");
        assertTrue(has(msgs, "commands.tick.sprint.report"));
        assertTrue(ts.frozen(), "끝나면 원래 정지 상태로");
        // 멈출 sprint 가 없다
        assertEquals(0, run("tick sprint stop"));
        // 도중에 멈춤: 보고 + stop.success, 정지 상태 복구
        run("tick unfreeze");
        run("tick sprint 1d");
        assertEquals(24000, ts.sprintTicks());
        core.scheduledTick();
        msgs.clear();
        assertEquals(1, run("tick sprint stop"));
        assertTrue(has(msgs, "commands.tick.sprint.report"));
        assertTrue(has(msgs, "commands.tick.sprint.stop.success"));
        assertFalse(ts.sprinting());
        assertFalse(ts.frozen());
        // sprint 중 freeze 는 sprint 를 먼저 멈춘다(보고 포함)
        run("tick sprint 1d");
        msgs.clear();
        assertEquals(1, run("tick freeze"));
        assertFalse(ts.sprinting());
        assertTrue(ts.frozen());
        assertTrue(has(msgs, "commands.tick.sprint.report"));
        // 이미 sprint 중에 다시 sprint: stop.success 뒤 sprinting
        run("tick sprint 100");
        msgs.clear();
        run("tick sprint 50");
        assertTrue(has(msgs, "commands.tick.sprint.stop.success"));
        assertEquals(50, ts.sprintTicks());
        assertFalse(ts.frozen(), "다시 시작하면 그 시점(풀린) 상태를 기억한다 — 바닐라와 같다");
        run("tick sprint stop");
        assertFalse(ts.frozen());
    }

    /** 실패 결과와 첫 실패 메시지(파싱 오류 문구)를 함께 확인한다. */
    private void assertFailsWith(String cmd, CommandSource source, String firstMessage) {
        java.util.List<String> msgs = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        core.addConsoleListener(t -> msgs.add(t.getString()));
        assertFalse(core.commands().performPrefixedCommandAsync(source, cmd).join().success(), cmd);
        assertFalse(msgs.isEmpty(), cmd);
        assertEquals(firstMessage, msgs.get(0), cmd);
    }
}
