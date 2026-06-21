// noise_threshold_count placement modifier의 임계 분기·반복수·무작위 비소비 검증
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class NoiseThresholdCountPlacementTest {

    @Test
    void selectsBelowOrAboveByNoiseThreshold() {
        WorldgenRandom r = new WorldgenRandom();
        BlockPos pos = new BlockPos(13, 64, -29);

        // noiseLevel 매우 큼 → value < level 항상 참 → belowNoise
        var below = new PlacementModifier.NoiseThresholdCountPlacement(1000.0, 3, 7);
        assertEquals(3, below.filter(null, r, pos).count());

        // noiseLevel 매우 작음 → value >= level 항상 → aboveNoise
        var above = new PlacementModifier.NoiseThresholdCountPlacement(-1000.0, 3, 7);
        assertEquals(7, above.filter(null, r, pos).count());
    }

    @Test
    void consumesNoRandom() {
        WorldgenRandom r1 = new WorldgenRandom();
        r1.setFeatureSeed(123456L, 2, 9);
        WorldgenRandom r2 = new WorldgenRandom();
        r2.setFeatureSeed(123456L, 2, 9);

        new PlacementModifier.NoiseThresholdCountPlacement(0.0, 4, 5)
            .filter(null, r1, new BlockPos(0, 64, 0)).count();

        assertEquals(r2.nextInt(1_000_000), r1.nextInt(1_000_000));
    }
}
