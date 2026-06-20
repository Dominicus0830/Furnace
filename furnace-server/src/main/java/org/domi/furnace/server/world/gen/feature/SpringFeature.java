package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class SpringFeature implements ConfiguredFeature {

    private final int stateId;
    private final boolean requiresBlockBelow;
    private final int rockCount;
    private final int holeCount;
    private final Set<Integer> validBlockIds;

    public SpringFeature(
            int stateId,
            boolean requiresBlockBelow,
            int rockCount,
            int holeCount,
            Set<Integer> validBlockIds) {
        this.stateId = stateId;
        this.requiresBlockBelow = requiresBlockBelow;
        this.rockCount = rockCount;
        this.holeCount = holeCount;
        this.validBlockIds = Set.copyOf(validBlockIds);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        if (!isValidBlock(view.getBlock(x, y + 1, z))) return false;
        if (requiresBlockBelow && !isValidBlock(view.getBlock(x, y - 1, z))) return false;
        if (view.getBlock(x, y, z) != ChunkSection.AIR && !isValidBlock(view.getBlock(x, y, z))) return false;

        int rocks = 0;
        int holes = 0;
        int[][] offsets = {
            {0, -1, 0}, {0, 1, 0}, {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}
        };
        for (int[] offset : offsets) {
            int state = view.getBlock(x + offset[0], y + offset[1], z + offset[2]);
            if (isValidBlock(state)) rocks++;
            if (state == ChunkSection.AIR) holes++;
        }
        if (rocks >= rockCount && holes == holeCount) {
            view.setBlock(x, y, z, stateId);
            return true;
        }
        return false;
    }

    private boolean isValidBlock(int stateId) {
        return validBlockIds.contains(stateId);
    }
}
