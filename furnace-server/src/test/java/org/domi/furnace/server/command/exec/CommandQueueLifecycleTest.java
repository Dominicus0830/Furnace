// 명령 큐 수명: 끝나지 않는 지연 결과는 타임아웃으로 실패 처리되고 뒤 명령이 진행된다, 타임아웃 뒤 늦은 완료는 버려진다(이중 재개 없음),
// close() 뒤의 완료 홉은 예외 없이 버려지고 기다리던 future 는 완료된다.
// 멈춘 컨텍스트의 재개 body(지연 결과 콜백) 안에서 들어온 명령은 그 컨텍스트에 합쳐진다(뒤에 기다리던 명령보다 먼저).
// (/reload 뒤 replaceLibrary 가 전역에서 불리는지는 FunctionTest 가 본다 — 다른 스레드면 assert.)
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandQueueLifecycleTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    final List<String> out = Collections.synchronizedList(new ArrayList<>());
    final AtomicReference<CompletableFuture<Integer>> pendingResult = new AtomicReference<>();
    final List<Integer> marks = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.addConsoleListener(t -> out.add(t.getString()));
        var d = core.commands().getDispatcher();
        // hang: 끝나지 않는(테스트가 나중에 완성하는) 지연 결과를 맡긴다
        d.register(LiteralArgumentBuilder.<CommandSource>literal("hang").executes(c -> {
            CompletableFuture<Integer> f = new CompletableFuture<>();
            pendingResult.set(f);
            c.getSource().defer(f);
            return 1;
        }));
        d.register(LiteralArgumentBuilder.<CommandSource>literal("probe").executes(c -> 42));
        d.register(LiteralArgumentBuilder.<CommandSource>literal("mark").then(org.domi.furnace.server.command.Commands.argument("n",
                org.domi.furnace.server.command.brigadier.Arguments.integer()).executes(c -> {
            marks.add(c.getArgument("n", Integer.class));
            return 1;
        })));
        core.commands().queue().deferredTimeout(Duration.ofMillis(300));
    }

    @AfterEach
    void tearDown() { sched.close(); }

    @Test
    void timeoutFailsTheCommandAndLaterCommandsRun() {
        var console = CommandSource.console(core);
        var hung = core.commands().performPrefixedCommandAsync(console, "hang");
        var next = core.commands().performPrefixedCommandAsync(console, "probe");
        CommandResult r = hung.join();
        assertFalse(r.success());
        assertEquals(1, r.callbacks(), "타임아웃은 onFailure 한 번");
        assertTrue(out.contains("An unexpected error occurred trying to execute that command"), out.toString());
        assertEquals(42, next.join().result(), "뒤 명령은 타임아웃 뒤 진행");
    }

    @Test
    void lateCompletionAfterTimeoutIsIgnored() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        var src = CommandSource.console(core).withCallback(ResultCallback.sync((ok, v) -> callbacks.incrementAndGet()));
        CommandResult r = core.commands().performPrefixedCommandAsync(src, "hang").join();
        assertFalse(r.success());
        assertEquals(1, callbacks.get());
        pendingResult.get().complete(5);   // 타임아웃 뒤 늦게 도착한 결과
        assertEquals(42, core.commands().performPrefixedCommandAsync(CommandSource.console(core), "probe").join().result());
        Thread.sleep(200);
        assertEquals(1, callbacks.get(), "늦은 완료는 콜백을 다시 부르지 않는다");
        assertFalse(core.commands().queue().isBusy());
    }

    @Test
    void closeDropsContextsAndLaterHopsAreIgnored() throws Exception {
        core.commands().queue().deferredTimeout(Duration.ofSeconds(30));
        var console = CommandSource.console(core);
        var hung = core.commands().performPrefixedCommandAsync(console, "hang");
        var queued = core.commands().performPrefixedCommandAsync(console, "probe");
        for (int i = 0; i < 200 && pendingResult.get() == null; i++) Thread.sleep(10);
        assertNotNull(pendingResult.get());
        core.commands().queue().close();   // 테스트 스레드(전역 아님)에서 닫기: 전역으로 넘겨 정리
        assertTrue(core.commands().queue().isClosed());
        assertEquals(CommandResult.NONE, hung.join(), "버려진 컨텍스트의 future 도 완료된다");
        assertEquals(CommandResult.NONE, queued.join(), "대기 중이던 명령도 버려진다");
        assertDoesNotThrow(() -> pendingResult.get().complete(1));   // 닫힌 뒤의 완료 홉은 조용히 버려진다
        assertDoesNotThrow(() -> core.commands().queue().close());   // 두 번 닫아도 된다
        // 닫힌 뒤 들어온 명령은 실행하지 않고 바로 끝난다
        assertEquals(CommandResult.NONE, core.commands().performPrefixedCommandAsync(console, "probe").join());
    }

    @Test
    void commandsEnqueuedFromResumeBodyJoinTheSameContext() throws Exception {
        core.commands().queue().deferredTimeout(Duration.ofSeconds(30));
        var console = CommandSource.console(core);
        // hang 의 결과 콜백(재개 body 안, 전역 스레드)에서 mark 1 을 넣는다 — 바닐라라면 같은 컨텍스트 안이라 바로 이어 돈다
        var src = console.withCallback(ResultCallback.sync((ok, v) -> core.commands().performPrefixedCommand(console, "mark 1")));
        var hung = core.commands().performPrefixedCommandAsync(src, "hang");
        var later = core.commands().performPrefixedCommandAsync(console, "mark 2");
        for (int i = 0; i < 200 && pendingResult.get() == null; i++) Thread.sleep(10);
        pendingResult.get().complete(3);
        hung.join();
        later.join();
        assertEquals(List.of(1, 2), marks, "재개 body 안의 명령이 뒤에 기다리던 명령보다 먼저");
    }
}
