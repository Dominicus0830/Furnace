// noise_based_count modifier의 count 계산·random 0소비 parity 검증
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class NoiseBasedCountPlacementTest {

    private FeatureWorldView view() {
        return new FeatureWorldView(new ChunkColumn(0), 0, 0, new int[ChunkColumn.SECTION_COUNT * 64]);
    }

    @Test
    void count_matchesCeilFormula_andConsumesZeroRandom() {
        var mod = new PlacementModifier.NoiseBasedCountPlacement(160, 80.0, 0.3);
        BlockPos pos = new BlockPos(8, 64, 8);

        double value = PlacementModifier.BIOME_INFO_NOISE.getValue(8 / 80.0, 8 / 80.0);
        int expected = Math.max((int) Math.ceil((value + 0.3) * 160), 0);

        WorldgenRandom r = new WorldgenRandom();
        r.setFeatureSeed(42L, 0, 0);
        assertEquals(expected, mod.filter(view(), r, pos).count());

        WorldgenRandom ref = new WorldgenRandom();
        ref.setFeatureSeed(42L, 0, 0);
        assertEquals(ref.nextLong(), r.nextLong());
    }
}
