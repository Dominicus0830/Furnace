// /tick: 속도·정지·step 상태와 전역 틱(시간) 반영, 권한, 틱 시간 표본(query 백분위).
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

    /** 실패 결과와 첫 실패 메시지(파싱 오류 문구)를 함께 확인한다. */
    private void assertFailsWith(String cmd, CommandSource source, String firstMessage) {
        java.util.List<String> msgs = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        core.addConsoleListener(t -> msgs.add(t.getString()));
        assertFalse(core.commands().performPrefixedCommandAsync(source, cmd).join().success(), cmd);
        assertFalse(msgs.isEmpty(), cmd);
        assertEquals(firstMessage, msgs.get(0), cmd);
    }
}
