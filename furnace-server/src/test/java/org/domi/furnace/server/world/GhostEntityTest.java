// 퇴장/원거리 이동한 플레이어가 다른 region 관찰자에게 유령으로 남지 않는지 + 핸드오프 틈엔 유지되는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityView;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class GhostEntityTest {

    private static List<Integer> ids(EmbeddedChannel ch) {
        List<Integer> ids = new ArrayList<>();
        for (ByteBuf b : BlockBreakTest.drain(ch)) { ids.add(Types.readVarInt(b)); b.release(); }
        return ids;
    }

    private static World world(FurnaceSchedulerImpl sched, int threads) {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, threads, 1, 5, 5, reg.directBits(), 7); // regionSize 1 → 16블록 셀
    }

    private static void tick(FurnaceSchedulerImpl s, World w, RegionPos rp) throws Exception {
        s.region(rp).execute(() -> w.regionAt(rp).tick()).get();
    }

    @Test
    void crossRegionObserver_despawnsPlayerWhoDisconnected() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = world(sched, 2);
        EmbeddedChannel chA = new EmbeddedChannel();
        Player a = new Player(1, UUID.randomUUID(), "A", chA);
        a.applyMove(0, -60, 0, 0, 0, true);
        Player b = new Player(2, UUID.randomUUID(), "B", new EmbeddedChannel());
        b.applyMove(16, -60, 0, 0, 0, true);
        RegionPos ra = a.regionPos(1), rb = b.regionPos(1);
        assertNotEquals(ra, rb);
        sched.region(ra).execute(() -> w.regionAt(ra).adopt(a)).get();
        sched.region(rb).execute(() -> w.regionAt(rb).adopt(b)).get();
        tick(sched, w, rb);
        tick(sched, w, ra);
        assertTrue(a.tracked().containsKey(2));

        b.markDisconnected();
        tick(sched, w, rb);
        tick(sched, w, ra);

        assertFalse(a.tracked().containsKey(2), "퇴장한 B가 A에게 유령으로 남음");
        assertTrue(a.trackedUuids().isEmpty());
        List<Integer> sent = ids(chA);
        assertTrue(sent.contains(0x42), "RemoveEntities");
        assertTrue(sent.contains(0x3D), "PlayerInfoRemove(탭 제거)");
        sched.close();
    }

    @Test
    void observer_despawnsPlayerWhoTeleportsFarAway() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        World w = world(sched, 1);
        RegionPos r00 = new RegionPos(0, 0);
        Player a = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        Player b = new Player(2, UUID.randomUUID(), "B", new EmbeddedChannel());
        a.applyMove(0, -60, 0, 0, 0, true);
        b.applyMove(10, -60, 0, 0, 0, true);
        sched.region(r00).execute(() -> { w.regionAt(r00).adopt(a); w.regionAt(r00).adopt(b); }).get();
        tick(sched, w, r00);
        assertTrue(a.tracked().containsKey(2));

        b.publishPosition(5_000_000, -60, 0, 0, 0, true);
        for (int i = 0; i < 4; i++) {
            tick(sched, w, r00);
            tick(sched, w, b.regionPos(1));
        }
        assertFalse(a.tracked().containsKey(2), "시야 밖으로 순간이동한 B가 남음");
        sched.close();
    }

    @Test
    void transitEntity_isCandidateUntilAdopted_andDeduped() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        World w = world(sched, 1);
        EntityView v = new EntityView(2, UUID.randomUUID(), "B", 0, -60, 0, 0, 0, true);
        w.markTransit(v);
        assertEquals(1, w.candidatesAround(new RegionPos(0, 0), 1).size());
        w.publishSnapshot(new RegionPos(0, 0), List.of(v)); // 새 region이 공개 → 중복 제거
        assertEquals(1, w.candidatesAround(new RegionPos(0, 0), 1).size());
        w.clearTransit(2);
        assertEquals(1, w.candidatesAround(new RegionPos(0, 0), 1).size());
        sched.close();
    }
}
