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

    @Test
    void multiChunk_writeToNeighborIsRecorded() {
        ChunkColumn[] grid = filledGrid();
        FeatureWorldView view = new FeatureWorldView(grid, biomeGrid(), 0, 0);

        view.setBlock(16, ChunkColumn.MIN_Y, 0, 2);

        assertEquals(2, grid[5].getBlock(0, ChunkColumn.MIN_Y, 0));
    }

    @Test
    void multiChunk_writeOutside3x3IsDropped() {
        ChunkColumn[] grid = filledGrid();
        FeatureWorldView view = new FeatureWorldView(grid, biomeGrid(), 0, 0);

        view.setBlock(48, ChunkColumn.MIN_Y, 0, 2);

        for (ChunkColumn col : grid) {
            assertEquals(0, col.getBlock(0, ChunkColumn.MIN_Y, 0));
        }
    }

    @Test
    void multiChunk_nullSlotWriteDropped() {
        ChunkColumn[] grid = filledGrid();
        grid[5] = null;
        FeatureWorldView view = new FeatureWorldView(grid, biomeGrid(), 0, 0);

        assertDoesNotThrow(() -> view.setBlock(16, ChunkColumn.MIN_Y, 0, 2));
        assertEquals(0, grid[4].getBlock(0, ChunkColumn.MIN_Y, 0));
    }

    @Test
    void featureWorldView_readsWgOceanFloorHeight() {
        ChunkColumn col = new ChunkColumn(6);
        int[] blockIds = new int[ChunkColumn.SECTION_COUNT * 16 * 16 * 16];
        int[] biomeIds = new int[ChunkColumn.SECTION_COUNT * 64];
        int topY = ChunkColumn.MIN_Y + 3;
        for (int y = ChunkColumn.MIN_Y; y <= topY; y++) {
            blockIds[(y - ChunkColumn.MIN_Y) * 256] = 1;
        }
        col.loadGenerated(blockIds, biomeIds);
        col.setBlock(0, topY, 0, 0);

        FeatureWorldView view = new FeatureWorldView(col, 0, 0, biomeIds);

        assertEquals(topY + 1, view.getWgOceanFloorHeight(0, 0));
        assertEquals(topY - 1, view.getOceanFloorY(0, 0));
    }

    private static ChunkColumn[] filledGrid() {
        ChunkColumn[] grid = new ChunkColumn[9];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = new ChunkColumn(6);
        }
        return grid;
    }

    private static int[][] biomeGrid() {
        int[][] biomeIdsGrid = new int[9][];
        for (int i = 0; i < biomeIdsGrid.length; i++) {
            biomeIdsGrid[i] = new int[ChunkColumn.SECTION_COUNT * 64];
        }
        return biomeIdsGrid;
    }
}
