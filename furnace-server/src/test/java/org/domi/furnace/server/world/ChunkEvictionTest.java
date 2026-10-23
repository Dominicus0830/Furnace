// 유휴 청크 퇴출: 구독 없는 미편집 청크는 버리고(저장 안 함), 편집된 청크는 저장한 뒤 퇴출하며, 구독 중인 청크는 보관한다
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkEvictionTest {

    private static final RegionPos R00 = new RegionPos(0, 0);

    @Test
    void idleChunks_areEvicted_editedOnesSavedFirst_subscribedOnesKept(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, new AnvilChunkCodec(reg, 0), new SuperflatWorldSource(reg, 0));
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        var io = Executors.newFixedThreadPool(2);
        World w = new World(src, new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7, io);
        w.configurePersistence(1_000_000, 3);   // 자동 저장은 끄고(퇴출 경로만 본다) 퇴출 유예 3틱
        Player p = new Player(1, UUID.randomUUID(), "P", new EmbeddedChannel());
        ChunkPos clean = new ChunkPos(0, 0), edited = new ChunkPos(1, 0), watched = new ChunkPos(2, 0);
        w.subscribeChunk(watched, p);           // 계속 구독

        boolean[] has = new boolean[3];
        sched.region(R00).execute(() -> {
            Region r = w.regionAt(R00);
            r.chunkAt(clean);
            r.chunkAt(edited);
            r.breakBlock(16, -61, 0);            // edited 청크의 (0,-61,0) — 슈퍼플랫 풀 블록을 공기로
            r.chunkAt(watched);
            for (int i = 0; i < 10; i++) r.tick();   // 퇴출 유예(3틱) 경과
            has[0] = r.chunkLoaded(clean);
            has[1] = r.chunkLoaded(edited);
            has[2] = r.chunkLoaded(watched);
        }).get();
        // 퇴출 저장은 위 region 태스크 안의 tick()에서 이미 제출됐다 — IO 풀을 닫고 비워 결정적으로 기다린다(고정 sleep 금지).
        io.shutdown();
        assertTrue(io.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS), "IO 풀 미종료");

        assertFalse(has[0], "구독 없는 생성 청크 → 저장 후 퇴출");
        assertFalse(has[1], "구독 없는 편집 청크도 저장 후 퇴출");
        assertTrue(has[2], "구독 중인 청크는 보관");

        RegionFileStore check = new RegionFileStore(dir);
        assertNotNull(check.readChunk(0, 0), "새로 생성된 청크도 바닐라처럼 퇴출 전에 저장된다");
        assertNotNull(check.readChunk(1, 0), "편집된 청크는 퇴출 전에 저장된다");
        AnvilWorldSource reloaded = new AnvilWorldSource(dir, check, new AnvilChunkCodec(reg, 0), new SuperflatWorldSource(reg, 0));
        assertEquals(ChunkSection.AIR, reloaded.generate(1, 0).getBlock(0, -61, 0), "저장된 편집이 재로딩에서 보존돼야 한다");
        check.closeAll();
        store.closeAll();
        sched.close();
    }
}
