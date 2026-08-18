package org.domi.furnace.server.world.gen.feature.trunk;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeFeature;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;

// vanilla CherryTrunkPlacer 포팅 (cherry_grove). 곧은 몸통 위에서 1~2개의 가지가 옆으로 뻗다가
// 위/아래로 꺾여 foliage attachment로 이어진다. 가지의 로그는 뻗는 방향(가로 이동)일 때만 axis를
// 그 방향으로 세팅하고, 위/아래로 꺾이는 구간은 기본(y축) 상태를 쓴다.
public final class CherryTrunkPlacer extends TrunkPlacer {
    private final TreeIntProvider branchCount;
    private final TreeIntProvider branchHorizontalLength;
    private final TreeIntProvider.Uniform branchStartOffsetFromTop;
    private final TreeIntProvider.Uniform secondBranchStartOffsetFromTop;
    private final TreeIntProvider branchEndOffsetFromTop;

    public CherryTrunkPlacer(
            int baseHeight, int heightRandA, int heightRandB,
            TreeIntProvider branchCount, TreeIntProvider branchHorizontalLength,
            TreeIntProvider.Uniform branchStartOffsetFromTop, TreeIntProvider branchEndOffsetFromTop) {
        super(baseHeight, heightRandA, heightRandB);
        this.branchCount = branchCount;
        this.branchHorizontalLength = branchHorizontalLength;
        this.branchStartOffsetFromTop = branchStartOffsetFromTop;
        this.secondBranchStartOffsetFromTop =
            new TreeIntProvider.Uniform(branchStartOffsetFromTop.minInclusive(), branchStartOffsetFromTop.maxInclusive() - 1);
        this.branchEndOffsetFromTop = branchEndOffsetFromTop;
    }

    @Override
    public List<FoliageAttachment> placeTrunk(
            FeatureWorldView view, WorldgenRandom random, int height, BlockPos origin, TreeConfig config, List<BlockPos> logs) {
        setDirtAt(view, origin, config, logs);
        int max = Math.max(0, height - 1 + branchStartOffsetFromTop.sample(random));
        int max1 = Math.max(0, height - 1 + secondBranchStartOffsetFromTop.sample(random));
        if (max1 >= max) max1++;

        int branchCountSample = branchCount.sample(random);
        boolean threeBranches = branchCountSample == 3;
        boolean twoOrMoreBranches = branchCountSample >= 2;
        int trunkTop;
        if (threeBranches) {
            trunkTop = height;
        } else if (twoOrMoreBranches) {
            trunkTop = Math.max(max, max1) + 1;
        } else {
            trunkTop = max + 1;
        }

        for (int i = 0; i < trunkTop; i++) {
            placeLog(view, new BlockPos(origin.x(), origin.y() + i, origin.z()), config, logs);
        }

        List<FoliageAttachment> list = new ArrayList<>();
        if (threeBranches) {
            list.add(new FoliageAttachment(new BlockPos(origin.x(), origin.y() + trunkTop, origin.z()), 0, false));
        }

        TreeFeature.Direction dir = TreeFeature.getRandomDirection(random);
        list.add(generateBranch(view, random, height, origin, config, dir, max, max < trunkTop - 1, logs));
        if (twoOrMoreBranches) {
            list.add(generateBranch(view, random, height, origin, config, dir.opposite(), max1, max1 < trunkTop - 1, logs));
        }

        return list;
    }

    private FoliageAttachment generateBranch(
            FeatureWorldView view, WorldgenRandom random, int height, BlockPos origin, TreeConfig config,
            TreeFeature.Direction dir, int branchStartOffset, boolean doubleBranch, List<BlockPos> logs) {
        int cx = origin.x();
        int cy = origin.y() + branchStartOffset;
        int cz = origin.z();
        int endOffset = height - 1 + branchEndOffsetFromTop.sample(random);
        boolean doubleSegment = doubleBranch || endOffset < branchStartOffset;
        int horizLen = branchHorizontalLength.sample(random) + (doubleSegment ? 1 : 0);
        int targetX = origin.x() + dir.dx() * horizLen;
        int targetZ = origin.z() + dir.dz() * horizLen;
        int targetY = origin.y() + endOffset;
        int straightSteps = doubleSegment ? 2 : 1;

        for (int i = 0; i < straightSteps; i++) {
            cx += dir.dx();
            cz += dir.dz();
            placeLog(view, new BlockPos(cx, cy, cz), axisStateId(config, dir), config, logs);
        }

        boolean goUp = targetY > cy;

        while (true) {
            int manhattan = Math.abs(cx - targetX) + Math.abs(cy - targetY) + Math.abs(cz - targetZ);
            if (manhattan == 0) {
                return new FoliageAttachment(new BlockPos(targetX, targetY + 1, targetZ), 0, false);
            }
            float f = (float) Math.abs(targetY - cy) / manhattan;
            boolean vertical = random.nextFloat() < f;
            if (vertical) {
                cy += goUp ? 1 : -1;
                placeLog(view, new BlockPos(cx, cy, cz), config.trunkStateId(), config, logs);
            } else {
                cx += dir.dx();
                cz += dir.dz();
                placeLog(view, new BlockPos(cx, cy, cz), axisStateId(config, dir), config, logs);
            }
        }
    }

    // axis: 0=x, 1=y, 2=z (FancyTrunkPlacer.axisTrunkStateId과 동일한 blockstates 오프셋 규약).
    private static int axisStateId(TreeConfig config, TreeFeature.Direction dir) {
        int axis = dir.dx() != 0 ? 0 : 2;
        return config.trunkStateId() + (axis - 1);
    }
}
