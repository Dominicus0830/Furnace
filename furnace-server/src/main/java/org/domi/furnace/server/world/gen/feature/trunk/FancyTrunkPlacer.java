package org.domi.furnace.server.world.gen.feature.trunk;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;

// vanilla FancyTrunkPlacer 포팅 (fancy_oak 등 큰 나무 계열).
public final class FancyTrunkPlacer extends TrunkPlacer {
    private static final double CLUSTER_DENSITY_MAGIC = 1.382;
    private static final double BRANCH_SLOPE = 0.381;
    private static final double BRANCH_LENGTH_MAGIC = 0.328;

    public FancyTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    public List<FoliageAttachment> placeTrunk(
            FeatureWorldView view, WorldgenRandom random, int freeTreeHeight, BlockPos origin, TreeConfig config, List<BlockPos> logs) {
        int i1 = freeTreeHeight + 2;
        int floor = floor(i1 * 0.618);
        setDirtAt(view, origin, config, logs);
        int min = Math.min(1, floor(CLUSTER_DENSITY_MAGIC + Math.pow(1.0 * i1 / 13.0, 2.0)));
        int i2 = origin.y() + floor;
        int i3 = i1 - 5;
        List<FoliageCoords> list = new ArrayList<>();
        list.add(new FoliageCoords(new BlockPos(origin.x(), origin.y() + i3, origin.z()), i2));

        for (; i3 >= 0; i3--) {
            float f = treeShape(i1, i3);
            if (!(f < 0.0F)) {
                for (int i4 = 0; i4 < min; i4++) {
                    double d2 = 1.0 * f * (random.nextFloat() + BRANCH_LENGTH_MAGIC);
                    double d3 = random.nextFloat() * 2.0F * Math.PI;
                    double d4 = d2 * Math.sin(d3) + 0.5;
                    double d5 = d2 * Math.cos(d3) + 0.5;
                    BlockPos blockPos = new BlockPos(origin.x() + floor(d4), origin.y() + i3 - 1, origin.z() + floor(d5));
                    BlockPos blockPos1 = new BlockPos(blockPos.x(), blockPos.y() + 5, blockPos.z());
                    if (makeLimb(view, blockPos, blockPos1, false, config, logs)) {
                        int i5 = origin.x() - blockPos.x();
                        int i6 = origin.z() - blockPos.z();
                        double d6 = blockPos.y() - Math.sqrt(i5 * i5 + i6 * i6) * BRANCH_SLOPE;
                        int i7 = d6 > i2 ? i2 : (int) d6;
                        BlockPos blockPos2 = new BlockPos(origin.x(), i7, origin.z());
                        if (makeLimb(view, blockPos2, blockPos, false, config, logs)) {
                            list.add(new FoliageCoords(blockPos, blockPos2.y()));
                        }
                    }
                }
            }
        }

        makeLimb(view, origin, new BlockPos(origin.x(), origin.y() + floor, origin.z()), true, config, logs);
        makeBranches(view, i1, origin, list, config, logs);

        List<FoliageAttachment> result = new ArrayList<>();
        for (FoliageCoords fc : list) {
            if (trimBranches(i1, fc.branchBase - origin.y())) {
                result.add(fc.attachment);
            }
        }
        return result;
    }

    private boolean makeLimb(FeatureWorldView view, BlockPos basePos, BlockPos offsetPos, boolean modifyWorld, TreeConfig config, List<BlockPos> logs) {
        if (!modifyWorld && basePos.equals(offsetPos)) return true;

        int dx = offsetPos.x() - basePos.x();
        int dy = offsetPos.y() - basePos.y();
        int dz = offsetPos.z() - basePos.z();
        int steps = getSteps(dx, dy, dz);
        float fx = (float) dx / steps;
        float fy = (float) dy / steps;
        float fz = (float) dz / steps;

        for (int i = 0; i <= steps; i++) {
            int px = basePos.x() + floor(0.5F + i * fx);
            int py = basePos.y() + floor(0.5F + i * fy);
            int pz = basePos.z() + floor(0.5F + i * fz);
            BlockPos cur = new BlockPos(px, py, pz);
            if (modifyWorld) {
                int axis = getLogAxis(basePos, cur);
                placeLog(view, cur, axisTrunkStateId(config, axis), config, logs);
            } else if (!isFree(view, cur, config)) {
                return false;
            }
        }
        return true;
    }

    private int getSteps(int x, int y, int z) {
        return Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
    }

    // axis: 0=x, 1=y, 2=z (blockstates_1.21.1.txt의 axis=x/y/z가 trunkStateId 기준 -1/0/+1로 나열됨).
    private int getLogAxis(BlockPos pos, BlockPos otherPos) {
        int axis = 1;
        int absX = Math.abs(otherPos.x() - pos.x());
        int absZ = Math.abs(otherPos.z() - pos.z());
        int max = Math.max(absX, absZ);
        if (max > 0) {
            axis = (absX == max) ? 0 : 2;
        }
        return axis;
    }

    private int axisTrunkStateId(TreeConfig config, int axis) {
        return config.trunkStateId() + (axis - 1);
    }

    private boolean trimBranches(int maxHeight, int currentHeight) {
        return currentHeight >= maxHeight * 0.2;
    }

    private void makeBranches(FeatureWorldView view, int maxHeight, BlockPos origin, List<FoliageCoords> foliageCoords, TreeConfig config, List<BlockPos> logs) {
        for (FoliageCoords fc : foliageCoords) {
            int branchBase = fc.branchBase;
            BlockPos blockPos = new BlockPos(origin.x(), branchBase, origin.z());
            if (!blockPos.equals(fc.attachment.pos()) && trimBranches(maxHeight, branchBase - origin.y())) {
                makeLimb(view, blockPos, fc.attachment.pos(), true, config, logs);
            }
        }
    }

    private static float treeShape(int height, int currentY) {
        if (currentY < height * 0.3F) {
            return -1.0F;
        } else {
            float f = height / 2.0F;
            float f1 = f - currentY;
            float sqrt = (float) Math.sqrt(f * f - f1 * f1);
            if (f1 == 0.0F) {
                sqrt = f;
            } else if (Math.abs(f1) >= f) {
                return 0.0F;
            }
            return sqrt * 0.5F;
        }
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static final class FoliageCoords {
        final FoliageAttachment attachment;
        final int branchBase;

        FoliageCoords(BlockPos attachmentPos, int branchBase) {
            this.attachment = new FoliageAttachment(attachmentPos, 0, false);
            this.branchBase = branchBase;
        }
    }
}
