// /tp: 상대(~)·국소(^) 좌표 계산, 회전, 오류 키(좌표 범위·엔티티 아님·다른 차원), 도착 엔티티 형, 그리고 실제 region 의 일반 엔티티(소)를
// 선택자로 골라 옮기는 경로(@e[type=cow,tag=…])와 facing.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeleportCommandTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private Player player(String name, double x, double y, double z, float yaw, float pitch, BlockingQueue<double[]> moves) {
        var p = new Player(10, UUID.randomUUID(), name, new EmbeddedChannel());
        p.applyMove(x, y, z, yaw, pitch, true);
        p.teleporter(moves::add);
        return p;
    }

    private static double[] next(BlockingQueue<double[]> q) throws InterruptedException {
        double[] t = q.poll(10, TimeUnit.SECONDS);
        assertNotNull(t, "텔레포트가 오지 않았다");
        return t;
    }

    @Test
    void relativeCoordinatesKeepRotation() throws Exception {
        var moves = new LinkedBlockingQueue<double[]>();
        var p = player("Steve", 10, 64, 20, 90, 15, moves);
        var src = CommandSource.player(core, p).withPermission(2);
        assertEquals(1, core.commands().getDispatcher().execute("tp ~1 ~2 ~3", src));
        double[] t = next(moves);
        assertArrayEquals(new double[] {11, 66, 23, 90, 15}, t, 1e-9);
        // 대상 지정 + 절대 좌표(정수는 칸 가운데로: x, z 에 0.5)
        assertEquals(1, core.commands().getDispatcher().execute("teleport @s 5 70 -5", src));
        assertArrayEquals(new double[] {5.5, 70, -4.5, 90, 15}, next(moves), 1e-9);
        // 소수점이 있으면 그대로
        core.commands().getDispatcher().execute("tp @s 5.25 70 -5.75", src);
        assertArrayEquals(new double[] {5.25, 70, -5.75, 90, 15}, next(moves), 1e-9);
    }

    @Test
    void localCoordinatesFollowLookDirection() throws Exception {
        var moves = new LinkedBlockingQueue<double[]>();
        // yaw 90 = 서쪽(-x) 을 본다: 앞 1 → x-1, 왼쪽 1 → 남쪽(+z)... 바닐라 LocalCoordinates 와 같은 식
        var p = player("Steve", 10, 64, 20, 90, 0, moves);
        var src = CommandSource.player(core, p).withPermission(2);
        core.commands().getDispatcher().execute("tp @s ^ ^ ^2", src);
        assertArrayEquals(new double[] {8, 64, 20, 90, 0}, next(moves), 1e-6);
        core.commands().getDispatcher().execute("tp @s ^ ^3 ^", src);
        assertArrayEquals(new double[] {10, 67, 20, 90, 0}, next(moves), 1e-6);
        core.commands().getDispatcher().execute("tp @s ^1 ^ ^", src);
        double[] left = next(moves);
        assertEquals(10, left[0], 1e-6);
        assertEquals(21, left[2], 1e-6, "yaw 90 에서 왼쪽은 +z");
    }

    @Test
    void rotationArgumentRelativeToSource() throws Exception {
        var moves = new LinkedBlockingQueue<double[]>();
        var p = player("Steve", 0, 64, 0, 30, 10, moves);
        var src = CommandSource.player(core, p).withPermission(2);
        core.commands().getDispatcher().execute("tp @s 1 64 1 ~15 ~-5", src);
        assertArrayEquals(new double[] {1.5, 64, 1.5, 45, 5}, next(moves), 1e-6);
        // wrapDegrees: 190 → -170
        core.commands().getDispatcher().execute("tp @s 1 64 1 190 0", src);
        assertArrayEquals(new double[] {1.5, 64, 1.5, -170, 0}, next(moves), 1e-6);
    }

    @Test
    void facingLocationUsesVanillaAtan2() throws Exception {
        var moves = new LinkedBlockingQueue<double[]>();
        var p = player("Steve", 0, 64, 0, 0, 0, moves);
        var src = CommandSource.player(core, p).withPermission(2);
        // (0,64,0)에서 (10,64,0)을 보면 동쪽: yaw -90, pitch 0
        core.commands().getDispatcher().execute("tp @s 0.0 64.0 0.0 facing 10.0 64.0 0.0", src);
        double[] t = next(moves);
        assertEquals(-90, t[3], 0.05);
        assertEquals(0, t[4], 0.05);
        // 위쪽 점은 pitch 음수
        core.commands().getDispatcher().execute("tp @s 0.0 64.0 0.0 facing 0.0 74.0 10.0", src);
        t = next(moves);
        assertEquals(0, t[3], 0.05);
        assertEquals(-45, t[4], 0.05);
        float[] r = Teleports.lookAt(0, 64, 0, new double[] {0, 64, -10});
        assertEquals(180f, Math.abs(r[0]), 0.05f, "북쪽(-z)은 yaw ±180");
    }

    @Test
    void errorKeys() {
        var d = core.commands().getDispatcher();
        var p = new Player(10, UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.applyMove(0, 64, 0, 0, 0, true);
        var src = CommandSource.player(core, p).withPermission(2);
        assertEquals("commands.teleport.invalidPosition",
                assertThrows(CommandSyntaxException.class, () -> d.execute("tp @s 30000000 64 0", src)).getRawMessage().translationKey());
        assertEquals("commands.teleport.invalidPosition",
                assertThrows(CommandSyntaxException.class, () -> d.execute("tp @s 0 20000000 0", src)).getRawMessage().translationKey());
        assertEquals("permissions.requires.entity",
                assertThrows(CommandSyntaxException.class, () -> d.execute("tp 1 2 3", CommandSource.console(core))).getRawMessage().translationKey());
        // 다른 차원에 있는 대상은 옮기지 않고 실패한다
        p.dimension("minecraft:the_nether");
        var err = assertThrows(CommandSyntaxException.class, () -> d.execute("tp @s 1 64 1", src));
        assertNull(err.getRawMessage().translationKey());
        assertTrue(err.getRawMessage().collapseToString().contains("dimensions"));
        // 권한 부족
        assertThrows(CommandSyntaxException.class, () -> d.execute("tp @s 1 64 1", CommandSource.player(core, p).withPermission(1)));
    }

    @Test
    void teleportToEntityUsesDestinationPositionAndRotation() throws Exception {
        var m1 = new LinkedBlockingQueue<double[]>();
        var m2 = new LinkedBlockingQueue<double[]>();
        Player a = player("Alice", 0, 64, 0, 0, 0, m1);
        Player b = player("Bob", 100.5, 70, -30.25, 135, 20, m2);
        var src = CommandSource.console(core);
        assertEquals(1, TeleportCommand.toEntity(src, List.of(a), b));
        assertArrayEquals(new double[] {100.5, 70, -30.25, 135, 20}, next(m1), 1e-9);
        assertEquals(2, TeleportCommand.toEntity(src, List.of(a, b), b));
        assertArrayEquals(new double[] {100.5, 70, -30.25, 135, 20}, next(m1), 1e-9);
        assertArrayEquals(new double[] {100.5, 70, -30.25, 135, 20}, next(m2), 1e-9);
    }

    @Test
    void ordinaryEntitiesAreSelectedAndMoved() throws Exception {
        var world = core.world();
        Cow[] cows = new Cow[2];
        var done = new java.util.concurrent.CountDownLatch(1);
        world.runAt(0.5, 0.5, region -> {
            var lvl = region.level();
            for (int i = 0; i < 2; i++) {
                cows[i] = new Cow(lvl, lvl.nextEntityId());
                cows[i].moveTo(0.5, -60, 0.5, 0, 0);
                lvl.addFreshEntity(cows[i]);
            }
            cows[1].addTag("mover");
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        var d = core.commands().getDispatcher();
        var console = CommandSource.console(core);
        // 태그로만 고른다
        d.execute("tp @e[type=cow,tag=mover] 8.5 -60 8.5", console);
        awaitTrue(() -> cows[1].x() == 8.5);
        assertEquals(0.5, cows[0].x(), 0, "tag 가 없는 소는 그대로");
        assertTrue(cows[1].onGround());
        // 이어서 tag 가 없는 쪽(@e[tag=!mover])을 한 번에 옮기면서 회전도 준다
        d.execute("tp @e[type=cow,tag=!mover] 3.5 -60 3.5 45 10", console);
        awaitTrue(() -> cows[0].x() == 3.5);
        assertEquals(45, cows[0].yRot(), 1e-6);
        assertEquals(10, cows[0].xRot(), 1e-6);
        // 소끼리: 두 번째 소 위치로
        d.execute("tp @e[type=cow,tag=!mover] @e[type=cow,tag=mover,limit=1]", console);
        awaitTrue(() -> cows[0].x() == 8.5);
    }

    static void awaitTrue(java.util.function.BooleanSupplier c) throws InterruptedException {
        for (int i = 0; i < 200 && !c.getAsBoolean(); i++) Thread.sleep(50);
        assertTrue(c.getAsBoolean(), "시간 안에 조건이 이루어지지 않았다");
    }
}
