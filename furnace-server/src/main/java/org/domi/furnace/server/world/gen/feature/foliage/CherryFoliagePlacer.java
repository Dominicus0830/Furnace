package org.domi.furnace.server.world.gen.feature.foliage;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

// vanilla CherryFoliagePlacer 포팅 (cherry_grove). 위에서 좁게 시작해 아래로 넓어지는 층을 쌓고,
// 맨 아래 두 층은 placeLeavesRowWithHangingLeavesBelow로 매달린 잎을 덧붙인다.
public final class CherryFoliagePlacer extends FoliagePlacer {
    private final TreeIntProvider heightProvider;
    private final float wideBottomLayerHoleChance;
    private final float cornerHoleChance;
    private final float hangingLeavesChance;
    private final float hangingLeavesExtensionChance;

    public CherryFoliagePlacer(
            TreeIntProvider radius, TreeIntProvider offset, TreeIntProvider heightProvider,
            float wideBottomLayerHoleChance, float cornerHoleChance,
            float hangingLeavesChance, float hangingLeavesExtensionChance) {
        super(radius, offset);
        this.heightProvider = heightProvider;
        this.wideBottomLayerHoleChance = wideBottomLayerHoleChance;
        this.cornerHoleChance = cornerHoleChance;
        this.hangingLeavesChance = hangingLeavesChance;
        this.hangingLeavesExtensionChance = hangingLeavesExtensionChance;
    }

    @Override
    public int foliageHeight(WorldgenRandom random, int trunkHeight, TreeConfig config) {
        return heightProvider.sample(random);
    }

    @Override
    protected void createFoliage(FeatureWorldView view, WorldgenRandom random, TreeConfig config, int trunkHeight,
                                 TrunkPlacer.FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset,
                                 List<BlockPos> leaves) {
        boolean doubleTrunk = attachment.doubleTrunk();
        BlockPos pos = new BlockPos(attachment.pos().x(), attachment.pos().y() + offset, attachment.pos().z());
        int radius = foliageRadius + attachment.radiusOffset() - 1;
        placeLeavesRow(view, random, config, pos, radius - 2, foliageHeight - 3, doubleTrunk, leaves);
        placeLeavesRow(view, random, config, pos, radius - 1, foliageHeight - 4, doubleTrunk, leaves);

        for (int y = foliageHeight - 5; y >= 0; y--) {
            placeLeavesRow(view, random, config, pos, radius, y, doubleTrunk, leaves);
        }

        placeLeavesRowWithHangingLeavesBelow(
            view, random, config, pos, radius, -1, doubleTrunk, hangingLeavesChance, hangingLeavesExtensionChance, leaves);
        placeLeavesRowWithHangingLeavesBelow(
            view, random, config, pos, radius - 1, -2, doubleTrunk, hangingLeavesChance, hangingLeavesExtensionChance, leaves);
    }

    // vanilla CherryFoliagePlacer.shouldSkipLocation(mcpal L93-105).
    @Override
    protected boolean shouldSkipLocation(WorldgenRandom random, int absX, int y, int absZ, int radius, boolean giantTrunk) {
        if (y == -1 && (absX == radius || absZ == radius) && random.nextFloat() < wideBottomLayerHoleChance) {
            return true;
        }
        boolean corner = absX == radius && absZ == radius;
        boolean wide = radius > 2;
        if (wide) {
            return corner || (absX + absZ > radius * 2 - 2 && random.nextFloat() < cornerHoleChance);
        }
        return corner && random.nextFloat() < cornerHoleChance;
    }
}
