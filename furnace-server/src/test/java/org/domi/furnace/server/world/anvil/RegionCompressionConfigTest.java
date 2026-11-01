package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** region-file-compression: deflate/gzip/none 로 쓴 청크를 같은 코드가 읽고, 압축 종류 바이트가 맞게 기록된다. */
class RegionCompressionConfigTest {

    @TempDir Path tmp;

    @AfterEach
    void reset() {
        Compression.resetWriteType();
    }

    @Test
    void configureAcceptsKnownNamesOnly() {
        assertTrue(Compression.configure("gzip"));
        assertEquals(Compression.GZIP, Compression.writeType());
        assertTrue(Compression.configure("none"));
        assertEquals(Compression.NONE, Compression.writeType());
        assertTrue(Compression.configure("deflate"));
        assertEquals(Compression.DEFLATE, Compression.writeType());
        assertFalse(Compression.configure("lz4"));   // 미지원 — 이전 설정 유지
        assertFalse(Compression.configure("zstd"));
        assertEquals(Compression.DEFLATE, Compression.writeType());
    }

    @Test
    void compressDecompressRoundTrip() throws Exception {
        byte[] data = new byte[5000];
        new Random(3).nextBytes(data);
        for (int t : new int[] {Compression.GZIP, Compression.DEFLATE, Compression.NONE}) {
            assertArrayEquals(data, Compression.decompress(t, Compression.compress(t, data)), "type " + t);
        }
    }

    @Test
    void regionFileStoresTypeByteAndReadsBack() throws Exception {
        byte[] nbt = new byte[3000];
        new Random(9).nextBytes(nbt);
        for (String name : new String[] {"gzip", "none", "deflate"}) {
            Compression.configure(name);
            Path f = tmp.resolve("r." + name + ".mca");
            try (RegionFile rf = new RegionFile(f)) {
                rf.writeChunk(1, 2, nbt);
                assertArrayEquals(nbt, rf.readChunk(1, 2), name);
            }
            // 파일을 다시 열어도 읽힌다(헤더의 압축 종류 바이트로 해제)
            try (RegionFile rf = new RegionFile(f)) {
                assertArrayEquals(nbt, rf.readChunk(1, 2), name + " reopen");
            }
        }
    }
}
