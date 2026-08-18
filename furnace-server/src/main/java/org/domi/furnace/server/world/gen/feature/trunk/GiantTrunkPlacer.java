package org.domi.furnace.server.world.gen.feature.trunk;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;

// vanilla GiantTrunkPlacer 포팅 (거대 가문비나무 등). 2x2 몸통 — origin 아래 2x2에 흙, 각 층마다
// (0,0)은 항상, 맨 위 층 제외하고 (1,0)/(1,1)/(0,1)도 심어 정사각 단면을 만든다.
public class GiantTrunkPlacer extends TrunkPlacer {
    public GiantTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    public List<FoliageAttachment> placeTrunk(
            FeatureWorldView view, WorldgenRandom random, int height, BlockPos origin, TreeConfig config, List<BlockPos> logs) {
        setDirtAt(view, origin, config, logs);
        setDirtAt(view, new BlockPos(origin.x() + 1, origin.y(), origin.z()), config, logs);
        setDirtAt(view, new BlockPos(origin.x(), origin.y(), origin.z() + 1), config, logs);
        setDirtAt(view, new BlockPos(origin.x() + 1, origin.y(), origin.z() + 1), config, logs);

        for (int i = 0; i < height; i++) {
            placeLogIfFreeOffset(view, config, logs, origin, 0, i, 0);
            if (i < height - 1) {
                placeLogIfFreeOffset(view, config, logs, origin, 1, i, 0);
                placeLogIfFreeOffset(view, config, logs, origin, 1, i, 1);
                placeLogIfFreeOffset(view, config, logs, origin, 0, i, 1);
            }
        }

        return List.of(new FoliageAttachment(new BlockPos(origin.x(), origin.y() + height, origin.z()), 0, true));
    }

    private void placeLogIfFreeOffset(
            FeatureWorldView view, TreeConfig config, List<BlockPos> logs, BlockPos origin, int dx, int dy, int dz) {
        BlockPos pos = new BlockPos(origin.x() + dx, origin.y() + dy, origin.z() + dz);
        if (isFree(view, pos, config)) {
            placeLog(view, pos, config, logs);
        }
    }
}
