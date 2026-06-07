// 섹션 블록 set/get, 비공기 카운트, heightmap 패킹 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkColumnTest {

    @Test
    void section_setBlock_countsNonAir() {
        ChunkSection s = new ChunkSection();
        assertEquals(0, s.nonAirCount());
        s.setBlock(0, 0, 0, 79); // bedrock
        s.setBlock(1, 0, 0, 79);
        assertEquals(2, s.nonAirCount());
        s.setBlock(0, 0, 0, 0);  // 다시 air → 카운트 감소
        assertEquals(1, s.nonAirCount());
        assertEquals(79, s.getBlock(1, 0, 0));
    }

    @Test
    void column_has24Sections_andBiomeFill() {
        ChunkColumn c = new ChunkColumn(6); // plains 인덱스 6
        assertEquals(24, c.sections().length);
        // 모든 섹션 바이옴이 단일 plains
        assertEquals(6, c.sections()[0].getBiome(0, 0, 0));
    }

    @Test
    void heightmap_packs9Bit() {
        ChunkColumn c = new ChunkColumn(6);
        c.setSurface(0, 0, 4); // (x=0,z=0) 표면 높이값 4
        long[] hm = c.motionBlockingHeightmap();
        // 9비트 256칸 valuesPerLong=7 → ceil(256/7)=37 long
        assertEquals(37, hm.length);
        // 첫 칸 값 4 복원
        BitStorage bs = BitStorage.wrap(9, 256, hm);
        assertEquals(4, bs.get(0));
    }
}
