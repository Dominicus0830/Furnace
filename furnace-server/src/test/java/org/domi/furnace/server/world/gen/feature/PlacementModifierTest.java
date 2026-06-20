package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class PlacementModifierTest {

    @Test
    void countPlacement_generatesNPositions() {
        PlacementModifier count = new PlacementModifier.CountPlacement(3);
        WorldgenRandom random = new WorldgenRandom();
        random.setSeed(42L);

        List<BlockPos> positions = count.filter(null, random, new BlockPos(0, 64, 0)).toList();

        assertEquals(List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(0, 64, 0),
            new BlockPos(0, 64, 0)
        ), positions);
    }

    @Test
    void inSquarePlacement_offsetsWithinChunk() {
        PlacementModifier inSquare = new PlacementModifier.InSquarePlacement();
        WorldgenRandom random = new WorldgenRandom();
        random.setSeed(42L);

        BlockPos pos = inSquare.filter(null, random, new BlockPos(32, 10, -16))
            .findFirst()
            .orElseThrow();

        assertTrue(pos.x() >= 32 && pos.x() < 48);
        assertEquals(10, pos.y());
        assertTrue(pos.z() >= -16 && pos.z() < 0);
    }
}
