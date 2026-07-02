package org.domi.furnace.server.world.gen.feature.trunk;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

public abstract class TrunkPlacer {

    protected final int baseHeight;
    protected final int heightRandA;
    protected final int heightRandB;

    protected TrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        this.baseHeight = baseHeight;
        this.heightRandA = heightRandA;
        this.heightRandB = heightRandB;
    }

    public final int getTreeHeight(WorldgenRandom random) {
        return baseHeight + random.nextInt(heightRandA + 1) + random.nextInt(heightRandB + 1);
    }

    public abstract List<FoliageAttachment> placeTrunk(
        FeatureWorldView view, WorldgenRandom random, int height, BlockPos origin, TreeConfig config, List<BlockPos> logs);

    protected final boolean placeLog(FeatureWorldView view, BlockPos pos, TreeConfig config, List<BlockPos> logs) {
        if (!TreeFeature.validTreePos(view, pos, config)) return false;
        view.setBlock(pos.x(), pos.y(), pos.z(), config.trunkStateId());
        logs.add(pos);
        return true;
    }

    protected final void setDirtAt(FeatureWorldView view, BlockPos pos, TreeConfig config, List<BlockPos> logs) {
        int below = view.getBlock(pos.x(), pos.y() - 1, pos.z());
        if (config.forceDirt() || !TreeFeature.isDirt(below, config)) {
            BlockPos dirtPos = new BlockPos(pos.x(), pos.y() - 1, pos.z());
            view.setBlock(dirtPos.x(), dirtPos.y(), dirtPos.z(), config.dirtStateId());
            logs.add(dirtPos);
        }
    }

    public boolean isFree(FeatureWorldView view, BlockPos pos, TreeConfig config) {
        int state = view.getBlock(pos.x(), pos.y(), pos.z());
        return TreeFeature.validTreePos(view, pos, config)
            || TreeFeature.isLog(state, config);
    }

    public final boolean isFree(FeatureWorldView view, BlockPos pos) {
        throw new UnsupportedOperationException("TreeConfig-aware isFree must be used");
    }

    public record FoliageAttachment(BlockPos pos, int radiusOffset, boolean doubleTrunk) {}
}
