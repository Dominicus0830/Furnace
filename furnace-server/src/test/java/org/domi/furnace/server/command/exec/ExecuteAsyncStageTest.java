// 비동기 수정자 단계(BuildContexts.AsyncStage)와 /execute on controller·origin: 포크 한도, forked 무음 실패, 비포크 실패 메시지,
// 타임아웃·늦은 완료 폐기, 여러 번 멈췄다 같은 자리부터 재개하는 순서.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.PrimedTnt;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.sched.GlobalThread;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecuteAsyncStageTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    Cow[] cows;
    final List<CommandSource> probed = Collections.synchronizedList(new ArrayList<>());
    final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    final CompletableFuture<Collection<CommandSource>> hanging = new CompletableFuture<>();

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().awaitChunk(new ChunkPos(0, 0));
        cows = DeferredResultTest.spawnCows(core, 2);
        core.addConsoleListener(t -> messages.add(t.getString()));
        var d = core.commands().getDispatcher();
        d.register(LiteralArgumentBuilder.<CommandSource>literal("probe").executes(c -> {
            probed.add(c.getSource());
            return 1;
        }));
        // slow: 소스마다 50ms 뒤 다른 스레드에서 완료되는 future 로 소스 둘(x, x+100)로 갈라진다 — 매번 멈췄다 재개한다
        d.register(LiteralArgumentBuilder.<CommandSource>literal("slow").forkAsync(d.getRoot(), c -> {
            CommandSource s = c.getSource();
            return CompletableFuture.supplyAsync(() -> (Collection<CommandSource>) List.of(s, s.withPosition(s.x() + 100, s.y(), s.z())),
                    CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS));
        }));
        d.register(LiteralArgumentBuilder.<CommandSource>literal("hang").forkAsync(d.getRoot(), c -> hanging));
        inRegion(() -> {
            cows[0].addTag("a");
            cows[1].addTag("b");
        });
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private <T> T inRegion(java.util.function.Supplier<T> task) {
        return core.world().submitAtChunk(new ChunkPos(0, 0), r -> task.get()).orTimeout(10, TimeUnit.SECONDS).join();
    }

    private void inRegion(Runnable task) { inRegion(() -> { task.run(); return null; }); }

    /** 한 컨텍스트로 한 줄을 돌려 끝날 때까지 기다린다. */
    private void runLine(String line, int forkLimit, Duration timeout) {
        var g = (GlobalThread) sched.global();
        var src = CommandSource.console(core);
        var d = core.commands().getDispatcher();
        g.submit(() -> {
            ExecutionContext ctx = new ExecutionContext(1000, forkLimit, g, r -> g.execute(r), timeout);
            ContextChain<CommandSource> chain = ContextChain.tryFlatten(d.parse(line, src).getContext().build(line)).orElseThrow();
            ExecutionContext.queueInitialCommandExecution(ctx, line, chain, src, ResultCallback.EMPTY);
            ctx.run();
            return ctx.onFinished();
        }).thenCompose(f -> f).orTimeout(20, TimeUnit.SECONDS).join();
    }

    @Test
    void forkLimitStopsChainWithMessage() {
        // 소 둘: 0 + 2 >= 2 → 한도 초과로 체인 중단(실행 없음)
        runLine("execute as @e[type=cow] run probe", 2, Duration.ofSeconds(10));
        assertTrue(probed.isEmpty());
        assertTrue(messages.isEmpty(), "포크 뒤라 한도 오류도 무음(바닐라 handleError(forkedMode)): " + messages);
        // 비포크에서 한도 1: 0 + 1 >= 1 → 메시지와 함께 중단
        runLine("execute positioned 1 1 1 run probe", 1, Duration.ofSeconds(10));
        assertTrue(probed.isEmpty());
        assertEquals(List.of(Text.translatable("command.forkLimit", 1).getString()), messages);
        messages.clear();
        runLine("execute as @e[type=cow] run probe", 3, Duration.ofSeconds(10));
        assertEquals(2, probed.size(), "한도 3 이면 둘이 들어간다");
    }

    @Test
    void failuresAreSilentWhenForkedAndMessagedWhenNot() {
        // 비포크: in 이 실패하면 빨간 메시지
        runLine("execute in minecraft:the_end run probe", 100, Duration.ofSeconds(10));
        assertTrue(probed.isEmpty());
        assertEquals(1, messages.size(), messages.toString());
        messages.clear();
        // 포크 뒤(as @e): 소마다 실패해도 메시지는 없다
        runLine("execute as @e[type=cow] in minecraft:the_end run probe", 100, Duration.ofSeconds(10));
        assertTrue(probed.isEmpty());
        assertTrue(messages.isEmpty(), "forked 에서는 실패가 무음: " + messages);
    }

    @Test
    void timeoutEndsChainAndLateCompletionIsDiscarded() {
        runLine("hang probe", 100, Duration.ofMillis(200));
        assertTrue(probed.isEmpty());
        assertTrue(messages.isEmpty(), "hang 은 포크라 타임아웃 메시지가 없다(체인은 끝난다): " + messages);
        // 늦게 끝나도(세대 토큰 폐기) 아무 일도 일어나지 않는다
        hanging.complete(List.of(CommandSource.console(core)));
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(probed.isEmpty());
    }

    @Test
    void repeatedSuspendResumeKeepsSourceOrder() {
        runLine("execute as @e[type=cow] run slow probe", 100, Duration.ofSeconds(10));
        assertEquals(4, probed.size());
        // 소0 의 두 갈래 → 소1 의 두 갈래, 소스 순서 유지
        assertSame(cows[0], probed.get(0).entity());
        assertSame(cows[0], probed.get(1).entity());
        assertSame(cows[1], probed.get(2).entity());
        assertSame(cows[1], probed.get(3).entity());
        assertEquals(probed.get(0).x() + 100, probed.get(1).x(), 1e-9);
        assertEquals(probed.get(2).x() + 100, probed.get(3).x(), 1e-9);
    }

    @Test
    void onControllerAndOrigin() {
        inRegion(() -> {
            assertTrue(cows[0].startRiding(cows[1], true));
            var lvl = cows[0].level();
            var tnt = new PrimedTnt(lvl, lvl.nextEntityId(), 0.5, -60, 0.5, cows[0]);
            tnt.addTag("t");
            lvl.addFreshEntity(tnt);
        });
        runLine("execute as @e[tag=b] on controller run tag @s add ctl", 100, Duration.ofSeconds(10));
        assertTrue(inRegion(() -> cows[0].getTags().contains("ctl")), "cow1 을 조종하는 건 위에 탄 cow0");
        runLine("execute as @e[tag=t] on origin run tag @s add o", 100, Duration.ofSeconds(10));
        assertTrue(inRegion(() -> cows[0].getTags().contains("o")), "TNT 의 점화자");
    }
}
