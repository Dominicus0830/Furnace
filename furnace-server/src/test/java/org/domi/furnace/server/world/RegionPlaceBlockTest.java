// Region.placeBlock 대상 칸 검사: 채워진 칸은 거부, 공기/교체 가능 블록은 허용, 블록엔티티 고아 방지
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class RegionPlaceBlockTest {

    @Test
    void placeOnOccupied_isRejected_andChestEntityStaysConsistent() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        w.setRegistries(reg, items);
        int chest = reg.defaultStateId("minecraft:chest");
        int stone = reg.defaultStateId("minecraft:stone");
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            assertTrue(r.placeBlock(3, -60, 3, chest), "공기 칸엔 설치돼야");
            assertFalse(r.placeBlock(3, -60, 3, stone), "체스트 위에 덮어쓰면 안 된다");
            assertNotNull(r.chestAt(3, -60, 3), "거부됐으니 체스트 블록엔티티가 남아야");
            assertEquals(chest, r.chunkAt(new ChunkPos(0, 0)).getBlock(3, -60, 3));
            int ground = r.chunkAt(new ChunkPos(0, 0)).getBlock(5, -61, 5);
            assertNotEquals(ChunkSection.AIR, ground);
            assertFalse(r.placeBlock(5, -61, 5, stone), "지면(고체) 덮어쓰기 거부");
            assertEquals(ground, r.chunkAt(new ChunkPos(0, 0)).getBlock(5, -61, 5));
        }).get();
        io.shutdown(); sched.close();
    }
}
