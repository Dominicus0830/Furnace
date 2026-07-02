package org.domi.furnace.server.world.gen.feature.foliage;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeFeature;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

public abstract class FoliagePlacer {

    protected final TreeIntProvider radius;
    protected final TreeIntProvider offset;

    protected FoliagePlacer(TreeIntProvider radius, TreeIntProvider offset) {
        this.radius = radius;
        this.offset = offset;
    }

    public abstract int foliageHeight(WorldgenRandom random, int trunkHeight, TreeConfig config);

    public int foliageRadius(WorldgenRandom random, int trunkHeight) {
        return radius.sample(random);
    }

    public final void createFoliage(
            FeatureWorldView view,
            WorldgenRandom random,
            TreeConfig config,
            int trunkHeight,
            TrunkPlacer.FoliageAttachment attachment,
            int foliageHeight,
            int foliageRadius,
            java.util.List<BlockPos> leaves) {
        int offsetY = offset.sample(random);
        createFoliage(view, random, config, trunkHeight, attachment, foliageHeight, foliageRadius, offsetY, leaves);
    }

    protected abstract void createFoliage(
        FeatureWorldView view, WorldgenRandom random, TreeConfig config, int trunkHeight,
        TrunkPlacer.FoliageAttachment attachment, int foliageHeight, int foliageRadius, int offset,
        java.util.List<BlockPos> leaves);

    protected final void placeLeavesRow(
            FeatureWorldView view, WorldgenRandom random, TreeConfig config, BlockPos center, int radius, int y, boolean giantTrunk,
            java.util.List<BlockPos> leaves) {
        int rowY = center.y() + y;
        for (int dx = -radius; dx <= radius + (giantTrunk ? 1 : 0); dx++) {
            for (int dz = -radius; dz <= radius + (giantTrunk ? 1 : 0); dz++) {
                if (shouldSkipLocationSigned(random, randomlessAbs(dx), y, randomlessAbs(dz), radius, giantTrunk)) continue;
                int wx = center.x() + dx;
                int wz = center.z() + dz;
                if (TreeFeature.validTreePos(view, new BlockPos(wx, rowY, wz), config)) {
                    view.setBlock(wx, rowY, wz, config.leafStateId(view.getBlock(wx, rowY, wz), 7));
                    leaves.add(new BlockPos(wx, rowY, wz));
                }
            }
        }
    }

    protected int randomlessAbs(int v) {
        return Math.abs(v);
    }

    protected boolean shouldSkipLocationSigned(WorldgenRandom random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        if (giantTrunk) {
            int x = Math.min(Math.abs(dx), Math.abs(dx - 1));
            int z = Math.min(Math.abs(dz), Math.abs(dz - 1));
            return x > radius || z > radius;
        }
        return dx == radius && dz == radius && (random.nextInt(2) == 0 || y == 0);
    }
}
