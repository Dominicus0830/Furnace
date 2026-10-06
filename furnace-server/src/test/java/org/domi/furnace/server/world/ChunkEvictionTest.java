// 구독자 없는 미편집 청크는 퇴출, 편집된 청크/구독 중 청크는 보관되는지 + 이동 델타 오버플로 경계 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkEvictionTest {

    private static final RegionPos R00 = new RegionPos(0, 0);

    @Test
    void idleCleanChunk_isEvicted_dirtyAndSubscribedAreKept() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7);
        Player p = new Player(1, UUID.randomUUID(), "P", new EmbeddedChannel());
        ChunkPos clean = new ChunkPos(0, 0), edited = new ChunkPos(1, 0), watched = new ChunkPos(2, 0);

        boolean[] has = new boolean[3];
        sched.region(R00).execute(() -> {
            Region r = w.regionAt(R00);
            r.openChunk(clean, p);  r.closeChunk(clean, p);
            r.openChunk(edited, p); r.breakBlock(16, -61, 0); r.closeChunk(edited, p);
            r.openChunk(watched, p);                                  // 계속 구독
            for (int i = 0; i < 800; i++) r.tick();                   // 퇴출 유예(600틱) 경과
            has[0] = r.chunkLoaded(clean);
            has[1] = r.chunkLoaded(edited);
            has[2] = r.chunkLoaded(watched);
        }).get();

        assertFalse(has[0], "구독 없고 미편집 → 퇴출");
        assertTrue(has[1], "편집된 청크는 영속화 전까지 보관");
        assertTrue(has[2], "구독 중인 청크는 보관");
        sched.close();
    }

    @Test
    void breakBlock_ignoresUnloadedChunkAndOutOfRangeY() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7);
        boolean[] created = new boolean[1];
        sched.region(R00).execute(() -> {
            Region r = w.regionAt(R00);
            r.breakBlock(5000, -61, 5000);       // 로드 안 된 청크
            created[0] = r.chunkLoaded(ChunkPos.ofBlock(5000, 5000));
            r.chunkAt(new ChunkPos(0, 0));
            r.breakBlock(0, 1000, 0);            // y 범위 밖 — 예외 없이 무시
            r.breakBlock(0, -1000, 0);
        }).get();
        assertFalse(created[0], "파괴 요청이 청크를 만들면 안 된다");
        sched.close();
    }

    @Test
    void fitsDelta_boundary() {
        assertTrue(Region.fitsDelta(7.99));
        assertFalse(Region.fitsDelta(7.9999), "short 오버플로 → 텔레포트로 보내야");
        assertFalse(Region.fitsDelta(-8.5));
        assertTrue(Region.fitsDelta(0));
    }
}
