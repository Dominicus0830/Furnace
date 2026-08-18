package org.domi.furnace.server.world.gen.feature.foliage;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

// vanilla FancyFoliagePlacer 포팅 (fancy_oak 등). BlobFoliagePlacer가 final이라 상속 대신
// FoliagePlacer를 직접 확장 — foliageHeight/createFoliage/shouldSkipLocation을 vanilla와 동일하게 구현.
public final class FancyFoliagePlacer extends FoliagePlacer {
    private final int height;

    public FancyFoliagePlacer(TreeIntProvider radius, TreeIntProvider offset, int height) {
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
                                 List<BlockPos> leaves) {
        for (int i = offset; i >= offset - foliageHeight; i--) {
            int radius = foliageRadius + (i != offset && i != offset - foliageHeight ? 1 : 0);
            placeLeavesRow(view, random, config, attachment.pos(), radius, i, attachment.doubleTrunk(), leaves);
        }
    }

    @Override
    protected boolean shouldSkipLocation(WorldgenRandom random, int absX, int y, int absZ, int radius, boolean giantTrunk) {
        float lx = absX + 0.5F;
        float lz = absZ + 0.5F;
        return lx * lx + lz * lz > (float) (radius * radius);
    }
}
