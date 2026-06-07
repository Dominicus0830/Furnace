// 블록→RegionPos, RegionPos→스레드 인덱스 매핑 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.api.sched.RegionPos;
import org.junit.jupiter.api.Test;

class RegionGridTest {

    @Test
    void blockToRegion_flooring() {
        // regionSize=16청크=256블록. (0,0)→region(0,0), (-1,-1)→region(-1,-1)
        assertEquals(new RegionPos(0, 0), RegionGrid.regionPos(0, 0, 16));
        assertEquals(new RegionPos(0, 0), RegionGrid.regionPos(255, 255, 16));
        assertEquals(new RegionPos(-1, -1), RegionGrid.regionPos(-1, -1, 16));
        assertEquals(new RegionPos(1, 0), RegionGrid.regionPos(256, 0, 16));
    }

    @Test
    void threadIndex_deterministic_andInRange() {
        RegionPos p = new RegionPos(3, -5);
        int a = RegionGrid.threadIndex(p, 8);
        int b = RegionGrid.threadIndex(new RegionPos(3, -5), 8);
        assertEquals(a, b);                 // 결정적
        assertTrue(a >= 0 && a < 8);        // 범위 내
    }
}
