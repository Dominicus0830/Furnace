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
}
