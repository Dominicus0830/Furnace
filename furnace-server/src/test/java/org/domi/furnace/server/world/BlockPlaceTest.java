// placeBlock: 빈 자리 설치+dirty, 플레이어 겹치면 거부; 면 오프셋 계산
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class BlockPlaceTest {

    @Test
    void placeOffset_sixFaces() {
        assertArrayEquals(new int[]{0, -1, 0}, Region.placeOffset(0)); // down
        assertArrayEquals(new int[]{0, 1, 0}, Region.placeOffset(1));  // up
        assertArrayEquals(new int[]{0, 0, -1}, Region.placeOffset(2)); // north
        assertArrayEquals(new int[]{0, 0, 1}, Region.placeOffset(3));  // south
        assertArrayEquals(new int[]{-1, 0, 0}, Region.placeOffset(4)); // west
        assertArrayEquals(new int[]{1, 0, 0}, Region.placeOffset(5));  // east
    }

    @Test
    void place_emptyAndRejectOverlap() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        int stone = reg.defaultStateId("minecraft:stone");
        RegionPos rp = new RegionPos(0, 0);
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        p.applyMove(0, -60, 0, 0, 0, true); // 발밑 블록 (0,-60,0) 점유
        boolean[] res = new boolean[2];
        sched.region(rp).execute(() -> {
            w.regionAt(rp).adopt(p);
            res[0] = w.regionAt(rp).placeBlock(5, -60, 5, stone);   // 빈 자리 → 설치
            res[1] = w.regionAt(rp).placeBlock(0, -60, 0, stone);   // 플레이어 겹침 → 거부
            // 확인
            ChunkColumn far = w.regionAt(rp).chunkAtBlocking(new ChunkPos(0, 0));
            assertEquals(stone, far.getBlock(5, -60, 5));            // 설치됨
            assertEquals(ChunkSection.AIR, far.getBlock(0, -60, 0)); // 거부(여전히 air)
            assertTrue(far.dirty());
        }).get();
        assertTrue(res[0], "빈 자리 설치 성공");
        assertFalse(res[1], "플레이어 겹침 거부");
        io.shutdown(); sched.close();
    }
}
