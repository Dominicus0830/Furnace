package org.domi.furnace.server.world.gen.feature.trunk;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

public final class ForkingTrunkPlacer extends TrunkPlacer {
    public ForkingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    public List<FoliageAttachment> placeTrunk(
            FeatureWorldView view, WorldgenRandom random, int height, BlockPos origin, TreeConfig config, List<BlockPos> logs) {
        setDirtAt(view, origin, config, logs);
        TreeFeature.Direction dir1 = TreeFeature.getRandomDirection(random);
        int splitStart = height - random.nextInt(4) - 1;
        int branchLen = 3 - random.nextInt(3);
        int x = origin.x();
        int z = origin.z();
        int topY = 0;
        List<FoliageAttachment> attachments = new ArrayList<>(2);

        for (int y = 0; y < height; y++) {
            int py = origin.y() + y;
            if (y >= splitStart && branchLen > 0) {
                x += dir1.dx();
                z += dir1.dz();
                branchLen--;
            }
            placeLog(view, new BlockPos(x, py, z), config, logs);
            topY = py;
        }
        attachments.add(new FoliageAttachment(new BlockPos(x, topY + 1, z), 1, false));

        x = origin.x();
        z = origin.z();
        TreeFeature.Direction dir2 = TreeFeature.getRandomDirection(random);
        if (dir2 != dir1) {
            int secondStart = splitStart - random.nextInt(2) - 1;
            int secondLen = 1 + random.nextInt(3);
            int secondTopY = 0;
            for (int y = secondStart; y < height && secondLen > 0; y++, secondLen--) {
                if (y >= 1) {
                    x += dir2.dx();
                    z += dir2.dz();
                    int py = origin.y() + y;
                    placeLog(view, new BlockPos(x, py, z), config, logs);
                    secondTopY = py;
                }
            }
            attachments.add(new FoliageAttachment(new BlockPos(x, secondTopY + 1, z), 0, false));
        }
        return attachments;
    }
}
