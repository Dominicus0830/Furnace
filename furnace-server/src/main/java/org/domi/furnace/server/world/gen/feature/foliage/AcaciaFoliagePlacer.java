package org.domi.furnace.server.world.gen.feature.foliage;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

public final class AcaciaFoliagePlacer extends FoliagePlacer {
    public AcaciaFoliagePlacer(TreeIntProvider radius, TreeIntProvider offset) {
        super(radius, offset);
    }

    @Override
    public int foliageHeight(WorldgenRandom random, int trunkHeight, TreeConfig config) {
        return 0;
    }

    @Override
    protected void createFoliage(FeatureWorldView view, WorldgenRandom random, TreeConfig config, int trunkHeight,
                                 TrunkPlacer.FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset,
                                 java.util.List<BlockPos> leaves) {
        placeLeavesRow(view, random, config, attachment.pos(), foliageRadius + attachment.radiusOffset(), offset, attachment.doubleTrunk(), leaves);
        placeLeavesRow(view, random, config, attachment.pos(), foliageRadius - 1, offset - 1, attachment.doubleTrunk(), leaves);
        placeLeavesRow(view, random, config, attachment.pos(), foliageRadius + attachment.radiusOffset(), offset - 2, attachment.doubleTrunk(), leaves);
    }

    // vanilla AcaciaFoliagePlacer.shouldSkipLocation — 기본(블롭) 규칙과 달리 RNG를 소비하지 않는다.
    @Override
    protected boolean shouldSkipLocation(WorldgenRandom random, int absX, int y, int absZ, int radius, boolean giantTrunk) {
        return y == 0 ? (absX > 1 || absZ > 1) && absX != 0 && absZ != 0 : absX == radius && absZ == radius && radius > 0;
    }
}
