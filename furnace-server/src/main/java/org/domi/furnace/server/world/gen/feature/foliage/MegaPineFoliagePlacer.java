package org.domi.furnace.server.world.gen.feature.foliage;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

// vanilla MegaPineFoliagePlacer 포팅 (거대 가문비나무, giant_trunk_placer와 짝). doubleTrunk(large)
// 분기가 실제로 발동하는 첫 foliage placer — FoliagePlacer.shouldSkipLocationSigned의 giant-trunk abs
// 이중 적용 버그 수정이 이 모양에 직결된다.
public final class MegaPineFoliagePlacer extends FoliagePlacer {
    private final TreeIntProvider crownHeight;

    public MegaPineFoliagePlacer(TreeIntProvider radius, TreeIntProvider offset, TreeIntProvider crownHeight) {
        super(radius, offset);
        this.crownHeight = crownHeight;
    }

    @Override
    public int foliageHeight(WorldgenRandom random, int trunkHeight, TreeConfig config) {
        return crownHeight.sample(random);
    }

    @Override
    protected void createFoliage(FeatureWorldView view, WorldgenRandom random, TreeConfig config, int trunkHeight,
                                 TrunkPlacer.FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset,
                                 List<BlockPos> leaves) {
        BlockPos pos = attachment.pos();
        int prevRadius = 0;
        for (int y = pos.y() - foliageHeight + offset; y <= pos.y() + offset; y++) {
            int depth = pos.y() - y;
            int radius = foliageRadius + attachment.radiusOffset() + floor((float) depth / foliageHeight * 3.5F);
            int rowRadius = (depth > 0 && radius == prevRadius && (y & 1) == 0) ? radius + 1 : radius;
            placeLeavesRow(view, random, config, new BlockPos(pos.x(), y, pos.z()), rowRadius, 0, attachment.doubleTrunk(), leaves);
            prevRadius = radius;
        }
    }

    @Override
    protected boolean shouldSkipLocation(WorldgenRandom random, int absX, int y, int absZ, int radius, boolean giantTrunk) {
        return absX + absZ >= 7 || absX * absX + absZ * absZ > radius * radius;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
