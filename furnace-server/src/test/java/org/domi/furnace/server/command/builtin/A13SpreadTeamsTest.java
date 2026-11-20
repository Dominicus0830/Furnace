// A13-03: respectTeams=true 일 때 서로 다른 팀의 플레이어는 서로 다른 위치에 놓여야 한다(바닐라 setPlayerPositions 는 팀별 위치).
// 같은 팀은 한 위치를 나누고, 팀 없는 대상은 한 그룹이다.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A13SpreadTeamsTest {

    @TempDir Path tmp;

    private static final SpreadPlayersCommand.Terrain FLAT = new SpreadPlayersCommand.Terrain() {
        @Override public int spawnY(int x, int z) { return 64; }
        @Override public boolean safe(int x, int z) { return true; }
    };

    @Test
    void differentTeamsGetDifferentPositions() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            var a = new Player(1, UUID.nameUUIDFromBytes("A".getBytes()), "A", new ConcurrentEmbeddedChannel());
            var b = new Player(2, UUID.nameUUIDFromBytes("B".getBytes()), "B", new ConcurrentEmbeddedChannel());
            for (String c : new String[]{"team add red", "team add blue", "team join red A", "team join blue B"})
                core.commands().performPrefixedCommandAsync(CommandSource.console(core), c).get(10, TimeUnit.SECONDS);
            a.scoreboard(core.scoreboard());
            b.scoreboard(core.scoreboard());
            var ps = new SpreadPlayersCommand.Position[] {new SpreadPlayersCommand.Position(), new SpreadPlayersCommand.Position()};
            ps[0].x = 10.2; ps[0].z = 20.9;
            ps[1].x = -40.2; ps[1].z = 5.1;
            var out = new ArrayList<SpreadPlayersCommand.Placement>();
            SpreadPlayersCommand.place(List.of(a, b), FLAT, ps, true, out);
            assertNotEquals(out.get(0).x(), out.get(1).x(), "팀이 다르면 위치도 달라야 한다(같은 팀만 한 위치를 나눈다)");
        } finally {
            sched.close();
        }
    }

    @Test
    void sameTeamSharesPositionAndTeamlessFormOneGroup() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            var a = new Player(1, UUID.nameUUIDFromBytes("A".getBytes()), "A", new ConcurrentEmbeddedChannel());
            var b = new Player(2, UUID.nameUUIDFromBytes("B".getBytes()), "B", new ConcurrentEmbeddedChannel());
            var c = new Player(3, UUID.nameUUIDFromBytes("C".getBytes()), "C", new ConcurrentEmbeddedChannel());
            var d = new Player(4, UUID.nameUUIDFromBytes("D".getBytes()), "D", new ConcurrentEmbeddedChannel());
            for (String cmd : new String[]{"team add red", "team join red A", "team join red B"})
                core.commands().performPrefixedCommandAsync(CommandSource.console(core), cmd).get(10, TimeUnit.SECONDS);
            for (Player p : List.of(a, b, c, d)) p.scoreboard(core.scoreboard());
            // A·B = red, C·D = 팀 없음 → 위치 둘
            var ps = new SpreadPlayersCommand.Position[] {new SpreadPlayersCommand.Position(), new SpreadPlayersCommand.Position()};
            ps[0].x = 10.2; ps[0].z = 20.9;
            ps[1].x = -40.2; ps[1].z = 5.1;
            var out = new ArrayList<SpreadPlayersCommand.Placement>();
            SpreadPlayersCommand.place(List.of(a, b, c, d), FLAT, ps, true, out);
            assertEquals(out.get(0).x(), out.get(1).x());
            assertEquals(out.get(2).x(), out.get(3).x());
            assertNotEquals(out.get(0).x(), out.get(2).x());
        } finally {
            sched.close();
        }
    }
}
