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
        // 바닐라: pos = attachment.pos().above(offset), y는 offset과 무관하게 -1..2. 큰(2x2) 줄기 부착점만 4줄+nextBoolean, 가지 부착점은 2줄
        BlockPos pos = new BlockPos(attachment.pos().x(), attachment.pos().y() + offset, attachment.pos().z());
        boolean giant = attachment.doubleTrunk();
        if (giant) {
            placeLeavesRow(view, random, config, pos, foliageRadius + 2, -1, true, leaves);
            placeLeavesRow(view, random, config, pos, foliageRadius + 3, 0, true, leaves);
            placeLeavesRow(view, random, config, pos, foliageRadius + 2, 1, true, leaves);
            if (random.nextBoolean()) {
                placeLeavesRow(view, random, config, pos, foliageRadius, 2, true, leaves);
            }
        } else {
            placeLeavesRow(view, random, config, pos, foliageRadius + 2, -1, false, leaves);
            placeLeavesRow(view, random, config, pos, foliageRadius + 1, 0, false, leaves);
        }
    }

    // vanilla DarkOakFoliagePlacer.shouldSkipLocationSigned: 큰 줄기의 y==0 줄 네 모서리(2x2 줄기 기준)는 건너뜀
    @Override
    protected boolean shouldSkipLocationSigned(WorldgenRandom random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        if (y == 0 && giantTrunk && (dx == -radius || dx >= radius) && (dz == -radius || dz >= radius)) {
            return true;
        }
        return super.shouldSkipLocationSigned(random, dx, y, dz, radius, giantTrunk);
    }

    // vanilla DarkOakFoliagePlacer.shouldSkipLocation
    @Override
    protected boolean shouldSkipLocation(WorldgenRandom random, int absX, int y, int absZ, int radius, boolean giantTrunk) {
        if (y == -1 && !giantTrunk) {
            return absX == radius && absZ == radius;
        }
        return y == 1 && absX + absZ > radius * 2 - 2;
    }
}
