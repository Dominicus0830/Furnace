// breakBlock이 소유 청크를 air로 + 교차region 구독자에게 BlockUpdate 전송하는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class BlockBreakTest {

    static List<ByteBuf> drain(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<ByteBuf> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) out.add((ByteBuf) o);
        return out;
    }

    @Test
    void breakBlock_clearsAndBroadcastsToSubscriber() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkWorkerPool pool = new ChunkWorkerPool(1);
        World w = new World(new SuperflatWorldSource(reg, 0), pool,
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7);
        RegionPos rp = new RegionPos(0, 0);
        ChunkPos cp = new ChunkPos(0, 0);
        EmbeddedChannel ch = new EmbeddedChannel();
        Player viewer = new Player(1, UUID.randomUUID(), "V", ch);
        int grass = reg.defaultStateId("minecraft:grass_block");

        sched.region(rp).execute(() -> w.regionAt(rp).openChunk(cp, viewer)).get(); // (0,0) 청크 구독
        pool.submit(() -> null).get();                 // 직렬화 완료 → 소유 스레드로 전달 예약됨
        sched.region(rp).execute(() -> { }).get();     // 전달 태스크 소진
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            assertEquals(grass, r.chunkAt(cp).getBlock(0, -61, 0)); // 부수기 전 grass
            r.breakBlock(0, -61, 0);
            assertEquals(ChunkSection.AIR, r.chunkAt(cp).getBlock(0, -61, 0)); // air로
        }).get();

        List<ByteBuf> out = drain(ch);
        assertEquals(2, out.size(), "청크 + BlockUpdate");
        assertEquals(0x27, Types.readVarInt(out.get(0))); // 청크가 먼저
        ByteBuf upd = out.get(1);
        assertEquals(BlockUpdate.ID, Types.readVarInt(upd));
        assertArrayEquals(new int[]{0, -61, 0}, Types.readBlockPos(upd));
        assertEquals(ChunkSection.AIR, Types.readVarInt(upd));
        out.forEach(ByteBuf::release);
        sched.close();
    }
}
