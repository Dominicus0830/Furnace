// ChunkColumn.setBiome / getBiome 라운드트립 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkColumnBiomeTest {

    @Test void setBiome_roundtrip() {
        ChunkColumn col = new ChunkColumn(0);
        col.setBiome(2, 10, 3, 42);
        // section = 10/4=2, sectionQuartY = 10%4=2
        // biomeIndex(2,2,3) = 2*16+3*4+2 = 32+12+2 = 46
        assertEquals(42, col.sections()[2].getBiome(2, 2, 3));
    }

    @Test void setBiome_bottom() {
        ChunkColumn col = new ChunkColumn(5);
        col.setBiome(0, 0, 0, 7);
        assertEquals(7, col.sections()[0].getBiome(0, 0, 0));
    }

    @Test void setBiome_top() {
        ChunkColumn col = new ChunkColumn(0);
        col.setBiome(3, 95, 3, 99);
        // section = 95/4=23, sectionQuartY = 95%4=3
        // biomeIndex(3,3,3) = 3*16+3*4+3 = 48+12+3 = 63
        assertEquals(99, col.sections()[23].getBiome(3, 3, 3));
    }
}
