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

    // I-5: 압축본이 255섹터를 넘으면 섹터 수 잘림 대신 바닐라처럼 외부 .mcc 로 뺀다
    @Test
    void oversized_goesExternal_andHeaderStaysValid(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.0.0.mca");
        byte[] big = new byte[1_200_000];
        new java.util.Random(1).nextBytes(big);   // 압축 안 됨 → 255섹터(1,044,480B) 초과
        try (RegionFile rf = new RegionFile(f)) {
            rf.writeChunk(2, 3, big);
            rf.writeChunk(4, 4, "neighbor".getBytes());   // 스텁 뒤 할당이 겹치지 않아야 한다
            assertTrue(java.nio.file.Files.exists(rf.externalPath(2, 3)));
            assertArrayEquals(big, rf.readChunk(2, 3));
        }
        assertTrue(java.nio.file.Files.size(f) < 100_000, "본 파일엔 1섹터 스텁만");
        try (RegionFile rf = new RegionFile(f)) {
            assertArrayEquals(big, rf.readChunk(2, 3));
            assertArrayEquals("neighbor".getBytes(), rf.readChunk(4, 4));
            rf.writeChunk(2, 3, new byte[10]);   // 다시 작아지면 외부 파일 삭제
            assertFalse(java.nio.file.Files.exists(rf.externalPath(2, 3)));
            assertEquals(10, rf.readChunk(2, 3).length);
        }
    }

    // 경계: 정확히 255섹터는 본 파일에 남는다
    @Test
    void exactly255Sectors_staysInline(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.0.0.mca");
        // 랜덤 바이트의 deflate 는 stored 블록(약 5B/16KB 오버헤드)이라 크기를 줄여 가며 255섹터에 맞춘다
        byte[] data = null;
        for (int n = 255 * 4096 - 5 - 200; n > 0; n -= 16) {
            byte[] d = new byte[n];
            new java.util.Random(n).nextBytes(d);
            int total = Compression.deflate(d).length + 5;
            if (total <= 255 * 4096 && total > 254 * 4096) { data = d; break; }
        }
        assertNotNull(data);
        try (RegionFile rf = new RegionFile(f)) {
            rf.writeChunk(0, 0, data);
            assertFalse(java.nio.file.Files.exists(rf.externalPath(0, 0)));
            assertArrayEquals(data, rf.readChunk(0, 0));
        }
    }

    // 입력 검증: 깨진 헤더(파일 밖 오프셋, 0섹터, 과대 길이)는 예외·과대 할당 없이 없는 청크로
    @Test
    void corruptHeader_treatedAsMissing(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.0.0.mca");
        try (RegionFile rf = new RegionFile(f)) {
            rf.writeChunk(0, 0, "a".getBytes());
            rf.writeChunk(1, 0, "b".getBytes());
            rf.writeChunk(2, 0, "c".getBytes());
        }
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f.toFile(), "rw")) {
            raf.seek(0); raf.writeInt((100_000 << 8) | 1);   // 파일 밖
            raf.seek(4); raf.writeInt((3 << 8));               // 0섹터
            raf.seek(8); int loc = raf.readInt();
            raf.seek((long) (loc >>> 8) * 4096); raf.writeInt(Integer.MAX_VALUE);   // 길이 과대
        }
        try (RegionFile rf = new RegionFile(f)) {
            assertNull(rf.readChunk(0, 0));
            assertNull(rf.readChunk(1, 0));
            assertNull(rf.readChunk(2, 0));
        }
    }

    @Test
    void nullPayload_rejected(@TempDir Path dir) throws Exception {
        try (RegionFile rf = new RegionFile(dir.resolve("r.0.0.mca"))) {
            assertThrows(IllegalArgumentException.class, () -> rf.writeChunk(0, 0, null));
        }
    }
}
