// 접속 직후 스폰 게이트가 풀릴 때 그 사이 서버 텔레포트(조인 이벤트 안·접속 직후 플러그인)를 스폰 자리로 되돌리지 않는지 실제 소켓으로 본다.
// Paper 는 게이트가 없어 스폰 텔레포트(id 1) 뒤 플러그인 텔레포트(id 2)가 마지막이고 서버 위치도 그 목적지다.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.domi.furnace.api.event.player.PlayerJoinEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.SetCenterChunk;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JoinTeleportGateTest {

    private static final double DX = 600.5, DY = -50.0, DZ = 600.5;   // 스폰과 다른 region(16청크 변)

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private NetworkServer server;
    private int port;
    private final FurnacePlugin plugin = new FurnacePlugin() {};

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().startTicking();
        org.domi.furnace.server.TestPluginContexts.init(plugin, core.plugins(), "jtg", tmp);
        plugin.setEnabled(true);
        ServerLoginContext login = new ServerLoginContext(false, false, false, true, false, false, true, 20, "t",
                null, null, core.lists(), null, null).core(core);
        server = new NetworkServer(0, -1, login, sched, core.world(), new EntityIdAllocator());
        port = server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().eventsImpl().unregisterAll(plugin);
        if (server != null) server.stop();
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private void onJoin(Consumer<PlayerJoinEvent> h) {
        core.plugins().eventsImpl().subscribe(plugin, PlayerJoinEvent.class, h);
    }

    private static Location dest() { return new Location("minecraft:overworld", DX, DY, DZ, 90f, 10f); }

    /** 받은 위치 동기화(x, z, id). */
    private record Tp(double x, double z, int id) {}

    /** 받은 프레임 중 위치 동기화와 첫 청크 중심. */
    private static final class Seen {
        final List<Tp> tps = new ArrayList<>();
        int[] firstCenter;
    }

    /**
     * millis 동안 읽는다. confirm 이면 위치 동기화마다 확인(teleport_confirm)을 돌려보낸다 — 실제 클라이언트처럼 스폰 텔레포트 확인과
     * 스폰 청크 전송 완료로 게이트가 풀린다. 아니면 300ms 대체 해제로 풀린다.
     */
    private static void read(TestClient c, Seen seen, long millis, boolean confirm) throws Exception {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            ByteBuf f = c.poll(50);
            if (f == null) continue;
            int id = Types.readVarInt(f);
            if (id == SyncPlayerPosition.ID) {
                double x = f.readDouble();
                f.readDouble();
                double z = f.readDouble();
                f.skipBytes(4 * 2 + 1);
                int tpId = Types.readVarInt(f);
                seen.tps.add(new Tp(x, z, tpId));
                if (confirm) c.write(0x00, b -> Types.writeVarInt(b, tpId));
            } else if (id == SetCenterChunk.ID && seen.firstCenter == null) {
                seen.firstCenter = new int[] {Types.readVarInt(f), Types.readVarInt(f)};
            }
        }
    }

    /** 서버 위치가 목적지로 자리 잡고(region 편입 뒤), 게이트 대체 해제 시간을 넘겨도 그대로인지. */
    private Player awaitServerAtDest(TestClient c) throws InterruptedException {
        Player p = null;
        for (int i = 0; i < 500; i++) {
            p = core.playerByUuid(c.uuid);
            if (p != null && p.ownerRegion != null && p.x() == DX && p.z() == DZ) break;
            Thread.sleep(10);
        }
        assertNotNull(p);
        assertEquals(DX, p.x(), "서버 위치 x");
        assertEquals(DZ, p.z(), "서버 위치 z");
        Thread.sleep(600);
        assertEquals(DX, p.x(), "게이트 해제 뒤에도 목적지");
        assertEquals(DZ, p.z(), "게이트 해제 뒤에도 목적지");
        return p;
    }

    private static void assertSpawnThenDest(Seen seen) {
        assertEquals(2, seen.tps.size(), "스폰 텔레포트 → 목적지 텔레포트, 그 뒤 재고정 없음: " + seen.tps);
        assertEquals(1, seen.tps.get(0).id());
        assertEquals(new Tp(DX, DZ, 2), seen.tps.get(1), "목적지가 마지막(Paper 처럼 id 2)");
    }

    /** 조인 이벤트 안 텔레포트 — 대체 해제(300ms) 경로. 이벤트 때 위치는 스폰이고, 청크 중심도 목적지부터. */
    @Test
    void joinEventTeleportSurvivesFallbackRelease() throws Exception {
        List<String> atJoin = new CopyOnWriteArrayList<>();
        onJoin(e -> {
            Location l = e.player().location();
            atJoin.add(l.x() + "," + l.z());
            assertTrue(e.player().teleportNow(dest()));
        });
        try (TestClient c = TestClient.join(port, "Alex")) {
            Seen seen = new Seen();
            read(c, seen, 1_500, false);
            assertSpawnThenDest(seen);
            Tp spawn = seen.tps.get(0);
            assertEquals(List.of(spawn.x() + "," + spawn.z()), atJoin, "조인 이벤트 때 위치는 스폰 자리");
            assertArrayEquals(new int[] {37, 37}, seen.firstCenter, "청크 중심은 처음부터 목적지(스폰 청크를 먼저 보내지 않는다)");
            awaitServerAtDest(c);
        }
    }

    /** 조인 이벤트 안 텔레포트 — 실제 클라이언트처럼 확인을 보내는 경로(스폰 확인 + 스폰 청크 전송 완료로 해제). */
    @Test
    void joinEventTeleportSurvivesConfirmedRelease() throws Exception {
        onJoin(e -> e.player().teleportNow(dest()));
        try (TestClient c = TestClient.join(port, "Alex")) {
            Seen seen = new Seen();
            read(c, seen, 1_500, true);
            assertSpawnThenDest(seen);
            awaitServerAtDest(c);
        }
    }

    /** 접속 직후(게이트가 풀리기 전) 다른 스레드에서 한 플러그인 텔레포트도 남는다. */
    @Test
    void teleportRightAfterJoinSurvivesGateRelease() throws Exception {
        try (TestClient c = TestClient.join(port, "Alex")) {
            Seen seen = new Seen();
            c.await(SyncPlayerPosition.ID);   // 스폰 텔레포트(id 1)
            seen.tps.add(new Tp(Double.NaN, Double.NaN, 1));
            Player p = core.playerByUuid(c.uuid);
            assertTrue(core.plugins().player(p).teleportNow(dest()));
            read(c, seen, 1_500, false);
            assertSpawnThenDest(seen);
            awaitServerAtDest(c);
        }
    }

    /** 텔레포트가 없으면 예전대로 게이트 해제 때 스폰 자리로 다시 고정한다(id 2) — 청크 사이 낙하 보정. */
    @Test
    void withoutTeleportTheGateStillRepinsToSpawn() throws Exception {
        try (TestClient c = TestClient.join(port, "Alex")) {
            Seen seen = new Seen();
            read(c, seen, 1_000, false);
            assertEquals(2, seen.tps.size(), seen.tps.toString());
            Tp spawn = seen.tps.get(0);
            assertEquals(1, spawn.id());
            assertEquals(new Tp(spawn.x(), spawn.z(), 2), seen.tps.get(1));
        }
    }
}
