package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** lz4(type 4) 블록 스트림: lz4-java 1.8.0 LZ4BlockOutputStream 이 만든 바이트를 풀고, 직접 압축한 것을 다시 푼다. */
class Lz4BlockTest {

    /** lz4-java 1.8.0 `new LZ4BlockOutputStream(out)` 에 아래 문장을 쓰고 닫은 결과(외부 라이브러리로 만든 고정 벡터). */
    private static final String LIB_OUTPUT = "4c5a34426c6f636b262300000041000000d7cc590ecf6675726e616365206c7a34200c0011f002726567696f6e206368756e6b206e6274214c5a34426c6f636b16000000000000000000000000";
    private static final String TEXT = "furnace lz4 furnace lz4 furnace lz4 furnace lz4 region chunk nbt!";

    @Test
    void decodesLz4JavaOutput() throws IOException {
        byte[] lib = HexFormat.of().parseHex(LIB_OUTPUT);
        assertEquals(TEXT, new String(Lz4Block.decompress(lib), StandardCharsets.UTF_8));
    }

    @Test
    void checksumMatchesLz4Java() {
        // 위 벡터 헤더의 체크섬 필드(0x0e59ccd7)와 같아야 한다
        byte[] text = TEXT.getBytes(StandardCharsets.UTF_8);
        assertEquals(0x0e59ccd7, Lz4Block.checksum(text, 0, text.length));
    }

    @Test
    void xxhash32KnownVectors() {
        // 공개 XXH32 시험값
        assertEquals(0x02CC5D05, Lz4Block.xxhash32(new byte[0], 0, 0, 0));
        byte[] abc = "abc".getBytes(StandardCharsets.US_ASCII);
        assertEquals(0x32D153FF, Lz4Block.xxhash32(abc, 0, 3, 0));
    }

    @Test
    void roundTripVariousSizes() throws IOException {
        Random r = new Random(7);
        for (int n : new int[] {0, 1, 12, 13, 100, 65535, 65536, 65537, 200_000, 400_000}) {
            for (int mode = 0; mode < 3; mode++) {
                byte[] d = new byte[n];
                for (int i = 0; i < n; i++) d[i] = (byte) (mode == 0 ? r.nextInt(256) : mode == 1 ? r.nextInt(4) : (i / 37) % 7);
                assertArrayEquals(d, Lz4Block.decompress(Lz4Block.compress(d)), "n=" + n + " mode=" + mode);
            }
        }
    }

    @Test
    void compressesRepetitiveData() {
        byte[] d = new byte[100_000];
        for (int i = 0; i < d.length; i++) d[i] = (byte) (i % 13);
        assertTrue(Lz4Block.compress(d).length < d.length / 20);
    }

    @Test
    void rejectsCorruptInput() {
        byte[] lib = HexFormat.of().parseHex(LIB_OUTPUT);
        byte[] badCheck = lib.clone();
        badCheck[17] ^= 1;
        assertThrows(IOException.class, () -> Lz4Block.decompress(badCheck));
        byte[] badMagic = lib.clone();
        badMagic[0] = 'X';
        assertThrows(IOException.class, () -> Lz4Block.decompress(badMagic));
        byte[] truncated = java.util.Arrays.copyOf(lib, 30);
        assertThrows(IOException.class, () -> Lz4Block.decompress(truncated));
    }
}
