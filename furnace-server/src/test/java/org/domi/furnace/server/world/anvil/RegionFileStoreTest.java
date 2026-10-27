// 여러 청크(다른 .mca 매핑)를 store로 read/write, 닫기 검증
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionFileStoreTest {

    @Test
    void writeRead_acrossRegionFiles(@TempDir Path dir) throws Exception {
        RegionFileStore store = new RegionFileStore(dir);
        store.writeChunk(0, 0, "a".getBytes());
        store.writeChunk(40, 0, "b".getBytes());   // 다른 .mca(r.1.0)
        assertArrayEquals("a".getBytes(), store.readChunk(0, 0));
        assertArrayEquals("b".getBytes(), store.readChunk(40, 0));
        assertNull(store.readChunk(5, 5));
        store.closeAll();
    }

    @Test
    void lru_closesOldestHandles_andReopensOnDemand(@TempDir Path dir) throws Exception {
        RegionFileStore store = new RegionFileStore(dir);
        int files = RegionFileStore.MAX_OPEN + 20;
        for (int i = 0; i < files; i++) store.writeChunk(i * 32, 0, ("c" + i).getBytes());
        assertEquals(RegionFileStore.MAX_OPEN, store.openCount());   // 바닐라처럼 256개 넘게 열어 두지 않는다
        for (int i = 0; i < files; i++) assertArrayEquals(("c" + i).getBytes(), store.readChunk(i * 32, 0));
        assertEquals(RegionFileStore.MAX_OPEN, store.openCount());
        store.closeAll();
    }

    @Test
    void lru_concurrentAccessSurvivesEviction(@TempDir Path dir) throws Exception {
        RegionFileStore store = new RegionFileStore(dir);
        int files = RegionFileStore.MAX_OPEN * 2;
        for (int i = 0; i < files; i++) store.writeChunk(i * 32, 0, ("c" + i).getBytes());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.Future<?>> fs = new java.util.ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int seed = t;
                fs.add(pool.submit(() -> {
                    java.util.Random r = new java.util.Random(seed);
                    for (int n = 0; n < 2000; n++) {
                        int i = r.nextInt(files);
                        assertArrayEquals(("c" + i).getBytes(), store.readChunk(i * 32, 0));
                    }
                    return null;
                }));
            }
            for (var f : fs) f.get();
        } finally {
            pool.shutdownNow();
        }
        store.closeAll();
    }
}
