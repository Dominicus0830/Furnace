// superflat 생성: 레이어 블록 배치 + 비공기 수 + heightmap 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Test;

class SuperflatWorldSourceTest {

    private static SuperflatWorldSource source() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        return new SuperflatWorldSource(reg, 6); // plains 인덱스 6
    }

    @Test
    void generates_layers() {
        ChunkColumn c = source().generate(0, 0);
        // 섹션0(y −64..−49): bedrock/dirt/grass/air
        assertEquals(85, sectionBlock(c, 0));  // y=−64 bedrock
        assertEquals(10, sectionBlock(c, 1));  // y=−63 dirt
        assertEquals(10, sectionBlock(c, 2));  // y=−62 dirt
        assertEquals(9, sectionBlock(c, 3));   // y=−61 grass
        assertEquals(0, sectionBlock(c, 4));   // y=−60 air
    }

    @Test
    void nonAirCount_section0_is1024() {
        ChunkColumn c = source().generate(0, 0);
        // (1 bedrock + 2 dirt + 1 grass) * 256 = 1024
        assertEquals(1024, c.sections()[0].nonAirCount());
    }

    @Test
    void heightmap_surfaceIs4() {
        ChunkColumn c = source().generate(0, 0);
        org.domi.furnace.server.world.BitStorage bs =
            org.domi.furnace.server.world.BitStorage.wrap(9, 256, c.motionBlockingHeightmap());
        assertEquals(4, bs.get(0));
    }

    private static int sectionBlock(ChunkColumn c, int localY) {
        return c.sections()[0].getBlock(0, localY, 0);
    }
}
