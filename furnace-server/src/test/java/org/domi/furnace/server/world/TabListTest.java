// 퇴장한 플레이어가 다른 플레이어 탭 목록에 영구히 남지 않는지, 단순히 시야 밖으로 나간 경우엔 탭을 유지하는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.PlayerInfoRemove;
import org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate;
import org.domi.furnace.server.net.protocol.play.RemoveEntities;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TabListTest {

    private static final RegionPos A = new RegionPos(0, 0);
    private static final RegionPos B = new RegionPos(1, 0);   // region-size 16청크 = 256블록 → x=300은 B

    private FurnaceSchedulerImpl sched;
    private World w;
    private EmbeddedChannel chObs;
    private Player obs;
    private Player tgt;

    private void setup(double tgtX) throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 20, 20, reg.directBits(), 7, Executors.newSingleThreadExecutor());
        chObs = new EmbeddedChannel();
        obs = new Player(1, UUID.randomUUID(), "obs", chObs);
        tgt = new Player(2, UUID.randomUUID(), "tgt", new EmbeddedChannel());
        obs.publishPosition(10, -60, 10, 0, 0, true);
        obs.drainPending();
        tgt.publishPosition(tgtX, -60, 10, 0, 0, true);
        tgt.drainPending();
        sched.region(A).execute(() -> w.regionAt(A).adopt(obs)).get();
        RegionPos tr = RegionGrid.regionPos(tgtX, 10, 16);
        sched.region(tr).execute(() -> w.regionAt(tr).adopt(tgt)).get();
    }

    @AfterEach
    void close() {
        if (sched != null) sched.close();
    }

    private void tick(RegionPos rp) throws Exception {
        sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
    }

    private List<Integer> ids() {
        chObs.runPendingTasks();
        List<Integer> out = new ArrayList<>();
        Object o;
        while ((o = chObs.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            out.add(Types.readVarInt(b));
            b.release();
        }
        return out;
    }

    /** 관찰자가 대상을 추적(스폰+탭 등록)하도록 몇 틱 돌린다. */
    private void meet(RegionPos targetRegion) throws Exception {
        tick(targetRegion);
        tick(A);
        tick(A);
        List<Integer> seen = ids();
        assertTrue(seen.contains(PlayerInfoUpdate.ID), "탭에 올라가야 한다: " + seen);
    }

    @Test
    void sameRegionDisconnect_removesEntityAndTabEntry() throws Exception {
        setup(30);                                  // 같은 region A
        meet(A);
        tgt.markDisconnected();
        tick(A);
        List<Integer> seq = ids();
        assertTrue(seq.contains(RemoveEntities.ID), "엔티티 디스폰: " + seq);
        assertTrue(seq.contains(PlayerInfoRemove.ID), "탭 제거(퇴장한 플레이어가 탭에 남으면 안 된다): " + seq);
    }

    @Test
    void crossRegionDisconnect_removesEntityAndTabEntry_afterGrace() throws Exception {
        setup(300);                                 // 인접 region B
        meet(B);
        tgt.markDisconnected();
        tick(B);                                    // 퇴장 처리 — 관찰자 region이 달라 즉시 디스폰 대상 아님
        List<Integer> all = new ArrayList<>();
        for (int t = 0; t < 5; t++) { tick(A); all.addAll(ids()); }
        assertTrue(all.contains(RemoveEntities.ID), "엔티티 디스폰: " + all);
        assertTrue(all.contains(PlayerInfoRemove.ID), "탭 제거: " + all);
    }

    @Test
    void outOfEntityRange_keepsTabEntry_thenRemovedOnDisconnect() throws Exception {
        setup(300);
        meet(B);
        // 엔티티 가시 범위(20청크=320블록)를 벗어나되 후보(인접 region)엔 남는 위치로 이동 — 접속은 유지
        tgt.publishPosition(340, -60, 10, 0, 0, true);
        tick(B);
        tick(A);
        List<Integer> away = ids();
        assertTrue(away.contains(RemoveEntities.ID), "시야 밖이면 엔티티는 디스폰: " + away);
        assertFalse(away.contains(PlayerInfoRemove.ID), "접속 중인 플레이어는 탭에서 빼면 안 된다: " + away);

        tgt.markDisconnected();                     // 이제 실제로 퇴장
        tick(B);
        List<Integer> all = new ArrayList<>();
        for (int t = 0; t < 5; t++) { tick(A); all.addAll(ids()); }
        assertTrue(all.contains(PlayerInfoRemove.ID), "시야 밖에서 퇴장해도 탭 항목은 결국 제거돼야 한다: " + all);
        assertFalse(all.contains(RemoveEntities.ID), "엔티티는 이미 디스폰됨: " + all);
    }
}
