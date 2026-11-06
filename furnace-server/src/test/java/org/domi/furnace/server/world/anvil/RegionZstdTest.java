package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** zstd region 압축(바닐라 아님): 왕복, 헤더 종류 바이트, 깨진 본문, 변환 도구. */
class RegionZstdTest {

    @TempDir Path tmp;

    @AfterEach
    void reset() { Compression.resetWriteType(); }

    private static byte[] compressible(int n) {
        byte[] b = new byte[n];
        Random r = new Random(5);
        for (int i = 0; i < n; i++) b[i] = (byte) (r.nextInt(4));
        return b;
    }

    @Test
    void roundTripSmallerThanInput() throws Exception {
        byte[] data = compressible(40_000);
        byte[] z = Compression.compress(Compression.ZSTD, data);
        assertTrue(z.length < data.length / 2, "압축됨: " + z.length);
        assertArrayEquals(data, Compression.decompress(Compression.ZSTD, z));
        assertArrayEquals(new byte[0], Compression.decompress(Compression.ZSTD, Compression.compress(Compression.ZSTD, new byte[0])));
    }

    @Test
    void corruptBodyIsIoException() throws Exception {
        byte[] z = Compression.compress(Compression.ZSTD, compressible(5000));
        byte[] bad = Arrays.copyOf(z, z.length / 2);
        assertThrows(IOException.class, () -> Compression.decompress(Compression.ZSTD, bad));
        assertThrows(IOException.class, () -> Compression.decompress(Compression.ZSTD, new byte[] {1, 2, 3}));
    }

    @Test
    void regionFileWritesType5() throws Exception {
        Compression.configure("zstd");
        byte[] nbt = compressible(6000);
        Path f = tmp.resolve("r.0.0.mca");
        try (RegionFile rf = new RegionFile(f)) {
            rf.writeChunk(3, 4, nbt);
            assertEquals(5, rf.readPayload(3, 4).type());
            assertArrayEquals(nbt, rf.readChunk(3, 4));
        }
    }

    @Test
    void convertDeflateToZstdAndBack() throws Exception {
        byte[] a = compressible(9000), b = compressible(3000);
        Path dir = tmp.resolve("world/region");
        Files.createDirectories(dir);
        try (RegionFile rf = new RegionFile(dir.resolve("r.-1.0.mca"))) {
            rf.writeChunk(-1, 0, a);
            rf.writeChunk(-32, 31, b);
        }
        RegionConvert.Result r = RegionConvert.convert(tmp.resolve("world"), "zstd");
        assertEquals(1, r.files());
        assertEquals(2, r.chunks());
        try (RegionFile rf = new RegionFile(dir.resolve("r.-1.0.mca"))) {
            assertEquals(5, rf.readPayload(-1, 0).type());
            assertArrayEquals(a, rf.readChunk(-1, 0));
            assertArrayEquals(b, rf.readChunk(-32, 31));
        }
        assertEquals(0, RegionConvert.convert(tmp.resolve("world"), "zstd").chunks());   // 이미 zstd 면 건너뜀
        assertEquals(2, RegionConvert.convert(tmp.resolve("world"), "deflate").chunks());
        try (RegionFile rf = new RegionFile(dir.resolve("r.-1.0.mca"))) {
            assertEquals(2, rf.readPayload(-1, 0).type());
            assertArrayEquals(a, rf.readChunk(-1, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> RegionConvert.convert(tmp.resolve("world"), "brotli"));
    }
}
