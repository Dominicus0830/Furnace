package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class SpringFeatureTest {

    @Test
    void springFeature_placesFluidWhenRockAndHoleCountsMatch() {
        int stone = 1;
        int water = 80;
        ChunkColumn col = new ChunkColumn(0);
        col.setBlock(8, 9, 8, stone);
        col.setBlock(8, 11, 8, stone);
        col.setBlock(7, 10, 8, stone);
        col.setBlock(9, 10, 8, stone);
        col.setBlock(8, 10, 7, stone);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
        SpringFeature spring = new SpringFeature(water, true, 4, 1, Set.of(stone));

        assertTrue(spring.place(view, new WorldgenRandom(), 8, 10, 8));

        assertEquals(water, view.getBlock(8, 10, 8));
    }

    @Test
    void springFeature_skipsWhenHoleCountDoesNotMatch() {
        int stone = 1;
        int water = 80;
        ChunkColumn col = new ChunkColumn(0);
        col.setBlock(8, 9, 8, stone);
        col.setBlock(8, 11, 8, stone);
        col.setBlock(7, 10, 8, stone);
        col.setBlock(9, 10, 8, stone);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
        SpringFeature spring = new SpringFeature(water, true, 4, 1, Set.of(stone));

        assertFalse(spring.place(view, new WorldgenRandom(), 8, 10, 8));

        assertEquals(ChunkSection.AIR, view.getBlock(8, 10, 8));
    }
}
