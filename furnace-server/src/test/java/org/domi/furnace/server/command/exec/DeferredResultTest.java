// 지연 결과 어댑터: region 의 엔티티를 고르는 명령이 실제 결과를 콜백으로 넘기는지(tag @e → 대상 수), 실패면 success=false·메시지,
// forked 소스면 메시지가 없는지, 그리고 앞 컨텍스트가 region 결과를 기다리는 동안 뒤 명령이 시작하지 않는지.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeferredResultTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    final List<String> out = Collections.synchronizedList(new ArrayList<>());
    Cow[] cows;

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.addConsoleListener(t -> out.add(t.getString()));
        cows = spawnCows(core, 2);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    /** region 스레드에서 소를 n 마리 올린다. */
    static Cow[] spawnCows(ServerCore core, int n) throws InterruptedException {
        Cow[] cows = new Cow[n];
        var done = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, region -> {
            var lvl = region.level();
            for (int i = 0; i < n; i++) {
                cows[i] = new Cow(lvl, lvl.nextEntityId());
                cows[i].moveTo(0.5 + i, -60, 0.5, 0, 0);
                lvl.addFreshEntity(cows[i]);
            }
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return cows;
    }

    /** 테스트용 포크 수정자: "testfork <명령>" 은 같은 소스 하나로 포크(forked 표시만 켠다). */
    static void registerTestFork(ServerCore core) {
        var d = core.commands().getDispatcher();
        d.register(LiteralArgumentBuilder.<CommandSource>literal("testfork").fork(d.getRoot(), c -> List.of(c.getSource())));
    }

    @Test
    void tagOnRegionEntitiesReportsTargetCount() {
        var console = CommandSource.console(core);
        CommandResult r = core.commands().performPrefixedCommandAsync(console, "tag @e[type=cow] add a").join();
        assertTrue(r.success());
        assertEquals(2, r.result(), "콜백 결과는 대상 수(지연 결과가 실제 값으로 온다)");
        assertEquals(1, r.callbacks());
        for (Cow c : cows) assertTrue(c.getTags().contains("a"));
        assertTrue(out.contains("Added tag 'a' to 2 entities"), out.toString());
    }

    @Test
    void failureReportsSuccessFalseAndMessage() {
        out.clear();
        CommandResult r = core.commands().performPrefixedCommandAsync(CommandSource.console(core), "tag @e[type=cow] remove zzz").join();
        assertFalse(r.success());
        assertEquals(0, r.result());
        assertEquals(1, r.callbacks());
        assertEquals(List.of("Target does not have this tag"), out);
    }

    @Test
    void forkedSourceSendsNoFailureMessage() {
        registerTestFork(core);
        out.clear();
        CommandResult r = core.commands().performPrefixedCommandAsync(CommandSource.console(core), "testfork tag @e[type=cow] remove zzz").join();
        assertFalse(r.success());
        assertEquals(1, r.callbacks(), "실패 콜백은 온다");
        assertEquals(List.of(), out, "forked 면 실패 메시지 없음");
    }

    @Test
    void secondTopLevelCommandStartsAfterFirstCompletes() throws Exception {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        var d = core.commands().getDispatcher();
        d.register(LiteralArgumentBuilder.<CommandSource>literal("probe").executes(c -> {
            order.add("probe");
            return 1;
        }));
        var console = CommandSource.console(core).withCallback(ResultCallback.sync((ok, v) -> order.add("result:" + v)));
        var done = new CountDownLatch(1);
        core.scheduler().global().execute(() -> {
            // 같은 전역 작업 안에서 연달아 넣는다: 첫 명령은 region 결과를 기다리며 멈추고, 두 번째는 대기열에서 기다린다
            core.commands().performPrefixedCommand(console, "tag @e[type=cow] add b");
            assertTrue(core.commands().queue().isBusy(), "첫 컨텍스트가 region 결과를 기다리는 중");
            core.commands().performPrefixedCommand(CommandSource.console(core), "probe");
            assertEquals(1, core.commands().queue().pendingCount());
            core.commands().performPrefixedCommandAsync(CommandSource.console(core), "probe").thenRun(done::countDown);
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(List.of("result:2", "probe", "probe"), order);
    }
}
