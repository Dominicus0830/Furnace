package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class SeagrassFeatureTest {

    @Test
    void seagrassFeature_placesTallSeagrassInWater() {
        BlockRegistry registry = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkColumn col = new ChunkColumn(0);
        int[] blockIds = new int[ChunkColumn.SECTION_COUNT * 16 * 16 * 16];
        int[] biomeIds = new int[ChunkColumn.SECTION_COUNT * 64];
        int floorY = 10;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                blockIds[(floorY - ChunkColumn.MIN_Y) * 256 + z * 16 + x] = registry.defaultStateId("minecraft:dirt");
                blockIds[(floorY + 1 - ChunkColumn.MIN_Y) * 256 + z * 16 + x] = registry.defaultStateId("minecraft:water");
                blockIds[(floorY + 2 - ChunkColumn.MIN_Y) * 256 + z * 16 + x] = registry.defaultStateId("minecraft:water");
            }
        }
        col.loadGenerated(blockIds, biomeIds);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, biomeIds);
        SeagrassFeature feature = new SeagrassFeature(
            1.0f,
            registry.defaultStateId("minecraft:seagrass"),
            registry.defaultStateId("minecraft:tall_seagrass"),
            registry.stateIdOrAir("minecraft:tall_seagrass", java.util.Map.of("half", "upper")),
            registry.defaultStateId("minecraft:magma_block"));
        WorldgenRandom random = new WorldgenRandom();
        random.setSeed(1234L);

        assertTrue(feature.place(view, random, 8, 0, 8));

        boolean foundLower = false;
        boolean foundUpper = false;
        int lower = registry.defaultStateId("minecraft:tall_seagrass");
        int upper = registry.stateIdOrAir("minecraft:tall_seagrass", java.util.Map.of("half", "upper"));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (view.getBlock(x, floorY + 1, z) == lower) foundLower = true;
                if (view.getBlock(x, floorY + 2, z) == upper) foundUpper = true;
            }
        }
        assertTrue(foundLower);
        assertTrue(foundUpper);
    }
}
