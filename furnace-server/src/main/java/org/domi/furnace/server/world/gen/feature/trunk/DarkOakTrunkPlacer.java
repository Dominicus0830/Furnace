package org.domi.furnace.server.world.gen.feature.trunk;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

public final class DarkOakTrunkPlacer extends TrunkPlacer {
    public DarkOakTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    public List<FoliageAttachment> placeTrunk(
            FeatureWorldView view, WorldgenRandom random, int height, BlockPos origin, TreeConfig config, List<BlockPos> logs) {
        setDirtAt(view, origin, config, logs);
        setDirtAt(view, new BlockPos(origin.x() + 1, origin.y(), origin.z()), config, logs);
        setDirtAt(view, new BlockPos(origin.x(), origin.y(), origin.z() + 1), config, logs);
        setDirtAt(view, new BlockPos(origin.x() + 1, origin.y(), origin.z() + 1), config, logs);

        TreeFeature.Direction dir = TreeFeature.getRandomDirection(random);
        int bendStart = height - random.nextInt(4);
        int bendLen = 2 - random.nextInt(3);
        int x = origin.x();
        int z = origin.z();
        int topY = origin.y();

        for (int y = 0; y < height; y++) {
            int py = origin.y() + y;
            if (y >= bendStart && bendLen > 0) {
                x += dir.dx();
                z += dir.dz();
                bendLen--;
            }
            // 바닐라: 굽은 기준 칸이 공기/잎일 때만 2x2를 심는다(TreeFeature.isAirOrLeaves)
            int base = view.getBlock(x, py, z);
            if (TreeFeature.isAir(base) || TreeFeature.isLeaves(base)) {
                placeSquare(view, x, py, z, config, logs);
            }
            topY = py;
        }

        List<FoliageAttachment> attachments = new ArrayList<>();
        attachments.add(new FoliageAttachment(new BlockPos(x, topY, z), 0, true));
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                if ((dx < 0 || dx > 1 || dz < 0 || dz > 1) && random.nextInt(3) <= 0) {
                    int branchH = random.nextInt(3) + 2;
                    for (int i = 0; i < branchH; i++) {
                        placeLog(view, new BlockPos(origin.x() + dx, topY - i - 1, origin.z() + dz), config, logs);
                    }
                    // 잎 부착점은 굽은 줄기 꼭대기(x, z) 기준 + 오프셋 (가지 로그는 원점 기준)
                    attachments.add(new FoliageAttachment(new BlockPos(x + dx, topY, z + dz), 0, false));
                }
            }
        }
        return attachments;
    }

    private void placeSquare(FeatureWorldView view, int x, int y, int z, TreeConfig config, List<BlockPos> logs) {
        placeLog(view, new BlockPos(x, y, z), config, logs);
        placeLog(view, new BlockPos(x + 1, y, z), config, logs);
        placeLog(view, new BlockPos(x, y, z + 1), config, logs);
        placeLog(view, new BlockPos(x + 1, y, z + 1), config, logs);
    }
}
