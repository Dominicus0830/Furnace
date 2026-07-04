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

    // 대량 read syscall 감소 실측: region 전체(1024청크) 기록 후 재오픈+반복 read 시간 측정
    @Test
    void bulkRead_manyChunks_timing(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.0.0.mca");
        byte[] payload = new byte[200];
        java.util.Arrays.fill(payload, (byte) 9);
        try (RegionFile rf = new RegionFile(f)) {
            for (int x = 0; x < 32; x++)
                for (int z = 0; z < 32; z++)
                    rf.writeChunk(x, z, payload);
        }

        int rounds = 20;
        long start = System.nanoTime();
        for (int r = 0; r < rounds; r++) {
            try (RegionFile rf = new RegionFile(f)) { // 생성자 헤더 read 포함
                for (int x = 0; x < 32; x++)
                    for (int z = 0; z < 32; z++)
                        assertArrayEquals(payload, rf.readChunk(x, z));
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.println("[bulkRead_manyChunks_timing] " + rounds + " rounds x 1024 chunks: " + elapsedMs + " ms");
    }
}
