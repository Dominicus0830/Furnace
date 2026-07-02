package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class OreFeatureTest {

    @Test
    void oreFeature_placesOreBlocks() {
        int stoneId = 1;
        int ironOreId = 2;
        ChunkColumn col = new ChunkColumn(6);
        int[] blockIds = new int[ChunkColumn.SECTION_COUNT * 4096];
        java.util.Arrays.fill(blockIds, stoneId);
        col.loadGenerated(blockIds, new int[ChunkColumn.SECTION_COUNT * 64]);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
        OreFeature ore = new OreFeature(List.of(new OreFeature.Target(stoneId, ironOreId)), 9, 0.0f);
        WorldgenRandom random = new WorldgenRandom();
        random.setSeed(42L);

        assertTrue(ore.place(view, random, 8, 0, 8));

        long ironCount = countBlock(col, ironOreId);
        assertTrue(ironCount > 0, "ironCount=" + ironCount);
    }

    private static long countBlock(ChunkColumn col, int stateId) {
        long count = 0;
        for (int y = ChunkColumn.MIN_Y; y < ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (col.getBlock(x, y, z) == stateId) count++;
                }
            }
        }
        return count;
    }
}
