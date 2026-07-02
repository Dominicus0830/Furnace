package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class DiskFeatureTest {

    @Test
    void diskFeature_replacesTargetsInsideRadiusAndHeight() {
        int dirt = 10;
        int gravel = 118;
        int stone = 1;
        ChunkColumn col = new ChunkColumn(0);
        for (int z = 4; z <= 12; z++) {
            for (int x = 4; x <= 12; x++) {
                col.setBlock(x, 63, z, dirt);
                col.setBlock(x, 64, z, dirt);
            }
        }
        col.setBlock(8, 63, 8, stone);
        FeatureWorldView view = new FeatureWorldView(col, 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
        DiskFeature disk = new DiskFeature(
            gravel, java.util.List.of(), new TreeIntProvider.Constant(2), 1, Set.of(dirt));

        assertTrue(disk.place(view, new WorldgenRandom(), 8, 63, 8));

        assertEquals(stone, view.getBlock(8, 63, 8));
        assertEquals(gravel, view.getBlock(8, 64, 8));
        assertEquals(gravel, view.getBlock(10, 63, 8));
        assertEquals(dirt, view.getBlock(11, 63, 8));
    }
}
