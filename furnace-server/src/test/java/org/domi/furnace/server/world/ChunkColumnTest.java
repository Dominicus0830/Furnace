// 섹션 블록 set/get, 비공기 카운트, heightmap 패킹 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkColumnTest {

    private static int surfaceBlockY(ChunkColumn c, int x, int z) throws Exception {
        return (int) ChunkColumn.class.getMethod("surfaceBlockY", int.class, int.class).invoke(c, x, z);
    }

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

    @Test
    void heightmap_isCached_untilSurfaceChanges() {
        ChunkColumn c = new ChunkColumn(6);
        c.setSurface(0, 0, 4);

        long[] first = c.motionBlockingHeightmap();
        long[] second = c.motionBlockingHeightmap();

        assertSame(first, second, "변경 없으면 heightmap 재계산 없이 재사용해야");

        c.setSurface(0, 0, 7);
        long[] third = c.motionBlockingHeightmap();

        assertNotSame(first, third, "표면 변경 후에는 캐시 무효화돼야");
        assertEquals(7, BitStorage.wrap(9, 256, third).get(0));
    }

    @Test
    void surfaceBlockY_readsPrecomputedSurface() throws Exception {
        ChunkColumn c = new ChunkColumn(6);
        c.setSurface(0, 0, 4);
        c.setSurface(1, 0, 0);

        assertEquals(ChunkColumn.MIN_Y + 3, surfaceBlockY(c, 0, 0));
        assertEquals(ChunkColumn.MIN_Y - 1, surfaceBlockY(c, 1, 0));
    }

    @Test
    void generatedSetBlock_doesNotMarkChunkDirty() {
        ChunkColumn c = new ChunkColumn(6);

        c.setGeneratedBlock(0, ChunkColumn.MIN_Y, 0, 79);

        assertFalse(c.dirty());
        assertEquals(79, c.getBlock(0, ChunkColumn.MIN_Y, 0));
    }
}
