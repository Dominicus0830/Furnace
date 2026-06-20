package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class FreezeTopLayerFeatureTest {

    @Test
    void freezeTopLayerFeature_freezesSurfaceWaterAndPlacesSnowAboveSolidSurface() {
        int stone = 1;
        int water = 80;
        int ice = 5780;
        int snow = 5772;
        ChunkColumn col = new ChunkColumn(0);
        col.setBlock(4, 62, 4, water);
        col.setSurface(4, 4, 127);
        col.setBlock(8, 63, 8, stone);
        col.setSurface(8, 8, 128);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
        // biome id 0 = 추운 바이옴(온도 0.0)으로 두어 동결 게이트 통과.
        FreezeTopLayerFeature freeze = new FreezeTopLayerFeature(water, ice, snow, new float[]{0.0f});

        assertTrue(freeze.place(view, new WorldgenRandom(), 0, 0, 0));

        assertEquals(ice, view.getBlock(4, 62, 4));
        assertEquals(snow, view.getBlock(8, 64, 8));
    }

    @Test
    void freezeTopLayerFeature_warmBiome_doesNotFreezeOrSnow() {
        int stone = 1;
        int water = 80;
        int ice = 5780;
        int snow = 5772;
        ChunkColumn col = new ChunkColumn(0);
        col.setBlock(4, 62, 4, water);
        col.setSurface(4, 4, 127);
        col.setBlock(8, 63, 8, stone);
        col.setSurface(8, 8, 128);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
        // biome id 0 = 따뜻한 바이옴(온도 0.8) → 동결/적설 안 됨.
        FreezeTopLayerFeature freeze = new FreezeTopLayerFeature(water, ice, snow, new float[]{0.8f});

        assertFalse(freeze.place(view, new WorldgenRandom(), 0, 0, 0));

        assertEquals(water, view.getBlock(4, 62, 4)); // 물 그대로
        assertEquals(0, view.getBlock(8, 64, 8));      // 눈 안 깔림(air)
    }
}
