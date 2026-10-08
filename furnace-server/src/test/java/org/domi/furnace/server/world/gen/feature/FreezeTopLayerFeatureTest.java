package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class FreezeTopLayerFeatureTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    private FeatureWorldView viewWithWaterAndStone(int water, int stone) {
        ChunkColumn col = new ChunkColumn(0);
        col.setBlock(4, 62, 4, water);
        col.setBlock(8, 63, 8, stone);
        return new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
    }

    @Test
    void coldBiome_freezesSurfaceWater_andSnowsSolidSurface() {
        int water = REG.defaultStateId("minecraft:water");
        int stone = REG.defaultStateId("minecraft:stone");
        FeatureWorldView view = viewWithWaterAndStone(water, stone);
        FreezeTopLayerFeature freeze = new FreezeTopLayerFeature(REG, new float[]{0.0f}, new boolean[]{false});

        assertTrue(freeze.place(view, new WorldgenRandom(), 0, 0, 0));

        assertEquals(REG.defaultStateId("minecraft:ice"), view.getBlock(4, 62, 4));
        // 얼음 위에는 눈이 얹히지 않는다(snow_layer_cannot_survive_on)
        assertEquals(0, view.getBlock(4, 63, 4));
        assertEquals(REG.defaultStateId("minecraft:snow"), view.getBlock(8, 64, 8));
    }

    @Test
    void warmBiome_doesNotFreezeOrSnow() {
        int water = REG.defaultStateId("minecraft:water");
        int stone = REG.defaultStateId("minecraft:stone");
        FeatureWorldView view = viewWithWaterAndStone(water, stone);
        FreezeTopLayerFeature freeze = new FreezeTopLayerFeature(REG, new float[]{0.8f}, new boolean[]{false});

        freeze.place(view, new WorldgenRandom(), 0, 0, 0);

        assertEquals(water, view.getBlock(4, 62, 4));
        assertEquals(0, view.getBlock(8, 64, 8));
    }

    @Test
    void snowOnGrass_setsSnowyTrue() {
        int grass = REG.stateIdOrAir("minecraft:grass_block", java.util.Map.of("snowy", "false"));
        ChunkColumn col = new ChunkColumn(0);
        col.setBlock(2, 70, 2, grass);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
        new FreezeTopLayerFeature(REG, new float[]{0.0f}, new boolean[]{false}).place(view, new WorldgenRandom(), 0, 0, 0);

        assertEquals(REG.stateIdOrAir("minecraft:grass_block", java.util.Map.of("snowy", "true")), view.getBlock(2, 70, 2));
        assertEquals(REG.defaultStateId("minecraft:snow"), view.getBlock(2, 71, 2));
    }
}
