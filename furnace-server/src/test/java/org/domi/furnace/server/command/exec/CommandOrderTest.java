// S0.5: 결과를 기다리지 않던 명령(setblock·fill·tp·summon·ride)이 region 일을 마친 뒤에야 다음 줄로 넘어가는지.
// 같은 컨텍스트에 두 줄을 넣고(execute 없이 queueInitialCommandExecution 두 번) 뒷줄의 test 명령이 앞줄의 효과를 보는지 본다.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.EntityOps;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.ContextChain;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.command.brigadier.RequiredArgumentBuilder;
import org.domi.furnace.server.command.builtin.SummonCommand;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.sched.GlobalThread;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandOrderTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    ThreadOwner global;
    Cow[] cows;
    final List<String> log = Collections.synchronizedList(new ArrayList<>());
    final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    final List<Boolean> messageOnGlobal = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        global = (ThreadOwner) sched.global();
        core.world().awaitChunk(new ChunkPos(0, 0));
        cows = DeferredResultTest.spawnCows(core, 2);
        core.addConsoleListener(t -> {
            messages.add(t.getString());
            messageOnGlobal.add(global.isOwnerThread());
        });
        var d = core.commands().getDispatcher();
        // probeblock x y z: 그 칸의 블록 상태를 region 에서 읽어 기록(execute if block 의 대역)
        d.register(LiteralArgumentBuilder.<CommandSource>literal("probeblock")
                .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("x", Arguments.integer())
                        .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("y", Arguments.integer())
                                .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("z", Arguments.integer()).executes(c -> {
                                    CommandSource s = c.getSource();
                                    Pos pos = new Pos(c.getArgument("x", Integer.class), c.getArgument("y", Integer.class), c.getArgument("z", Integer.class));
                                    return EntityOps.result(s, EntityOps.awaitAsync(s, EntityOps.atRegion(s.level(), pos.x(), pos.z(),
                                            r -> r.level().getBlockState(pos)), st -> {
                                        log.add("block=" + st);
                                        return CompletableFuture.completedFuture(1);
                                    }));
                                })))));
        // probecow: 첫 소의 위치를 주인 region 에서 읽어 기록(execute at @e 의 대역)
        d.register(LiteralArgumentBuilder.<CommandSource>literal("probecow").executes(c -> {
            CommandSource s = c.getSource();
            return EntityOps.result(s, EntityOps.awaitAsync(s, EntityOps.onOwner(s, cows[0], r -> r.x() + "," + r.y() + "," + r.z()), pos -> {
                log.add("cow=" + pos);
                return CompletableFuture.completedFuture(1);
            }));
        }));
    }

    @AfterEach
    void tearDown() { sched.close(); }

    /** 한 컨텍스트에 줄들을 차례로 넣어 끝날 때까지 돌린다. */
    private void runLines(String... lines) {
        var g = (GlobalThread) sched.global();
        var src = CommandSource.console(core);
        var d = core.commands().getDispatcher();
        CompletableFuture<Void> done = g.submit(() -> {
            ExecutionContext ctx = new ExecutionContext(1000, 4, g, r -> g.execute(r), Duration.ofSeconds(10));
            for (String line : lines) {
                ContextChain<CommandSource> chain = ContextChain.tryFlatten(d.parse(line, src).getContext().build(line)).orElseThrow();
                ExecutionContext.queueInitialCommandExecution(ctx, line, chain, src, ResultCallback.EMPTY);
            }
            ctx.run();
            return ctx.onFinished();
        }).thenCompose(f -> f);
        done.orTimeout(20, TimeUnit.SECONDS).join();
    }

    @Test
    void setblockThenProbeSeesNewBlock() {
        int stone = B.defaultState("minecraft:stone");
        runLines("setblock 1 100 1 minecraft:stone", "probeblock 1 100 1");
        assertEquals(List.of("block=" + stone), log);
        assertTrue(messages.contains("Changed the block at 1, 100, 1"), messages.toString());
        assertFalse(messageOnGlobal.contains(false), "setblock 성공 메시지는 전역 스레드에서");
    }

    @Test
    void fillThenProbeSeesNewBlock() {
        int stone = B.defaultState("minecraft:stone");
        runLines("fill 1 100 1 3 100 3 minecraft:stone", "probeblock 3 100 3");
        assertEquals(List.of("block=" + stone), log);
        assertTrue(messages.contains("Successfully filled 9 block(s)"), messages.toString());
        assertFalse(messageOnGlobal.contains(false));
    }

    @Test
    void setblockFailureSendsMessageOnGlobalAndNextLineStillRuns() {
        runLines("setblock 1 100 1 minecraft:stone", "setblock 1 100 1 minecraft:stone", "probeblock 1 100 1");   // 같은 블록이라 두 번째는 실패
        assertEquals(List.of("block=" + B.defaultState("minecraft:stone")), log);
        assertTrue(messages.contains("Could not set the block"), messages.toString());
        assertFalse(messageOnGlobal.contains(false), "실패 메시지도 전역 스레드에서");
    }

    @Test
    void tpThenNextLineReadsNewPosition() {
        runLines("tp @e[type=cow,limit=1,sort=arbitrary] 4.5 -60 4.5", "probecow");
        assertEquals(List.of("cow=4.5,-60.0,4.5"), log);
        assertFalse(messageOnGlobal.contains(false), "tp 성공 메시지는 전역 스레드에서");
    }

    @Test
    void summonFutureReturnsTheEntity() {
        CompletableFuture<Entity> f = SummonCommand.summonAt(CommandSource.console(core), "minecraft:cow", new double[] {8.5, -60, 8.5}, new NbtCompound(), true);
        Entity e = f.orTimeout(10, TimeUnit.SECONDS).join();
        assertEquals("minecraft:cow", e.typeName());
        assertEquals(8.5, e.x());
    }

    @Test
    void summonCommandThenNextLineRuns() {
        runLines("summon minecraft:cow 12.5 -60 12.5", "setblock 1 100 1 minecraft:stone");
        assertTrue(messages.stream().anyMatch(m -> m.startsWith("Summoned new")), messages.toString());
        assertTrue(messages.contains("Changed the block at 1, 100, 1"), messages.toString());
        assertFalse(messageOnGlobal.contains(false), "summon 성공 메시지는 전역 스레드에서");
    }

    @Test
    void rideMessagesAreSentFromGlobal() {
        runLines("ride @e[type=cow,limit=1,sort=arbitrary] mount @e[type=cow,limit=1,sort=furthest]");
        runLines("ride @e[type=cow,limit=1,sort=arbitrary] mount @e[type=cow,limit=1,sort=furthest]");   // 이미 탔거나 실패 → 메시지
        assertFalse(messages.isEmpty());
        assertFalse(messageOnGlobal.contains(false), "ride 메시지는 전역 스레드에서: " + messages);
    }
}
