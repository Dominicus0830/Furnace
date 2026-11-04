// 상대 이동 패킷(short)으로 못 담는 이동량(특히 8블록 직전 7.9999)이 반대 방향 이동 대신 텔레포트로 나가는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.MoveEntityPosRot;
import org.domi.furnace.server.net.protocol.play.TeleportEntity;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class DeltaMoveOverflowTest {

    @Test
    void fitsDelta_matchesShortEncodingRange() {
        assertTrue(Region.fitsDelta(0));
        assertTrue(Region.fitsDelta(7.99));
        assertTrue(Region.fitsDelta(-8.0));          // -32768 = Short.MIN_VALUE
        assertFalse(Region.fitsDelta(7.9999));       // 반올림하면 32768 → short로 넘침
        assertFalse(Region.fitsDelta(8.0));
        assertTrue(Region.fitsDelta(-8.0001));       // -32768.41 → -32768, 아직 표현 가능
        assertFalse(Region.fitsDelta(-8.0002));      // -32769.2 → 범위 밖
        assertFalse(Region.fitsDelta(100));
        // 판정이 true인 값은 인코딩해도 부호가 보존돼야 한다
        for (double d = -8.5; d <= 8.5; d += 0.0001) {
            if (!Region.fitsDelta(d)) continue;
            short s = (short) Math.round(d * 4096);
            assertTrue(d > 0 ? s >= 0 : d < 0 ? s <= 0 : s == 0, "부호가 뒤집힘 d=" + d + " s=" + s);
        }
    }

    private static List<Integer> ids(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<Integer> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            out.add(Types.readVarInt(b));
            b.release();
        }
        return out;
    }

    /** 관찰자가 보는 대상이 delta만큼 이동한 틱에서 나가는 이동 패킷 종류를 모은다. */
    private static List<Integer> observe(double delta) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, Executors.newSingleThreadExecutor());
        RegionPos r00 = new RegionPos(0, 0);
        EmbeddedChannel chObs = new ConcurrentEmbeddedChannel();
        Player obs = new Player(1, UUID.randomUUID(), "obs", chObs);
        Player tgt = new Player(2, UUID.randomUUID(), "tgt", new ConcurrentEmbeddedChannel());
        obs.publishPosition(0, -60, 0, 0, 0, true);
        obs.drainPending();
        tgt.publishPosition(5, -60, 5, 0, 0, true);
        tgt.drainPending();
        sched.region(r00).execute(() -> { w.regionAt(r00).adopt(obs); w.regionAt(r00).adopt(tgt); }).get();
        sched.region(r00).execute(() -> w.regionAt(r00).tick()).get();   // 서로 스폰(추적 시작)
        ids(chObs);                                                       // 스폰 관련 패킷 비우기
        tgt.publishPosition(5 + delta, -60, 5, 0, 0, true);
        sched.region(r00).execute(() -> w.regionAt(r00).tick()).get();   // 이동 반영 → broadcastMove
        List<Integer> seq = ids(chObs);
        sched.close();
        return seq;
    }

    @Test
    void nearEightBlocks_isSentAsTeleport_notOverflowedRelativeMove() throws Exception {
        List<Integer> seq = observe(7.9999);
        assertTrue(seq.contains(TeleportEntity.ID), "7.9999블록 이동은 텔레포트여야 한다: " + seq);
        assertFalse(seq.contains(MoveEntityPosRot.ID), "short 오버플로로 반대 방향 상대 이동이 나가면 안 된다: " + seq);
    }

    @Test
    void smallMove_stillUsesRelativeMove() throws Exception {
        List<Integer> seq = observe(1.5);
        assertTrue(seq.contains(MoveEntityPosRot.ID), "작은 이동은 상대 이동 패킷: " + seq);
        assertFalse(seq.contains(TeleportEntity.ID), seq.toString());
    }
}
