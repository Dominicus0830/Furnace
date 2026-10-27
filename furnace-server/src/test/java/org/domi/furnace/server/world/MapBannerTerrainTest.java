// 사용자 테스트 D4: 지도 깃발 표식이 다음 지도 갱신(checkBanners)에서 지워지던 것 — 지형 어댑터가 블록 엔티티를 로컬 좌표로 찾았다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class MapBannerTerrainTest {

    @Test
    void bannerOutsideChunkZeroIsFoundByMapTerrain() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        w.setRegistries(reg, items);
        int banner = reg.defaultStateId("minecraft:red_banner");
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            assertTrue(r.placeBlock(20, -60, 21, banner));
            var b = r.new MapTerrain().bannerAt(20, -60, 21);
            assertNotNull(b, "청크 (1,1) 의 깃발을 찾아야 표식이 남는다");
            assertEquals(new org.domi.furnace.server.map.MapData.Banner(20, -60, 21, b.color(), null), b);
        }).get();
        io.shutdown();
        sched.close();
    }
}
