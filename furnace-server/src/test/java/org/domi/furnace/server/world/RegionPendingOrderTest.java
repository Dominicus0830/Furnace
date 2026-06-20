package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegionPendingOrderTest {

    @Test
    void reorderPendingNearestFirst_ordersByDistanceFromCenter() {
        Set<ChunkPos> pending = new LinkedHashSet<>(List.of(
                new ChunkPos(2, 2),
                new ChunkPos(0, 1),
                new ChunkPos(0, 0),
                new ChunkPos(1, 0)));

        Region.reorderPendingNearestFirst(new ChunkPos(0, 0), pending);

        assertEquals(List.of(
                new ChunkPos(0, 0),
                new ChunkPos(0, 1),
                new ChunkPos(1, 0),
                new ChunkPos(2, 2)), List.copyOf(pending));
    }
}
