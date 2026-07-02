package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class KelpFeatureTest {

    @Test
    void kelpFeature_placesPlantAndTopStatesInWater() {
        BlockRegistry registry = BlockRegistry.load("blocks/1.21.1.txt");
        ChunkColumn col = new ChunkColumn(0);
        int[] blockIds = new int[ChunkColumn.SECTION_COUNT * 16 * 16 * 16];
        int[] biomeIds = new int[ChunkColumn.SECTION_COUNT * 64];
        int floorY = 10;
        int dirt = registry.defaultStateId("minecraft:dirt");
        int water = registry.defaultStateId("minecraft:water");
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                blockIds[(floorY - ChunkColumn.MIN_Y) * 256 + z * 16 + x] = dirt;
                for (int y = floorY + 1; y <= floorY + 8; y++) {
                    blockIds[(y - ChunkColumn.MIN_Y) * 256 + z * 16 + x] = water;
                }
            }
        }
        col.loadGenerated(blockIds, biomeIds);

        int[] topStates = new int[4];
        for (int age = 20; age <= 23; age++) {
            topStates[age - 20] = registry.stateIdOrAir("minecraft:kelp", Map.of("age", Integer.toString(age)));
        }
        KelpFeature feature = new KelpFeature(
            registry.defaultStateId("minecraft:kelp"),
            registry.defaultStateId("minecraft:kelp_plant"),
            topStates,
            registry.defaultStateId("minecraft:magma_block"));
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, biomeIds);
        WorldgenRandom random = new WorldgenRandom();
        random.setSeed(1234L);

        assertTrue(feature.place(view, random, 8, 0, 8));

        boolean foundPlant = false;
        boolean foundTop = false;
        int kelpPlant = registry.defaultStateId("minecraft:kelp_plant");
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = floorY + 1; y <= floorY + 8; y++) {
                    int state = view.getBlock(x, y, z);
                    if (state == kelpPlant) foundPlant = true;
                    for (int topState : topStates) {
                        if (state == topState) foundTop = true;
                    }
                }
            }
        }
        assertTrue(foundPlant);
        assertTrue(foundTop);
    }
}
