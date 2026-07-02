package org.domi.furnace.server.world.gen.feature.foliage;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

public final class PineFoliagePlacer extends FoliagePlacer {
    private final TreeIntProvider height;

    public PineFoliagePlacer(TreeIntProvider radius, TreeIntProvider offset, TreeIntProvider height) {
        super(radius, offset);
        this.height = height;
    }

    @Override
    public int foliageHeight(WorldgenRandom random, int trunkHeight, TreeConfig config) {
        return height.sample(random);
    }

    @Override
    public int foliageRadius(WorldgenRandom random, int trunkHeight) {
        return super.foliageRadius(random, trunkHeight) + random.nextInt(Math.max(trunkHeight + 1, 1));
    }

    @Override
    protected void createFoliage(FeatureWorldView view, WorldgenRandom random, TreeConfig config, int trunkHeight,
                                 TrunkPlacer.FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset,
                                 java.util.List<BlockPos> leaves) {
        int radius = 0;
        for (int y = offset; y >= offset - foliageHeight; y--) {
            placeLeavesRow(view, random, config, attachment.pos(), radius, y, attachment.doubleTrunk(), leaves);
            if (radius >= 1 && y == offset - foliageHeight + 1) {
                radius--;
            } else if (radius < foliageRadius + attachment.radiusOffset()) {
                radius++;
            }
        }
    }
}
