package org.domi.furnace.server.world.gen.feature.foliage;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

// vanilla MegaJungleFoliagePlacer 포팅 (registry id: jungle_foliage_placer, 정글 거목 — mega_jungle_trunk_placer와 짝).
public final class JungleFoliagePlacer extends FoliagePlacer {
    private final int height;

    public JungleFoliagePlacer(TreeIntProvider radius, TreeIntProvider offset, int height) {
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
        int rows = attachment.doubleTrunk() ? foliageHeight : 1 + random.nextInt(2);
        for (int y = offset; y >= offset - rows; y--) {
            int radius = foliageRadius + attachment.radiusOffset() + 1 - y;
            placeLeavesRow(view, random, config, attachment.pos(), radius, y, attachment.doubleTrunk(), leaves);
        }
    }

    @Override
    protected boolean shouldSkipLocation(WorldgenRandom random, int absX, int y, int absZ, int radius, boolean giantTrunk) {
        return absX + absZ >= 7 || absX * absX + absZ * absZ > radius * radius;
    }
}
