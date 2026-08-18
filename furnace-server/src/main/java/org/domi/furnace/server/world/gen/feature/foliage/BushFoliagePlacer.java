package org.domi.furnace.server.world.gen.feature.foliage;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

// vanilla BushFoliagePlacer 포팅 (사바나 아카시아 덤불 변종 등). vanilla는 BlobFoliagePlacer를 상속하지만
// Furnace의 BlobFoliagePlacer는 final이라(FancyFoliagePlacer와 동일 사유) FoliagePlacer를 직접 확장하고
// Blob의 foliageHeight(상수 height 반환)을 그대로 복제한다.
public final class BushFoliagePlacer extends FoliagePlacer {
    private final int height;

    public BushFoliagePlacer(TreeIntProvider radius, TreeIntProvider offset, int height) {
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
        for (int y = offset; y >= offset - foliageHeight; y--) {
            int radius = foliageRadius + attachment.radiusOffset() - 1 - y;
            placeLeavesRow(view, random, config, attachment.pos(), radius, y, attachment.doubleTrunk(), leaves);
        }
    }

    @Override
    protected boolean shouldSkipLocation(WorldgenRandom random, int absX, int y, int absZ, int radius, boolean giantTrunk) {
        return absX == radius && absZ == radius && random.nextInt(2) == 0;
    }
}
