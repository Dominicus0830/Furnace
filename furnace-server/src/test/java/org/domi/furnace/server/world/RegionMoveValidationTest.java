// region 틱의 이동 검증 배선: 연결이 넣은 검증 대기 패킷을 소유 region 스레드가 순서대로 검증해 위치를 정하고, 되돌릴 땐 연결에 텔레포트를 시킨다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerMoveValidator;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class RegionMoveValidationTest {

    private static World world(FurnaceSchedulerImpl sched) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(2),
                sched, sched.regionThreadCount(), 16, 1, 1, reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
    }

    private static PlayerMoveValidator.Check check(double x, double z, float yaw) {
        return new PlayerMoveValidator.Check(true, true, x, -60, z, yaw, 10f);
    }

    @Test
    void regionValidatesQueuedMoves_rejectsTeleportHackAndHoldsUntilConnectionClears() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = world(sched);
        Player p = new Player(1, UUID.randomUUID(), "Dom", new ConcurrentEmbeddedChannel());
        List<double[]> teleports = new ArrayList<>();
        p.teleporter(teleports::add);
        p.publishPosition(0.5, -60, 0.5, 0, 0, true);
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertTrue(p.validatesMoves, "첫 틱부터 region 이 검증한다");
        assertEquals(0.5, p.x(), 1e-12);

        // 정상 걸음: 검증을 통과해 region 이 위치와 회전을 정한다(drainPending 은 쓰지 않는다)
        p.pushCheckedMove(check(0.7, 0.5, 90f), true);
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertEquals(0.7, p.x(), 1e-12);
        assertEquals(90f, p.yaw(), 1e-6);
        assertTrue(teleports.isEmpty());

        // 한 틱에 30 블록: 되돌리고 연결에 텔레포트를 시킨다
        p.pushCheckedMove(check(30.7, 0.5, 0f), true);
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertEquals(0.7, p.x(), 1e-12, "서버 위치로 되돌아간다");
        assertEquals(1, teleports.size());
        assertEquals(0.7, teleports.get(0)[0], 1e-12);
        assertTrue(p.moveRejectHold);

        // 연결이 텔레포트를 처리하기 전에 들어온 패킷은 버린다
        p.pushCheckedMove(check(0.9, 0.5, 0f), true);
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertEquals(0.7, p.x(), 1e-12);

        // 연결이 clearMoves(= 텔레포트 처리)를 하면 다시 받는다
        p.clearMoves();
        p.pushCheckedMove(check(0.9, 0.5, 0f), true);
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertEquals(0.9, p.x(), 1e-12);
        sched.close();
    }
    @Test
    void movePacketQueuedBeforeMountingIsNotRejectedAfterMount() throws Exception {
        // 봇 접속 시험(2026-11-04): 말에 탈 때마다 "moved wrongly" 와 되돌림 텔레포트가 나갔다.
        // 타기 직전에 보낸 걸음 패킷이 탄 뒤 검증되며 탈것 위 자리와 비교된 것 — 바닐라는 탄 동안 위치를 버리고 회전만 받는다.
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = world(sched);
        Player p = new Player(1, UUID.randomUUID(), "Dom", new ConcurrentEmbeddedChannel());
        List<double[]> teleports = new ArrayList<>();
        p.teleporter(teleports::add);
        p.publishPosition(0.5, -60, 0.5, 0, 0, true);
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> w.regionAt(rp).adopt(p)).get();
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        sched.region(rp).execute(() -> {
            var horse = new org.domi.furnace.server.entity.Horse(w.regionAt(rp).level(), 9999);
            horse.moveTo(3.5, -60, 3.5, 0, 0);
            p.setVehicleField(horse);
            p.setRidingPos(3.5, -59.16, 3.5);   // 탄 자리(positionRider)
        }).get();
        p.pushCheckedMove(check(0.6, 0.5, 45f), true);   // 타기 전에 보낸 걸음
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
        assertTrue(teleports.isEmpty(), "탄 동안 받은 위치 패킷으로 되돌리지 않는다");
        assertFalse(p.moveRejectHold);
        assertEquals(3.5, p.x(), 1e-9, "위치는 탈것 자리 그대로");
        assertEquals(45f, p.yaw(), 1e-6, "회전은 받는다");
        sched.close();
    }
}
