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
                if (shouldSkipLocationSigned(random, dx, y, dz, radius, giantTrunk)) continue;
                int wx = center.x() + dx;
                int wz = center.z() + dz;
                if (TreeFeature.validTreePos(view, new BlockPos(wx, rowY, wz), config)) {
                    view.setBlock(wx, rowY, wz, config.leafStateId(random, view.getBlock(wx, rowY, wz), 7));
                    leaves.add(new BlockPos(wx, rowY, wz));
                }
            }
        }
    }

    // vanilla FoliagePlacer.shouldSkipLocationSigned(mcpal L77-89) 포팅 — SIGNED dx/dz를 받아
    // giantTrunk 여부에 따라 abs를 1회만 적용한 뒤 shouldSkipLocation(서브클래스 오버라이드 가능)에 위임.
    // 이전 구현은 호출부(placeLeavesRow)가 randomlessAbs()로 미리 abs를 적용한 값을 다시 여기서 abs
    // 처리해(giantTrunk 분기) 이중 적용 버그가 있었음 — dx<0일 때 min(abs(dx),abs(dx-1)) 결과가 달라짐.
    // giantTrunk=true를 만드는 트렁크는 지금까지 전무했으므로(GiantTrunkPlacer 계열 신규 포팅 전) 실제로
    // 노출된 적 없는 잠재 버그였다.
    protected boolean shouldSkipLocationSigned(WorldgenRandom random, int dx, int y, int dz, int radius, boolean giantTrunk) {
        int absX;
        int absZ;
        if (giantTrunk) {
            absX = Math.min(Math.abs(dx), Math.abs(dx - 1));
            absZ = Math.min(Math.abs(dz), Math.abs(dz - 1));
        } else {
            absX = Math.abs(dx);
            absZ = Math.abs(dz);
        }
        return shouldSkipLocation(random, absX, y, absZ, radius, giantTrunk);
    }

    // vanilla FoliagePlacer.shouldSkipLocation의 기본(=BlobFoliagePlacer) 포뮬러. abs 적용이 끝난
    // localX/localZ를 받는다 — 서브클래스가 vanilla처럼 이 메서드를 오버라이드해 모양을 바꾼다.
    protected boolean shouldSkipLocation(WorldgenRandom random, int absX, int y, int absZ, int radius, boolean giantTrunk) {
        return absX == radius && absZ == radius && (random.nextInt(2) == 0 || y == 0);
    }

    // vanilla FoliagePlacer.placeLeavesRowWithHangingLeavesBelow(mcpal L114-150) 포팅 — cherry_foliage_placer
    // 전용. 잎 한 줄을 놓은 뒤, 그 줄 바로 아래에서 "이미 놓인 잎 아래"에만 확률적으로 매달린 잎(최대 2단)을 덧붙인다.
    // Direction.Plane.HORIZONTAL 순회 순서(NORTH,EAST,SOUTH,WEST)와 getClockWise 매핑을 그대로 재현.
    protected final void placeLeavesRowWithHangingLeavesBelow(
            FeatureWorldView view, WorldgenRandom random, TreeConfig config, BlockPos pos, int radius, int y, boolean giantTrunk,
            float hangingLeavesChance, float hangingLeavesExtensionChance, java.util.List<BlockPos> leaves) {
        placeLeavesRow(view, random, config, pos, radius, y, giantTrunk, leaves);
        int extra = giantTrunk ? 1 : 0;
        BlockPos logPos = new BlockPos(pos.x(), pos.y() - 1, pos.z());

        for (TreeFeature.Direction dir : HANGING_LEAVES_HORIZONTALS) {
            TreeFeature.Direction cw = clockWise(dir);
            int i1 = isPositiveHorizontalAxis(cw) ? radius + extra : radius;
            int mx = pos.x() + cw.dx() * i1 - dir.dx() * radius;
            int my = pos.y() + y - 1;
            int mz = pos.z() + cw.dz() * i1 - dir.dz() * radius;
            for (int i2 = -radius; i2 < radius + extra; i2++) {
                boolean isSet = leaves.contains(new BlockPos(mx, my + 1, mz));
                if (isSet && tryPlaceExtension(view, random, config, hangingLeavesChance, logPos, mx, my, mz, leaves)) {
                    tryPlaceExtension(view, random, config, hangingLeavesExtensionChance, logPos, mx, my - 1, mz, leaves);
                }
                mx += dir.dx();
                mz += dir.dz();
            }
        }
    }

    private static final TreeFeature.Direction[] HANGING_LEAVES_HORIZONTALS = {
        TreeFeature.Direction.NORTH, TreeFeature.Direction.EAST, TreeFeature.Direction.SOUTH, TreeFeature.Direction.WEST
    };

    private static TreeFeature.Direction clockWise(TreeFeature.Direction dir) {
        return switch (dir) {
            case NORTH -> TreeFeature.Direction.EAST;
            case EAST -> TreeFeature.Direction.SOUTH;
            case SOUTH -> TreeFeature.Direction.WEST;
            case WEST -> TreeFeature.Direction.NORTH;
            default -> throw new IllegalStateException("no horizontal clockwise for " + dir);
        };
    }

    private static boolean isPositiveHorizontalAxis(TreeFeature.Direction dir) {
        return dir == TreeFeature.Direction.SOUTH || dir == TreeFeature.Direction.EAST;
    }

    private boolean tryPlaceExtension(
            FeatureWorldView view, WorldgenRandom random, TreeConfig config, float chance, BlockPos logPos,
            int x, int y, int z, java.util.List<BlockPos> leaves) {
        int manhattan = Math.abs(x - logPos.x()) + Math.abs(y - logPos.y()) + Math.abs(z - logPos.z());
        if (manhattan >= 7) return false;
        if (random.nextFloat() > chance) return false;
        return tryPlaceLeaf(view, random, config, new BlockPos(x, y, z), leaves);
    }

    private boolean tryPlaceLeaf(FeatureWorldView view, WorldgenRandom random, TreeConfig config, BlockPos pos, java.util.List<BlockPos> leaves) {
        if (!TreeFeature.validTreePos(view, pos, config)) return false;
        view.setBlock(pos.x(), pos.y(), pos.z(), config.leafStateId(random, view.getBlock(pos.x(), pos.y(), pos.z()), 7));
        leaves.add(pos);
        return true;
    }
}
