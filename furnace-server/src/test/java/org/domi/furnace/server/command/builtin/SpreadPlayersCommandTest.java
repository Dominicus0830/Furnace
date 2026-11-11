// /spreadplayers: 바닐라와 같은 난수 호출 순서(초기 위치 x→z, 안전하지 않은 칸 재배치), 최소 거리, 실패 오류 키, 팀 유지, 실제 월드의 소 퍼뜨리기.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.Vec2Argument;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpreadPlayersCommandTest {

    @TempDir Path tmp;

    private static final SpreadPlayersCommand.Terrain FLAT = new SpreadPlayersCommand.Terrain() {
        @Override public int spawnY(int x, int z) { return 64; }
        @Override public boolean safe(int x, int z) { return true; }
    };

    @Test
    void initialPositionsDrawXThenZPerPosition() {
        var a = new LegacyRandomSource(7);
        var b = new LegacyRandomSource(7);
        var ps = SpreadPlayersCommand.createInitialPositions(a, 3, -10, -20, 30, 40);
        for (int i = 0; i < 3; i++) {
            double x = b.nextDouble() * 40 + -10;
            double z = b.nextDouble() * 60 + -20;
            assertEquals(x, ps[i].x, 0.0);
            assertEquals(z, ps[i].z, 0.0);
        }
        assertEquals(b.nextInt(), a.nextInt(), "그 뒤 난수 상태도 같다(더 쓰거나 덜 쓰지 않는다)");
        // 폭이 0 이면 난수를 쓰지 않는다
        var c = new LegacyRandomSource(7);
        var d = new LegacyRandomSource(7);
        var flat = SpreadPlayersCommand.createInitialPositions(c, 1, 5, 5, 5, 5);
        assertEquals(5, flat[0].x, 0.0);
        assertEquals(d.nextInt(), c.nextInt());
    }

    @Test
    void unsafeColumnsAreRerolledInOrder() throws Exception {
        // x < 0 인 칸은 안전하지 않다 — 첫 (x, z) 쌍이 음수 x 면 다시 뽑는다. 기대값은 같은 시드의 난수열에서 x >= 0 인 첫 쌍.
        var terrain = new SpreadPlayersCommand.Terrain() {
            @Override public int spawnY(int x, int z) { return 64; }
            @Override public boolean safe(int x, int z) { return x >= 0; }
        };
        for (long seed = 1; seed <= 20; seed++) {
            var rng = new LegacyRandomSource(seed);
            var ref = new LegacyRandomSource(seed);
            double ex, ez;
            do {
                ex = ref.nextDouble() * 100 - 50;
                ez = ref.nextDouble() * 100 - 50;
            } while (Math.floor(ex) < 0);
            var ps = SpreadPlayersCommand.createInitialPositions(rng, 1, -50, -50, 50, 50);
            SpreadPlayersCommand.spreadPositions(0, 0, 4.0, terrain, rng, -50, -50, 50, 50, ps, false);
            assertEquals(ex, ps[0].x, 0.0, "seed " + seed);
            assertEquals(ez, ps[0].z, 0.0, "seed " + seed);
        }
    }

    @Test
    void spreadKeepsMinimumDistanceInsideBounds() throws Exception {
        for (long seed = 1; seed <= 10; seed++) {
            var rng = new LegacyRandomSource(seed);
            var ps = SpreadPlayersCommand.createInitialPositions(rng, 10, -50, -50, 50, 50);
            SpreadPlayersCommand.spreadPositions(0, 0, 5.0, FLAT, rng, -50, -50, 50, 50, ps, false);
            for (int i = 0; i < ps.length; i++) {
                assertTrue(ps[i].x >= -50 && ps[i].x <= 50 && ps[i].z >= -50 && ps[i].z <= 50);
                for (int j = i + 1; j < ps.length; j++) assertTrue(ps[i].dist(ps[j]) >= 5.0 - 1e-9, "seed " + seed + " " + i + "," + j);
            }
        }
    }

    @Test
    void deterministicForFixedSeed() throws Exception {
        double[][] first = null;
        for (int run = 0; run < 2; run++) {
            var rng = new LegacyRandomSource(12345);
            var ps = SpreadPlayersCommand.createInitialPositions(rng, 4, -30, -30, 30, 30);
            SpreadPlayersCommand.spreadPositions(0, 0, 8.0, FLAT, rng, -30, -30, 30, 30, ps, false);
            double[][] cur = new double[4][];
            for (int i = 0; i < 4; i++) cur[i] = new double[] {ps[i].x, ps[i].z};
            if (first == null) first = cur;
            else assertArrayEquals(first, cur);
        }
    }

    @Test
    void failsWhenAreaTooSmall() {
        var rng = new LegacyRandomSource(1);
        var ps = SpreadPlayersCommand.createInitialPositions(rng, 5, -1, -1, 1, 1);
        var e = assertThrows(CommandSyntaxException.class,
                () -> SpreadPlayersCommand.spreadPositions(0, 0, 100.0, FLAT, rng, -1, -1, 1, 1, ps, false));
        assertEquals("commands.spreadplayers.failed.entities", e.getRawMessage().translationKey());
        var rng2 = new LegacyRandomSource(1);
        var ps2 = SpreadPlayersCommand.createInitialPositions(rng2, 5, -1, -1, 1, 1);
        var e2 = assertThrows(CommandSyntaxException.class,
                () -> SpreadPlayersCommand.spreadPositions(0, 0, 100.0, FLAT, rng2, -1, -1, 1, 1, ps2, true));
        assertEquals("commands.spreadplayers.failed.teams", e2.getRawMessage().translationKey());
    }

    @Test
    void placementUsesBlockCenterAndAveragesNearestDistance() {
        var p1 = new Player(1, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        var p2 = new Player(2, UUID.randomUUID(), "B", new ConcurrentEmbeddedChannel());
        var ps = new SpreadPlayersCommand.Position[] {new SpreadPlayersCommand.Position(), new SpreadPlayersCommand.Position()};
        ps[0].x = 1.7;
        ps[0].z = -2.2;
        ps[1].x = 4.7;
        ps[1].z = 1.8;
        var out = new ArrayList<SpreadPlayersCommand.Placement>();
        double avg = SpreadPlayersCommand.place(List.of(p1, p2), FLAT, ps, false, out);
        assertEquals(1.5, out.get(0).x());
        assertEquals(-2.5, out.get(0).z(), "floor(-2.2) = -3, +0.5");
        assertEquals(64, out.get(0).y());
        assertEquals(4.5, out.get(1).x());
        assertEquals(1.5, out.get(1).z());
        assertEquals(5.0, avg, 1e-12, "서로가 가장 가까운 상대이므로 평균은 둘 사이 거리");
        // 한 명이면 평균 0
        var one = new ArrayList<SpreadPlayersCommand.Placement>();
        assertEquals(0.0, SpreadPlayersCommand.place(List.of(p1), FLAT, ps, false, one));
    }

    @Test
    void respectTeamsPutsTeamlessTargetsAtTheSamePosition() {
        var a = new Player(1, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        var b = new Player(2, UUID.randomUUID(), "B", new ConcurrentEmbeddedChannel());
        var c = new Player(3, UUID.randomUUID(), "C", new ConcurrentEmbeddedChannel());
        var ps = new SpreadPlayersCommand.Position[] {new SpreadPlayersCommand.Position()};
        ps[0].x = 10.2;
        ps[0].z = 20.9;
        var out = new ArrayList<SpreadPlayersCommand.Placement>();
        double avg = SpreadPlayersCommand.place(List.of(a, b, c), FLAT, ps, true, out);
        assertEquals(3, out.size());
        for (var p : out) {
            assertEquals(10.5, p.x());
            assertEquals(20.5, p.z());
        }
        assertTrue(Double.isInfinite(avg), "다른 위치가 없으면 바닐라도 Double.MAX_VALUE 를 대상 수만큼 더해 무한대가 된다");
    }

    @Test
    void invalidMaxHeightError() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            var src = CommandSource.console(core);
            var e = assertThrows(CommandSyntaxException.class, () -> SpreadPlayersCommand.spread(src, new Vec2Argument.Vec2(0, 0), 1, 10, -1000, false, List.<Rider>of(new Player(1, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel()))));
            assertEquals("commands.spreadplayers.failed.invalid.height", e.getRawMessage().translationKey());
        } finally {
            sched.close();
        }
    }

    @Test
    void spreadsCowsInRealWorld() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            Cow[] cows = new Cow[4];
            var done = new CountDownLatch(1);
            core.world().runAt(0.5, 0.5, region -> {
                var lvl = region.level();
                for (int i = 0; i < cows.length; i++) {
                    cows[i] = new Cow(lvl, lvl.nextEntityId());
                    cows[i].moveTo(0.5, -60, 0.5, 0, 0);
                    lvl.addFreshEntity(cows[i]);
                }
                done.countDown();
            });
            assertTrue(done.await(10, TimeUnit.SECONDS));
            core.commands().performPrefixedCommandAsync(CommandSource.console(core), "spreadplayers 0 0 6 20 false @e[type=cow]").join();
            TeleportCommandTest.awaitTrue(() -> {
                for (Cow c : cows) if (c.x() == 0.5 && c.z() == 0.5) return false;
                return true;
            });
            for (Cow c : cows) {
                assertTrue(Math.abs(c.x()) <= 20.5 && Math.abs(c.z()) <= 20.5, "범위 안: " + c.x() + "," + c.z());
                assertEquals(-60.0, c.y(), 0.0, "슈퍼플랫 지면(-61) 바로 위");
                assertEquals(0.5, c.x() - Math.floor(c.x()), 0.0, "칸 가운데");
            }
            for (int i = 0; i < cows.length; i++) {
                for (int j = i + 1; j < cows.length; j++) {
                    double dx = cows[i].x() - cows[j].x(), dz = cows[i].z() - cows[j].z();
                    assertTrue(Math.sqrt(dx * dx + dz * dz) >= 6.0 - 1.5, "간격(칸 가운데로 내림 오차 허용)");
                }
            }
        } finally {
            sched.close();
        }
    }
}
