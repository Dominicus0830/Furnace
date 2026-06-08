// ChunkSnapshot이 ChunkColumn 섹션을 불변 카피로 뜨는지 + getBlock 검증
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class ChunkSnapshotTest {

    @Test
    void of_copiesAndIsIndependent() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkColumn col = new SuperflatWorldSource(reg, 0).generate(0, 0);
        int grass = reg.defaultStateId("minecraft:grass_block");
        assertEquals(grass, col.getBlock(0, -61, 0)); // superflat grass
        ChunkSnapshot snap = ChunkSnapshot.of(2, 3, col);
        assertEquals(2, snap.chunkX());
        assertEquals(3, snap.chunkZ());
        // 스냅샷 후 원본 변형해도 스냅샷 불변
        col.setBlock(0, -61, 0, ChunkSection.AIR);
        int sectionIdx = (-61 - ChunkColumn.MIN_Y) >> 4;
        int local = (-61 - ChunkColumn.MIN_Y) & 15;
        assertEquals(grass, snap.sections()[sectionIdx].blocks()[local * 256 + 0 * 16 + 0]);
    }
}
