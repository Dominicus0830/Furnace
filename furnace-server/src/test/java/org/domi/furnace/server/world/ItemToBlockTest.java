// item id → block default state id 해석(설치용)
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.Executors;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ItemToBlockTest {

    @Test
    void resolvesItemToBlockState() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(blocks, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, blocks.directBits(), 7, io);
        w.setRegistries(blocks, items);
        int stoneItem = 1; // minecraft:stone
        assertEquals(blocks.defaultStateId("minecraft:stone"), w.itemToBlockState(stoneItem));
        assertEquals(0, w.itemToBlockState(838)); // diamond_sword → 블록 아님 → air(0)
        assertEquals(0, w.itemToBlockState(0));   // air
        io.shutdown(); sched.close();
    }
}
