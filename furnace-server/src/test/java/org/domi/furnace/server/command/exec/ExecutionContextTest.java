// 바닐라 ExecutionContext(hs) 이식 검증: 큐 순서(새 항목은 그 항목 직후에 넣은 순서대로), 체인 예산 소진, 포크 한도(limit-1),
// 큐 상한(초과 뒤 추가는 버림), 최상위 프레임 discard 가 덱 전체를 비움.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.RequiredArgumentBuilder;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.sched.GlobalThread;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class ExecutionContextTest {

    @TempDir Path tmp;
    private GlobalThread global;

    @BeforeEach
    void setUp() { global = new GlobalThread("exec-test-global"); }

    @AfterEach
    void tearDown() { global.close(); }

    private ExecutionContext context(int chainLimit, int forkLimit, int maxDepth) {
        return new ExecutionContext(chainLimit, forkLimit, global, r -> global.execute(r), Duration.ofSeconds(5), maxDepth);
    }

    private void onGlobal(Runnable r) { global.execute(r).join(); }

    private static EntryAction record(List<String> log, String name, Consumer<ExecutionContext> then) {
        return (ctx, frame) -> {
            log.add(name);
            ctx.incrementCost();
            if (then != null) then.accept(ctx);
        };
    }

    @Test
    void entriesQueuedByAnEntryRunRightAfterItInOrder() {
        List<String> log = new ArrayList<>();
        onGlobal(() -> {
            ExecutionContext ctx = context(1000, 1000, ExecutionContext.MAX_QUEUE_DEPTH);
            ExecutionContext.queueInitialAction(ctx, (c, frame) -> {
                log.add("A");
                c.queueNext(new CommandQueueEntry(frame, record(log, "a1", cc -> cc.queueNext(new CommandQueueEntry(frame, record(log, "a1x", null))))));
                c.queueNext(new CommandQueueEntry(frame, record(log, "a2", null)));
                c.queueNext(new CommandQueueEntry(frame, record(log, "a3", null)));
            }, ResultCallback.EMPTY);
            ExecutionContext.queueInitialAction(ctx, record(log, "B", null), ResultCallback.EMPTY);
            ctx.run();
            assertTrue(ctx.isFinished());
        });
        assertEquals(List.of("A", "a1", "a1x", "a2", "a3", "B"), log);
    }

    @Test
    void chainBudgetStopsExecutionAndLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(ExecutionContext.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            List<String> log = new ArrayList<>();
            onGlobal(() -> {
                ExecutionContext ctx = context(3, 1000, ExecutionContext.MAX_QUEUE_DEPTH);
                for (int i = 0; i < 5; i++) ExecutionContext.queueInitialAction(ctx, record(log, "c" + i, null), ResultCallback.EMPTY);
                ctx.run();
                assertTrue(ctx.isFinished());
                assertEquals(0, ctx.remainingQuota());
            });
            assertEquals(List.of("c0", "c1", "c2"), log);
            assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().equals("Command execution stopped due to limit (executed 3 commands)")),
                    "예산 소진 로그");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void queueOverflowDropsEverythingAndLaterAdds() {
        List<String> log = new ArrayList<>();
        onGlobal(() -> {
            ExecutionContext ctx = context(1000, 1000, 4);
            ExecutionContext.queueInitialAction(ctx, (c, frame) -> {
                log.add("top");
                for (int i = 0; i < 10; i++) c.queueNext(new CommandQueueEntry(frame, record(log, "x" + i, null)));
                assertTrue(c.isQueueOverflowed());
            }, ResultCallback.EMPTY);
            ctx.run();
            assertTrue(ctx.isFinished());
        });
        assertEquals(List.of("top"), log, "상한을 넘으면 큐를 비우고 그 뒤 추가는 버린다");
    }

    @Test
    void topLevelDiscardClearsWholeDeque() {
        List<String> log = new ArrayList<>();
        List<Integer> frameResults = new ArrayList<>();
        onGlobal(() -> {
            ExecutionContext ctx = context(1000, 1000, ExecutionContext.MAX_QUEUE_DEPTH);
            ExecutionContext.queueInitialAction(ctx, (c, frame) -> {
                log.add("A");
                assertEquals(0, frame.depth());
                c.queueNext(new CommandQueueEntry(frame, (cc, f) -> {
                    log.add("return");
                    f.returnSuccess(7);
                    f.discard();
                }));
                c.queueNext(new CommandQueueEntry(frame, record(log, "after-return", null)));
            }, ResultCallback.sync((ok, v) -> frameResults.add(v)));
            ExecutionContext.queueInitialAction(ctx, record(log, "other-top", null), ResultCallback.EMPTY);
            ctx.run();
        });
        assertEquals(List.of("A", "return"), log, "최상위 프레임(depth 0) discard 는 덱 전체를 비운다");
        assertEquals(List.of(7), frameResults);
    }

    @Test
    void forkLimitAllowsAtMostLimitMinusOne() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            List<String> out = Collections.synchronizedList(new ArrayList<>());
            core.addConsoleListener(t -> out.add(t.getString()));
            CommandDispatcher<CommandSource> d = new CommandDispatcher<>();
            int[] runs = new int[1];
            var count = d.register(LiteralArgumentBuilder.<CommandSource>literal("count").executes(c -> ++runs[0]));
            d.register(LiteralArgumentBuilder.<CommandSource>literal("fork").then(RequiredArgumentBuilder.<CommandSource, Integer>argument("n", Arguments.integer(0))
                    .fork(d.getRoot(), c -> Collections.nCopies(c.getArgument("n", Integer.class), c.getSource()))));
            // forward(…, fork=false): 같은 다중 소스 수정자지만 forked 가 아니라 한도 오류가 메시지로 보인다
            d.register(LiteralArgumentBuilder.<CommandSource>literal("multi").then(RequiredArgumentBuilder.<CommandSource, Integer>argument("n", Arguments.integer(0))
                    .forward(d.getRoot(), c -> Collections.nCopies(c.getArgument("n", Integer.class), c.getSource()), false)));
            CommandSource src = CommandSource.console(core);
            var g = (GlobalThread) sched.global();
            for (String input : new String[] {"fork 3 count", "fork 4 count", "multi 3 count", "multi 4 count"}) {
                runs[0] = 0;
                out.clear();
                g.execute(() -> {
                    ExecutionContext ctx = new ExecutionContext(1000, 4, g, r -> g.execute(r), Duration.ofSeconds(5));
                    ContextChain<CommandSource> chain = ContextChain.tryFlatten(d.parse(input, src).getContext().build(input)).orElseThrow();
                    ExecutionContext.queueInitialCommandExecution(ctx, input, chain, src, ResultCallback.EMPTY);
                    ctx.run();
                }).join();
                switch (input) {
                    case "fork 3 count", "multi 3 count" -> {
                        assertEquals(3, runs[0], "한도 4 에서 소스 3개는 통과: " + input);
                        assertTrue(out.isEmpty(), out.toString());
                    }
                    case "fork 4 count" -> {
                        assertEquals(0, runs[0], "한도 4 에서 소스 4개는 command.forkLimit");
                        assertEquals(List.of(), out, "forked 면 한도 오류도 메시지 없음(바닐라 handleError)");
                    }
                    default -> {
                        assertEquals(0, runs[0]);
                        assertEquals(List.of("Maximum number of contexts (4) reached"), out);
                    }
                }
            }
            assertNotNull(count);
        } finally {
            sched.close();
        }
    }

    @Test
    void publicMethodsAssertGlobalThread() {
        ExecutionContext ctx = context(10, 10, 10);
        assertThrows(AssertionError.class, () -> ExecutionContext.queueInitialAction(ctx, (c, f) -> {}, ResultCallback.EMPTY));
        assertThrows(AssertionError.class, ctx::run);
    }
}
