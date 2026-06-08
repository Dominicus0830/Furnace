// breakBlock이 소유 청크를 air로 + 교차region 구독자에게 BlockUpdate 전송하는지 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
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

    @Test
    void breakBlock_clearsAndBroadcastsToSubscriber() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, java.util.concurrent.Executors.newSingleThreadExecutor());
        RegionPos rp = new RegionPos(0, 0);
        ChunkPos cp = new ChunkPos(0, 0);
        EmbeddedChannel ch = new EmbeddedChannel();
        Player viewer = new Player(1, UUID.randomUUID(), "V", ch);
        w.subscribeChunk(cp, viewer); // viewer가 (0,0) 청크 구독
        int grass = reg.defaultStateId("minecraft:grass_block");

        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            assertEquals(grass, r.chunkAt(cp).getBlock(0, -61, 0)); // 부수기 전 grass
            r.breakBlock(0, -61, 0);
            assertEquals(ChunkSection.AIR, r.chunkAt(cp).getBlock(0, -61, 0)); // air로
        }).get();

        // viewer 채널이 BlockUpdate를 받았는지(eventLoop 마샬 → flush 처리)
        ch.runPendingTasks();
        ByteBuf out = ch.readOutbound();
        assertNotNull(out, "BlockUpdate 전송돼야");
        assertEquals(BlockUpdate.ID, Types.readVarInt(out));
        int[] pos = Types.readBlockPos(out);
        assertArrayEquals(new int[]{0, -61, 0}, pos);
        assertEquals(ChunkSection.AIR, Types.readVarInt(out));
        out.release();
        sched.close();
    }
}
