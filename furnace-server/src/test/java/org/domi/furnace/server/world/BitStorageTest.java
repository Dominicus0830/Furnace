// compacted long 팩킹 라운드트립 + long 개수 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BitStorageTest {

    @Test
    void roundTrips_4bit() {
        BitStorage s = new BitStorage(4, 4096);
        for (int i = 0; i < 4096; i++) s.set(i, i % 13);
        for (int i = 0; i < 4096; i++) assertEquals(i % 13, s.get(i), "idx " + i);
    }

    @Test
    void longCount_isCompacted_notSpanning() {
        // 4비트 → valuesPerLong=16 → 4096/16=256 long
        BitStorage s = new BitStorage(4, 4096);
        assertEquals(256, s.data().length);
        // 5비트 → valuesPerLong=12 → ceil(4096/12)=342 long
        BitStorage s5 = new BitStorage(5, 4096);
        assertEquals(342, s5.data().length);
    }

    @Test
    void roundTrips_9bit_heightmap() {
        BitStorage s = new BitStorage(9, 256);
        for (int i = 0; i < 256; i++) s.set(i, (i * 3) & 0x1FF);
        for (int i = 0; i < 256; i++) assertEquals((i * 3) & 0x1FF, s.get(i));
    }

    // 곱셈·시프트 인덱스가 나눗셈 기준 팩킹과 long 배열까지 같다(모든 비트폭, 섹션·높이맵·바이옴 크기).
    @Test
    void packingMatchesDivisionReference_allWidths() {
        java.util.Random rnd = new java.util.Random(5);
        for (int bits = 1; bits <= 32; bits++) {
            for (int size : new int[]{64, 256, 4096, 5000}) {
                BitStorage s = new BitStorage(bits, size);
                int vpl = 64 / bits;
                long mask = (1L << bits) - 1;
                long[] ref = new long[(size + vpl - 1) / vpl];
                int[] vals = new int[size];
                for (int i = 0; i < size; i++) {
                    int v = (int) (rnd.nextLong() & mask);
                    vals[i] = v;
                    s.set(i, v);
                    int li = i / vpl, off = (i % vpl) * bits;
                    ref[li] = (ref[li] & ~(mask << off)) | (((long) v & mask) << off);
                }
                assertArrayEquals(ref, s.data(), "bits=" + bits + " size=" + size);
                for (int i = 0; i < size; i++) assertEquals(vals[i], s.get(i));
            }
        }
    }
}
