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

    /**
     * 전역 틱 반복을 쉬지 않고 불러 sprint 를 끝까지(넉넉한 상한 60초). 매번 lockstep 을 확인한다:
     * 전역이 시작한 sprint 틱 ≤ 참여 region 들이 마친 틱의 최솟값 + 1.
     */
    private void sprintToEnd(TickState ts) throws InterruptedException {
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (ts.sprinting()) {
            assertTrue(System.nanoTime() < deadline, "sprint 가 60초 안에 끝나지 않았다");
            core.scheduledTick();
            int gen = ts.sprintGeneration();
            long global = ts.sprintGlobalDone();
            long min = Long.MAX_VALUE;
            for (var w : core.worlds()) min = Math.min(min, w.regionizer().sprintMinDone(gen));
            if (ts.sprinting() && ts.sprintGeneration() == gen && min != Long.MAX_VALUE) {
                assertTrue(global <= min + 1, "전역 " + global + " 이 region 최솟값 " + min + " + 1 을 앞섰다");
            }
            Thread.onSpinWait();
        }
    }

    /** 그 칸의 청크를 가진 region 에서 갑옷 거치대를 만든다(강제 로드 청크라 엔티티 틱을 받는다 — tickCount 가 진행 틱 수). */
    private org.domi.furnace.server.entity.ArmorStand stand(org.domi.furnace.server.world.World w, double x, double z) throws Exception {
        return w.submitAtChunk(org.domi.furnace.server.world.ChunkPos.ofBlock(x, z), r -> {
            var lvl = r.level();
            var e = new org.domi.furnace.server.entity.ArmorStand(lvl, lvl.nextEntityId());
            e.moveTo(x, -60, z, 0, 0);
            lvl.addFreshEntity(e);
            return e;
        }).get();
    }

    private int ticksOf(org.domi.furnace.server.world.World w, org.domi.furnace.server.entity.Entity e) throws Exception {
        return w.submitAtChunk(org.domi.furnace.server.world.ChunkPos.ofBlock(e.x(), e.z()), r -> e.tickCount).get();
    }

    /** 그 칸의 region 이 n 번 더 돌 때까지 기다린다(넉넉한 상한 30초). */
    private void awaitRegionTicks(org.domi.furnace.server.world.World w, double x, double z, int n) throws Exception {
        var cp = org.domi.furnace.server.world.ChunkPos.ofBlock(x, z);
        long start = w.submitAtChunk(cp, r -> r.tickCount()).get();
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (w.submitAtChunk(cp, r -> r.tickCount()).get() < start + n) {
            assertTrue(System.nanoTime() < deadline, "region 이 돌지 않는다");
            Thread.onSpinWait();
        }
    }

    @Test
    void heldRegionDoesNotRunPlayerTicks_whileWaitingForSlowGlobal() throws Exception {
        var world = core.world();
        var p = new org.domi.furnace.server.entity.Player(world.entityIds().next(), java.util.UUID.randomUUID(), "A",
                new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        world.submitAtChunk(new org.domi.furnace.server.world.ChunkPos(0, 0), r -> {
            r.adopt(p);
            return null;
        }).get();
        world.startTicking();
        TickState ts = core.tickState();
        awaitRegionTicks(world, 8, 8, 2);
        int before = world.submitAtChunk(new org.domi.furnace.server.world.ChunkPos(0, 0), r -> p.attackStrengthTicker).get();
        long start = System.nanoTime();
        run("tick sprint 100");
        // 전역을 느리게(반복 사이 10ms) — region 은 대부분 전역을 기다린다
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (ts.sprinting()) {
            assertTrue(System.nanoTime() < deadline);
            core.scheduledTick();
            Thread.sleep(10);
        }
        long elapsedTicks = (System.nanoTime() - start) / 50_000_000L;
        int after = world.submitAtChunk(new org.domi.furnace.server.world.ChunkPos(0, 0), r -> p.attackStrengthTicker).get();
        // sprint 100 틱 + sprint 앞뒤로 평소 주기(50ms)로 돈 틱만. 기다리는 차례(100µs 마다)가 플레이어를 틱하면 수천이 된다
        assertTrue(after - before >= 100, "sprint 틱만큼은 진행: " + (after - before));
        assertTrue(after - before <= 100 + elapsedTicks + 5, "기다리는 차례는 플레이어 틱이 아니다: " + (after - before) + ", 경과 " + elapsedTicks);
    }

    @Test
    void sprintGivesEveryRegionExactlyNWorldTicks_evenWhenRegionsWouldMergeMidSprint() throws Exception {
        var world = core.world();
        // 구역(16 청크) 0 과 3 — 병합 반경 1 로는 따로인 두 region. 강제 로드라 엔티티가 틱을 받는다
        assertEquals(1, run("forceload add 8 8"));
        assertEquals(1, run("forceload add 776 8"));
        var sa = stand(world, 8.5, 8.5);
        var sb = stand(world, 776.5, 8.5);
        world.startTicking();
        TickState ts = core.tickState();
        run("tick freeze");   // 정지 중에는 엔티티가 틱을 받지 않는다 — sprint 틱만 센다
        awaitRegionTicks(world, 8, 8, 3);   // 정지 전 마지막 틱이 끝나도록 region 이 정지 상태로 몇 번 돈 뒤에 잰다
        awaitRegionTicks(world, 776, 8, 3);
        int ta = ticksOf(world, sa), tb = ticksOf(world, sb);
        run("tick sprint 300");
        // sprint 도중 사이 구역 청크를 올려 두 region 이 합쳐지려 하게 한다(새로 생긴 region 은 sprint 에 참여하지 않는다)
        for (int i = 0; i < 5; i++) core.scheduledTick();
        for (int cx = 16; cx <= 32; cx += 16) {
            var cp = new org.domi.furnace.server.world.ChunkPos(cx, 0);
            world.submitAtChunk(cp, r -> r.loadChunk(cp));
        }
        sprintToEnd(ts);
        assertTrue(ts.frozen());
        awaitRegionTicks(world, 8, 8, 3);   // 끝난 뒤 병합이 일어나도 정지 중이라 그대로
        assertEquals(ta + 300, ticksOf(world, sa), "구역 A 는 정확히 300 틱 진행");
        assertEquals(tb + 300, ticksOf(world, sb), "구역 B 는 정확히 300 틱 진행");
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
        sprintToEnd(ts);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertEquals(t0 + 200, core.clock().gameTime(), "전역 시계는 정확히 200 틱");
        long r1 = world.submitAtChunk(new org.domi.furnace.server.world.ChunkPos(0, 0), r -> r.tickCount()).get();
        assertTrue(r1 - r0 >= 200, "region 도 200 틱 이상 돌았다: " + (r1 - r0));
        assertTrue(elapsedMs < 60_000, "넉넉한 상한(20 TPS 로 10초 걸릴 일): " + elapsedMs + "ms");
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
