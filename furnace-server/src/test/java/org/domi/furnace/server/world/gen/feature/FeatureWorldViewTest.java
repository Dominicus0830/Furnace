package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Test;

class FeatureWorldViewTest {

    @Test
    void featureWorldView_getSetBlock() {
        ChunkColumn col = new ChunkColumn(6);
        col.setBlock(0, ChunkColumn.MIN_Y, 0, 1);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);

        assertEquals(1, view.getBlock(0, ChunkColumn.MIN_Y, 0));

        view.setBlock(0, ChunkColumn.MIN_Y, 0, 2);

        assertEquals(2, col.getBlock(0, ChunkColumn.MIN_Y, 0));
    }

    @Test
    void featureWorldView_ignoresWritesOutsideCenterChunk() {
        ChunkColumn col = new ChunkColumn(6);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);

        view.setBlock(16, ChunkColumn.MIN_Y, 0, 2);

        assertEquals(0, col.getBlock(0, ChunkColumn.MIN_Y, 0));
    }
}
