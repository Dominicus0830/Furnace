package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

/**
 * NMS shim 4번 묶음: {@code RegionFile}·{@code RegionFileVersion}·{@code DedicatedServer#forceSynchronousWrites} — Orebfuscator 가 자기 캐시 폴더에
 * 바닐라 지역 파일 형식으로 읽고 쓴다(서버의 anvil 구현 위). 디스크 바이트까지 바닐라 형식인지 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsRegionFileTest {

    FurnaceTestServer server;

    @TempDir
    Path dir;

    @BeforeAll
    void start() { server = FurnaceTestServer.builder().failFast(false).plugin(CompatTestPlugin.class).start(); }

    @AfterAll
    void stop() { server.close(); }

    RegionFile open(Path file, RegionFileVersion v) throws IOException { return new RegionFile(null, file, file.getParent(), v, false); }

    static byte[] bytes(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    static void write(RegionFile f, ChunkPos pos, byte[] data) throws IOException {
        try (var out = f.getChunkDataOutputStream(pos)) {
            out.write(data);
        }
    }

    static byte[] read(RegionFile f, ChunkPos pos) throws IOException {
        try (DataInputStream in = f.getChunkDataInputStream(pos)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    @Test
    void writesTheVanillaLayoutAndReadsItBack() throws IOException {
        Path file = dir.resolve("r.0.-1.mca");
        ChunkPos pos = new ChunkPos(3, -2);
        byte[] data = bytes(5000, 1);
        try (RegionFile f = open(file, RegionFileVersion.VERSION_NONE)) {
            assertSame(file, f.getPath());
            assertFalse(f.hasChunk(pos));
            assertNull(f.getChunkDataInputStream(pos), "없는 청크는 null");
            write(f, pos, data);
            assertTrue(f.hasChunk(pos));
            assertArrayEquals(data, read(f, pos));
            f.flush();
        }
        // 디스크: 위치표(청크 안 번호 (z&31)*32+(x&31)) → 섹터 → 길이(본문+1)·종류 3(압축 없음)·본문 그대로
        try (var raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(4L * ((-2 & 31) * 32 + (3 & 31)));
            int loc = raf.readInt();
            assertEquals(2, loc >>> 8, "헤더 두 섹터 뒤 첫 자리");
            assertEquals(2, loc & 0xFF, "5005 바이트 = 두 섹터");
            raf.seek((long) (loc >>> 8) * 4096);
            assertEquals(data.length + 1, raf.readInt());
            assertEquals(3, raf.readByte());
            byte[] body = new byte[data.length];
            raf.readFully(body);
            assertArrayEquals(data, body);
            assertEquals(0, raf.length() % 4096, "섹터 배수");
        }
        try (RegionFile again = open(file, RegionFileVersion.VERSION_DEFLATE)) {
            assertArrayEquals(data, read(again, pos), "다시 열어도 — 읽을 때는 청크마다 적힌 종류로 푼다");
        }
    }

    @Test
    void compressedAndOversizedChunks() throws IOException {
        Path file = dir.resolve("r.1.1.mca");
        ChunkPos small = new ChunkPos(33, 40), big = new ChunkPos(34, 40);
        byte[] text = "orebfuscator ".repeat(400).getBytes();
        byte[] huge = bytes(256 * 4096, 2);   // 압축 안 되는 1MB — 255 섹터를 넘어 외부 파일로
        try (RegionFile f = open(file, RegionFileVersion.VERSION_DEFLATE)) {
            write(f, small, text);
            write(f, big, huge);
            assertArrayEquals(text, read(f, small));
            assertArrayEquals(huge, read(f, big));
        }
        assertTrue(Files.isRegularFile(dir.resolve("c.34.40.mcc")), "바닐라처럼 같은 폴더의 c.<x>.<z>.mcc");
        try (var raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(4L * ((40 & 31) * 32 + (33 & 31)));
            raf.seek((long) (raf.readInt() >>> 8) * 4096 + 4);
            assertEquals(2, raf.readByte(), "deflate");
        }
    }

    /** 쓸 때 고른 압축 종류(gzip 1·lz4 4)가 청크 머리에 적히고, 다른 종류로 연 파일에서도 그 종류로 풀린다. */
    @Test
    void gzipAndLz4Chunks() throws IOException {
        Path file = dir.resolve("r.3.3.mca");
        byte[] text = "region ".repeat(700).getBytes();
        ChunkPos gz = new ChunkPos(96, 96), lz = new ChunkPos(97, 96);
        try (RegionFile f = open(file, RegionFileVersion.VERSION_GZIP)) {
            write(f, gz, text);
        }
        try (RegionFile f = open(file, RegionFileVersion.VERSION_LZ4)) {
            write(f, lz, text);
            assertArrayEquals(text, read(f, gz));
            assertArrayEquals(text, read(f, lz));
        }
        try (var raf = new RandomAccessFile(file.toFile(), "r")) {
            for (var c : new Object[][] {{gz, 1}, {lz, 4}}) {
                ChunkPos p = (ChunkPos) c[0];
                raf.seek(4L * ((p.z & 31) * 32 + (p.x & 31)));
                raf.seek((long) (raf.readInt() >>> 8) * 4096 + 4);
                assertEquals((int) c[1], raf.readByte(), "압축 종류 " + c[1]);
            }
        }
    }

    @Test
    void versionsAndRefusals() throws IOException {
        assertEquals(3, RegionFileVersion.VERSION_NONE.getId());
        assertSame(RegionFileVersion.VERSION_LZ4, RegionFileVersion.fromId(4));
        assertNull(RegionFileVersion.fromId(9));
        assertTrue(RegionFileVersion.isValidVersion(127));
        assertSame(RegionFileVersion.VERSION_DEFLATE, RegionFileVersion.getCompressionFormat(), "서버 기본 deflate");
        assertFalse(DedicatedServer.INSTANCE.forceSynchronousWrites(), "시험 서버는 sync-chunk-writes 기본(거짓)");
        Path file = dir.resolve("r.2.2.mca");
        Path notDir = dir.resolve("plain.txt");
        Files.writeString(notDir, "x");
        var e = assertThrows(IllegalArgumentException.class, () -> new RegionFile(null, file, notDir, RegionFileVersion.VERSION_NONE, false));
        assertEquals("Expected directory, got " + notDir.toAbsolutePath(), e.getMessage());
        Path other = Files.createDirectories(dir.resolve("other"));
        assertThrows(UnsupportedNmsCall.class, () -> new RegionFile(null, file, other, RegionFileVersion.VERSION_NONE, false));
        assertThrows(UnsupportedNmsCall.class, () -> new RegionFile(null, file, dir, RegionFileVersion.VERSION_CUSTOM, false));
    }
}
