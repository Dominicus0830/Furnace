package org.domi.furnace.server.world.gen.feature.trunk;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeFeature;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;

// vanilla UpwardsBranchingTrunkPlacer 포팅 (맹그로브 등). 몸통을 곧게 올리며 매 로그마다 확률적으로
// 곁가지를 낸다.
//
// canGrowThrough(#minecraft:mangrove_logs_can_grow_through)는 의도적으로 구현하지 않음 —
// worldgen-oracle의 OracleMain이 이 태그를 바인딩하지 않아(datagen 모드 미바인딩 태그는 공집합)
// oracle(ground truth) 기준으로도 이 분기는 항상 false이므로, 표준 validTreePos만으로 oracle과
// byte-exact 일치한다(result.md Caveats 참조).
public final class UpwardsBranchingTrunkPlacer extends TrunkPlacer {
    private final TreeIntProvider extraBranchSteps;
    private final float placeBranchPerLogProbability;
    private final TreeIntProvider extraBranchLength;

    public UpwardsBranchingTrunkPlacer(
            int baseHeight, int heightRandA, int heightRandB,
            TreeIntProvider extraBranchSteps, float placeBranchPerLogProbability, TreeIntProvider extraBranchLength) {
        super(baseHeight, heightRandA, heightRandB);
        this.extraBranchSteps = extraBranchSteps;
        this.placeBranchPerLogProbability = placeBranchPerLogProbability;
        this.extraBranchLength = extraBranchLength;
    }

    @Override
    public List<FoliageAttachment> placeTrunk(
            FeatureWorldView view, WorldgenRandom random, int height, BlockPos origin, TreeConfig config, List<BlockPos> logs) {
        List<FoliageAttachment> list = new ArrayList<>();

        for (int i = 0; i < height; i++) {
            int y = origin.y() + i;
            boolean placed = placeLog(view, new BlockPos(origin.x(), y, origin.z()), config, logs);
            if (placed && i < height - 1 && random.nextFloat() < placeBranchPerLogProbability) {
                TreeFeature.Direction dir = TreeFeature.getRandomDirection(random);
                int sampleA = extraBranchLength.sample(random);
                int sampleB = extraBranchLength.sample(random);
                int max = Math.max(0, sampleA - sampleB - 1);
                int steps = extraBranchSteps.sample(random);
                placeBranch(view, config, logs, list, origin.x(), origin.z(), height, y, dir, max, steps);
            }
            if (i == height - 1) {
                list.add(new FoliageAttachment(new BlockPos(origin.x(), y + 1, origin.z()), 0, false));
            }
        }

        return list;
    }

    private void placeBranch(
            FeatureWorldView view, TreeConfig config, List<BlockPos> logs, List<FoliageAttachment> attachments,
            int startX, int startZ, int height, int y, TreeFeature.Direction dir, int extraBranchLength, int extraBranchSteps) {
        int i = y + extraBranchLength;
        int x = startX;
        int z = startZ;
        int i1 = extraBranchLength;

        while (i1 < height && extraBranchSteps > 0) {
            if (i1 >= 1) {
                int i2 = y + i1;
                x += dir.dx();
                z += dir.dz();
                i = i2;
                if (placeLog(view, new BlockPos(x, i2, z), config, logs)) {
                    i = i2 + 1;
                }
                attachments.add(new FoliageAttachment(new BlockPos(x, i2, z), 0, false));
            }
            i1++;
            extraBranchSteps--;
        }

        if (i - y > 1) {
            BlockPos pos = new BlockPos(x, i, z);
            attachments.add(new FoliageAttachment(pos, 0, false));
            attachments.add(new FoliageAttachment(new BlockPos(pos.x(), pos.y() - 2, pos.z()), 0, false));
        }
    }
}
