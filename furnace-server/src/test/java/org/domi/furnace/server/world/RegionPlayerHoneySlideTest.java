// 꿀 블록 옆면을 미끄러지는 플레이어: 바닐라 HoneyBlock.maybeDoSlideEffects(플레이어는 생물) — 레벨 난수 1/5 로 엔티티 이벤트 53(자기 포함).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.EntityEvent;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class RegionPlayerHoneySlideTest {

    /** x=8 에 꿀 기둥(y -60..-50), 플레이어는 x 에 서서 매 틱 0.1 씩 내려간다(공중). 받은 이벤트 53 수. */
    private static int slideEvents(double x) throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        try {
            World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
            w.setRegistries(reg, ItemRegistry.load("items/1.21.1.txt"));
            RegionPos rp = new RegionPos(0, 0);
            var ch = new ConcurrentEmbeddedChannel();
            Player p = new Player(1, UUID.randomUUID(), "Honey", ch);
            p.teleporter(t -> {});
            double y = -51.5;
            p.publishPosition(x, y, 8.5, 0, 0, false);
            sched.region(rp).execute(() -> {
                Region r = w.regionAt(rp);
                r.chunkAtBlocking(new ChunkPos(0, 0));
                for (int by = -60; by <= -50; by++) r.placeBlock(8, by, 8, reg.defaultStateId("minecraft:honey_block"));
                r.adopt(p);
            }).get();
            int n = 0;
            for (int i = 0; i < 40; i++) {
                y -= 0.1;
                p.pushMove(x, y, 8.5, false);   // 이동 패킷(서버 move → checkInsideBlocks)
                p.publishPosition(x, y, 8.5, 0, 0, false);
                sched.region(rp).execute(() -> w.regionAt(rp).tick()).get();
                n += drain(ch, p.entityId());
            }
            return n;
        } finally {
            io.shutdown();
            sched.close();
        }
    }

    private static int drain(ConcurrentEmbeddedChannel ch, int self) {
        ch.runPendingTasks();
        int n = 0;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            if (o instanceof EntityEvent e) {
                if (e.entityId() == self && e.event() == 53) n++;
            } else if (o instanceof ByteBuf bb) {
                if (bb.readableBytes() > 0 && Types.readVarInt(bb) == EntityEvent.ID && bb.readableBytes() == 5 && bb.readInt() == self && bb.readByte() == 53) n++;
                bb.release();
            }
        }
        return n;
    }

    @Test
    void slidingPlayerGetsSlideParticleEvents() throws Exception {
        // 꿀 칸(x 8..9)에 몸통이 걸치고(x - 0.3 < 9) 가운데에서 0.4375 + 0.3 보다 멀다(x > 9.2375)
        assertTrue(slideEvents(9.25) > 0, "미끄러지면 1/5 확률로 이벤트 53");
    }

    @Test
    void playerAwayFromHoneyGetsNone() throws Exception {
        assertEquals(0, slideEvents(9.4), "꿀 칸에 걸치지 않으면 없음");
    }
}
