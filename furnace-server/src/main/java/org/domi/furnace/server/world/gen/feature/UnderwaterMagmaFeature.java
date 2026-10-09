// 바닐라 UnderwaterMagmaFeature 포팅 — 해저 마그마 배치
package org.domi.furnace.server.world.gen.feature;

import java.util.OptionalInt;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class UnderwaterMagmaFeature implements ConfiguredFeature {

    private static final int WATER_MIN = 80;
    private static final int WATER_MAX = 95;
    private static final int MAGMA_BLOCK = 12543;

    private final int floorSearchRange;
    private final int placementRadius;
    private final float placementProbability;

    public UnderwaterMagmaFeature(int floorSearchRange, int placementRadius, float placementProbability) {
        this.floorSearchRange = floorSearchRange;
        this.placementRadius = placementRadius;
        this.placementProbability = placementProbability;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        OptionalInt floorOpt = getFloorY(view, x, y, z);
        if (floorOpt.isEmpty()) return false;

        int floorY = floorOpt.getAsInt();
        int r = placementRadius;
        int minX = x - r, maxX = x + r;
        int minY = floorY - r, maxY = floorY + r;
        int minZ = z - r, maxZ = z + r;

        boolean placed = false;
        // BlockPos.betweenClosedStream order: x-inner, y-mid, z-outer
        for (int bz = minZ; bz <= maxZ; bz++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bx = minX; bx <= maxX; bx++) {
                    if (random.nextFloat() < placementProbability && isValidPlacement(view, bx, by, bz)) {
                        view.setBlock(bx, by, bz, MAGMA_BLOCK);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    // Replicates vanilla Column.scan downward: scans from (x,y,z) while water, returns first non-water Y.
    private OptionalInt getFloorY(FeatureWorldView view, int x, int y, int z) {
        if (!isWater(view.getBlock(x, y, z))) return OptionalInt.empty();
        int curY = y;
        for (int i = 1; i < floorSearchRange && isWater(view.getBlock(x, curY, z)); i++) {
            curY--;
        }
        return !isWater(view.getBlock(x, curY, z)) ? OptionalInt.of(curY) : OptionalInt.empty();
    }

    private boolean isValidPlacement(FeatureWorldView view, int x, int y, int z) {
        if (isWaterOrAir(view.getBlock(x, y, z))) return false;
        if (isWaterOrAir(view.getBlock(x, y - 1, z))) return false;
        if (isWaterOrAir(view.getBlock(x + 1, y, z))) return false;
        if (isWaterOrAir(view.getBlock(x - 1, y, z))) return false;
        if (isWaterOrAir(view.getBlock(x, y, z + 1))) return false;
        if (isWaterOrAir(view.getBlock(x, y, z - 1))) return false;
        return true;
    }

    private static boolean isWater(int stateId) {
        return stateId >= WATER_MIN && stateId <= WATER_MAX;
    }

    private static boolean isWaterOrAir(int stateId) {
        return stateId == 0 || stateId == 12958 || stateId == 12959
            || (stateId >= WATER_MIN && stateId <= WATER_MAX);
    }

    private static boolean inWorldY(FeatureWorldView view, int y) {
        return y >= view.minY() && y < (view.maxY() + 1);
    }
}
