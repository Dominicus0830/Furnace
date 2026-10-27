// scheduler 규칙 8: 명령 본문·결과 콜백·헬퍼 마무리 코드가 전역 스레드에서만 도는지(region 에서 완성되는 future 를 거치는 tag @e 경로 포함),
// 다른 스레드에서 들어온 명령이 전역으로 넘어가는지, 전역 스레드에서 명령 future 를 join 하면 assert 에 걸리는지.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.RequiredArgumentBuilder;
import org.domi.furnace.server.command.arguments.EntitySelector;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandThreadTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    ThreadOwner global;
    final List<String> onGlobal = Collections.synchronizedList(new ArrayList<>());
    final List<String> offGlobal = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        global = (ThreadOwner) sched.global();
        DeferredResultTest.spawnCows(core, 2);
        var d = core.commands().getDispatcher();
        // threadprobe <targets>: 본문 → 선택자 해석 뒤 action → 대상 주인에게 보낸 일의 마무리, 각 지점의 스레드를 적는다
        d.register(LiteralArgumentBuilder.<CommandSource>literal("threadprobe").then(
                RequiredArgumentBuilder.<CommandSource, EntitySelector>argument("targets", EntityArgument.entities()).executes(c -> {
                    mark("body");
                    CommandSource s = c.getSource();
                    return EntityOps.result(s, EntityArgument.withEntitiesAsync(c, "targets", targets -> {
                        mark("action");
                        return EntityOps.forEachAsync(s, targets, r -> r.getTags().size(), results -> {
                            mark("finish");
                            return results.size();
                        });
                    }));
                })));
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private void mark(String where) { (global.isOwnerThread() ? onGlobal : offGlobal).add(where); }

    @Test
    void bodyCallbackAndFinishRunOnGlobalThroughRegionFutures() {
        var src = CommandSource.console(core).withCallback(ResultCallback.sync((ok, v) -> mark("callback")));
        CommandResult r = core.commands().performPrefixedCommandAsync(src, "threadprobe @e[type=cow]").join();
        assertEquals(2, r.result());
        assertEquals(List.of(), offGlobal, "전역 밖에서 돈 지점");
        assertEquals(List.of("body", "action", "finish", "callback"), onGlobal);
    }

    @Test
    void tagOnRegionEntitiesCallsBackOnGlobal() {
        var src = CommandSource.console(core).withCallback(ResultCallback.sync((ok, v) -> mark("callback")));
        core.commands().performPrefixedCommandAsync(src, "tag @e[type=cow] add t").join();
        assertEquals(List.of("callback"), onGlobal);
        assertEquals(List.of(), offGlobal);
    }

    @Test
    void commandFromOtherThreadIsHandedToGlobal() throws Exception {
        var done = new CountDownLatch(1);
        var src = CommandSource.console(core).withCallback(ResultCallback.sync((ok, v) -> done.countDown()));
        Thread t = new Thread(() -> core.commands().performPrefixedCommand(src, "threadprobe @e[type=cow]"), "plugin-like");
        t.start();
        t.join();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(List.of(), offGlobal);
        assertTrue(onGlobal.contains("body"));
    }

    @Test
    void joiningCommandFutureOnGlobalAsserts() {
        boolean asserted = sched.global().submit(() -> {
            var f = core.commands().performPrefixedCommandAsync(CommandSource.console(core), "threadprobe @e[type=cow]");
            try {
                f.join();
                return false;
            } catch (AssertionError e) {
                return true;
            }
        }).join();
        assertTrue(asserted, "전역 스레드에서 명령 future join 은 assert");
    }
}
