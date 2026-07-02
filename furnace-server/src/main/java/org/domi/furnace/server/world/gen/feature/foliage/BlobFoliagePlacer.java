package org.domi.furnace.server.world.gen.feature.foliage;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

public final class BlobFoliagePlacer extends FoliagePlacer {
    private final int height;

    public BlobFoliagePlacer(TreeIntProvider radius, TreeIntProvider offset, int height) {
        super(radius, offset);
        this.height = height;
    }

    @Override
    public int foliageHeight(WorldgenRandom random, int trunkHeight, TreeConfig config) {
        return height;
    }

    @Override
    protected void createFoliage(FeatureWorldView view, WorldgenRandom random, TreeConfig config, int trunkHeight,
                                 TrunkPlacer.FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset,
                                 java.util.List<BlockPos> leaves) {
        for (int y = offset; y >= offset - foliageHeight; y--) {
            int radius = Math.max(foliageRadius + attachment.radiusOffset() - 1 - y / 2, 0);
            placeLeavesRow(view, random, config, attachment.pos(), radius, y, attachment.doubleTrunk(), leaves);
        }
    }
}
