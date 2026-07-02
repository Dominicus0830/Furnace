package org.domi.furnace.server.world.gen.feature.foliage;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

public final class DarkOakFoliagePlacer extends FoliagePlacer {
    public DarkOakFoliagePlacer(TreeIntProvider radius, TreeIntProvider offset) {
        super(radius, offset);
    }

    @Override
    public int foliageHeight(WorldgenRandom random, int trunkHeight, TreeConfig config) {
        return 4;
    }

    @Override
    protected void createFoliage(FeatureWorldView view, WorldgenRandom random, TreeConfig config, int trunkHeight,
                                 TrunkPlacer.FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset,
                                 java.util.List<BlockPos> leaves) {
        placeLeavesRow(view, random, config, attachment.pos(), foliageRadius + 2, offset - 1, true, leaves);
        placeLeavesRow(view, random, config, attachment.pos(), foliageRadius + 3, offset, true, leaves);
        placeLeavesRow(view, random, config, attachment.pos(), foliageRadius + 2, offset + 1, true, leaves);
        if (random.nextBoolean()) {
            placeLeavesRow(view, random, config, attachment.pos(), foliageRadius, offset + 2, true, leaves);
        }
    }
}
