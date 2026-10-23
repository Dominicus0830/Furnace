package org.domi.furnace.server.world.gen.feature.foliage;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeFeature;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

// vanilla RandomSpreadFoliagePlacer 포팅 (맹그로브 등). attachment 주변 상자 안에서 무작위 좌표를
// leafPlacementAttempts회 뽑아 하나씩 잎을 시도한다. shouldSkipLocation은 항상 false(호출되지 않음 —
// placeLeavesRow를 쓰지 않기 때문).
public final class RandomSpreadFoliagePlacer extends FoliagePlacer {
    private final TreeIntProvider foliageHeightProvider;
    private final int leafPlacementAttempts;

    public RandomSpreadFoliagePlacer(TreeIntProvider radius, TreeIntProvider offset, TreeIntProvider foliageHeightProvider, int leafPlacementAttempts) {
        super(radius, offset);
        this.foliageHeightProvider = foliageHeightProvider;
        this.leafPlacementAttempts = leafPlacementAttempts;
    }

    @Override
    public int foliageHeight(WorldgenRandom random, int trunkHeight, TreeConfig config) {
        return foliageHeightProvider.sample(random);
    }

    @Override
    protected void createFoliage(FeatureWorldView view, WorldgenRandom random, TreeConfig config, int trunkHeight,
                                 TrunkPlacer.FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset,
                                 List<BlockPos> leaves) {
        BlockPos pos = attachment.pos();
        for (int i = 0; i < leafPlacementAttempts; i++) {
            int dx = random.nextInt(foliageRadius) - random.nextInt(foliageRadius);
            int dy = random.nextInt(foliageHeight) - random.nextInt(foliageHeight);
            int dz = random.nextInt(foliageRadius) - random.nextInt(foliageRadius);
            BlockPos leafPos = new BlockPos(pos.x() + dx, pos.y() + dy, pos.z() + dz);
            if (TreeFeature.validTreePos(view, leafPos, config)) {
                view.setBlock(leafPos.x(), leafPos.y(), leafPos.z(), config.leafStateId(random, view.getBlock(leafPos.x(), leafPos.y(), leafPos.z()), 7));
                leaves.add(leafPos);
            }
        }
    }
}
