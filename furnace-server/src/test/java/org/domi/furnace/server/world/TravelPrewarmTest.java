// M10 차원 이동 미리 올리기: 미리 올린 뒤에는 탐색 범위에 만든 적 없는 청크가 있어도(디스크 확인 결과를 잠깐 믿는다) 빠른 길을 타고,
// 미리 올린 청크는 퇴출 유예와 무관하게 붙잡힌다(엔티티 틱 표는 건드리지 않는다).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.PortalTravel;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TravelPrewarmTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;

    @AfterEach
    void close() { if (sched != null) sched.close(); }

    @Test
    void prewarmMakesTravelResidentAndHoldsChunks() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = TestServers.ioPool();
        var type = DimensionBorderTest.TEST_NETHER;
        var src = new AnvilWorldSource(tmp, new RegionFileStore(type.storageDir(tmp)), new AnvilChunkCodec(reg, 0), new SuperflatWorldSource(reg, 0));
        World w = new World(type, src, new ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2, reg.directBits(), 7, io, io);
        w.worldDir(tmp);
        w.configurePersistence(Integer.MAX_VALUE, 100);
        w.startTicking();
        // 지옥문 → 네더 계획 꼴: 탐색 16+21 블록(7×7 청크), 생성 16 블록(3×3 청크)
        PortalTravel plan = new PortalTravel(type.levelKey(), 2, 0, 37, 16, lvl -> null);
        assertFalse(w.travelChunksResident(plan));
        w.prewarmTravel(plan);
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!w.travelChunksResident(plan)) {
            assertTrue(System.nanoTime() < deadline, "미리 올린 뒤에도 빠른 길을 못 탄다: 로드 " + w.loadedChunkCount());
            Thread.sleep(10);
        }
        assertEquals(9, w.loadedChunkCount(), "생성 반경 3×3 만 만들었다 — 탐색 링의 만든 적 없는 청크는 디스크에 없다고 기억");
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                assertTrue(w.ticketed(Regionizer.chunkKey(dx, dz)), "미리 올린 청크는 붙잡힌다");
                assertFalse(w.entityTicketed(dx, dz), "엔티티 틱 표는 건드리지 않는다");
            }
        }
    }
}
