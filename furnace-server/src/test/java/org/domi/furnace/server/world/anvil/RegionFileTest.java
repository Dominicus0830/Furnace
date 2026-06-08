// .mca 섹터 청크 바이트 write→read 라운드트립 + 음수좌표 + 재기록 검증
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionFileTest {

    @Test
    void writeRead_roundTrip(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.0.0.mca");
        byte[] payload = "hello chunk".getBytes();
        try (RegionFile rf = new RegionFile(f)) {
            rf.writeChunk(3, 5, payload);
            assertArrayEquals(payload, rf.readChunk(3, 5));
            assertNull(rf.readChunk(0, 0)); // 미기록
        }
        // 재오픈 후에도 유지
        try (RegionFile rf = new RegionFile(f)) {
            assertArrayEquals(payload, rf.readChunk(3, 5));
        }
    }

    @Test
    void rewrite_largerThenSmaller(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.0.0.mca");
        try (RegionFile rf = new RegionFile(f)) {
            rf.writeChunk(1, 1, new byte[5000]); // 2섹터
            rf.writeChunk(1, 1, new byte[100]);  // 1섹터(in-place 축소)
            assertEquals(100, rf.readChunk(1, 1).length);
        }
    }
}
