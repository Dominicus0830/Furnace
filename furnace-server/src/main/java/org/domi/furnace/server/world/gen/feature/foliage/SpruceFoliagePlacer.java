package org.domi.furnace.server.world.gen.feature.foliage;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

public final class SpruceFoliagePlacer extends FoliagePlacer {
    private final TreeIntProvider trunkHeight;

    public SpruceFoliagePlacer(TreeIntProvider radius, TreeIntProvider offset, TreeIntProvider trunkHeight) {
        super(radius, offset);
        this.trunkHeight = trunkHeight;
    }

    @Override
    public int foliageHeight(WorldgenRandom random, int trunkHeight, TreeConfig config) {
        return Math.max(4, trunkHeight - this.trunkHeight.sample(random));
    }

    @Override
    protected void createFoliage(FeatureWorldView view, WorldgenRandom random, TreeConfig config, int trunkHeight,
                                 TrunkPlacer.FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset,
                                 java.util.List<BlockPos> leaves) {
        int radius = random.nextInt(2);
        int maxRadius = 1;
        int extra = 0;
        for (int y = offset; y >= offset - foliageHeight; y--) {
            placeLeavesRow(view, random, config, attachment.pos(), radius, y, attachment.doubleTrunk(), leaves);
            if (radius >= maxRadius) {
                radius = extra;
                extra = 1;
                maxRadius = Math.min(maxRadius + 1, foliageRadius + attachment.radiusOffset());
            } else {
                radius++;
            }
        }
    }
}
