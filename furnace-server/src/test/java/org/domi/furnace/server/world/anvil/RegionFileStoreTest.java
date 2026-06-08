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
}
