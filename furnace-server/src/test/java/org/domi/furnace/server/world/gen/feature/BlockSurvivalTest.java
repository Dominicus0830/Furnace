package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class BlockSurvivalTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final BlockSurvival SURVIVAL = new BlockSurvival(REG, new FluidKinds(REG));

    private FeatureWorldView view(ChunkColumn col) {
        return new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
    }

    @Test
    void tallGrass_placesLowerThenUpper_whenAboveIsAir() {
        ChunkColumn col = new ChunkColumn(0);
        col.setBlock(3, 60, 3, REG.defaultStateId("minecraft:grass_block"));
        FeatureWorldView view = view(col);
        int lower = REG.stateIdOrAir("minecraft:tall_grass", Map.of("half", "lower"));
        SimpleBlockFeature f = new SimpleBlockFeature(new SimpleStateProvider(lower), SURVIVAL);

        assertTrue(f.place(view, new WorldgenRandom(), 3, 61, 3));

        assertEquals(lower, view.getBlock(3, 61, 3));
        assertEquals(REG.stateIdOrAir("minecraft:tall_grass", Map.of("half", "upper")), view.getBlock(3, 62, 3));
    }

    @Test
    void tallGrass_notPlaced_whenAboveBlocked() {
        ChunkColumn col = new ChunkColumn(0);
        col.setBlock(3, 60, 3, REG.defaultStateId("minecraft:grass_block"));
        col.setBlock(3, 62, 3, REG.defaultStateId("minecraft:stone"));
        FeatureWorldView view = view(col);
        int lower = REG.stateIdOrAir("minecraft:tall_grass", Map.of("half", "lower"));

        assertFalse(new SimpleBlockFeature(new SimpleStateProvider(lower), SURVIVAL).place(view, new WorldgenRandom(), 3, 61, 3));
        assertEquals(0, view.getBlock(3, 61, 3));
    }

    @Test
    void flower_needsDirtOrFarmland() {
        ChunkColumn col = new ChunkColumn(0);
        col.setBlock(1, 60, 1, REG.defaultStateId("minecraft:stone"));
        col.setBlock(2, 60, 2, REG.defaultStateId("minecraft:dirt"));
        FeatureWorldView view = view(col);
        SimpleBlockFeature f = new SimpleBlockFeature(new SimpleStateProvider(REG.defaultStateId("minecraft:dandelion")), SURVIVAL);

        assertFalse(f.place(view, new WorldgenRandom(), 1, 61, 1));
        assertTrue(f.place(view, new WorldgenRandom(), 2, 61, 2));
    }

    @Test
    void randomizedIntProvider_setsPropertyInRange() {
        int vine = REG.stateIdOrAir("minecraft:cave_vines", Map.of("age", "0", "berries", "false"));
        RandomizedIntStateProvider p = new RandomizedIntStateProvider(
            new SimpleStateProvider(vine), "age", new TreeIntProvider.Uniform(23, 25), REG);
        WorldgenRandom r = new WorldgenRandom();
        for (int i = 0; i < 20; i++) {
            String key = REG.canonicalKeyOf(p.getState(r, 0, 0, 0));
            assertTrue(key.contains("age=23") || key.contains("age=24") || key.contains("age=25"), key);
            assertTrue(key.contains("berries=false"), key);
        }
    }
}
