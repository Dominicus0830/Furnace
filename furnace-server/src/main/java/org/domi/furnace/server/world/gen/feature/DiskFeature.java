package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class DiskFeature implements ConfiguredFeature {

    private final int stateId;
    private final int radius;
    private final int halfHeight;
    private final Set<Integer> targetStateIds;

    public DiskFeature(int stateId, int radius, int halfHeight, Set<Integer> targetStateIds) {
        this.stateId = stateId;
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.targetStateIds = Set.copyOf(targetStateIds);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        boolean placed = false;
        int radiusSquared = radius * radius;
        for (int bx = x - radius; bx <= x + radius; bx++) {
            int dx = bx - x;
            for (int bz = z - radius; bz <= z + radius; bz++) {
                int dz = bz - z;
                if (dx * dx + dz * dz > radiusSquared) continue;
                for (int by = y - halfHeight; by <= y + halfHeight; by++) {
                    if (targetStateIds.contains(view.getBlock(bx, by, bz))) {
                        view.setBlock(bx, by, bz, stateId);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }
}
