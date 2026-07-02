package org.domi.furnace.server.world.gen.feature.trunk;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;

public final class StraightTrunkPlacer extends TrunkPlacer {
    public StraightTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    public List<FoliageAttachment> placeTrunk(
            FeatureWorldView view, WorldgenRandom random, int height, BlockPos origin, TreeConfig config, List<BlockPos> logs) {
        setDirtAt(view, origin, config, logs);
        for (int i = 0; i < height; i++) {
            placeLog(view, new BlockPos(origin.x(), origin.y() + i, origin.z()), config, logs);
        }
        return List.of(new FoliageAttachment(new BlockPos(origin.x(), origin.y() + height, origin.z()), 0, false));
    }
}
