package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class KelpFeature implements ConfiguredFeature {

    private static final int WATER_MIN_STATE = 80;
    private static final int WATER_MAX_STATE = 95;
    private static final int WATER_SOURCE_STATE = 80;

    private final int kelpDefaultStateId;
    private final int kelpPlantStateId;
    private final int[] kelpTopStateIds;
    private final int magmaBlockStateId;

    public KelpFeature(int kelpDefaultStateId, int kelpPlantStateId, int[] kelpTopStateIds, int magmaBlockStateId) {
        this.kelpDefaultStateId = kelpDefaultStateId;
        this.kelpPlantStateId = kelpPlantStateId;
        this.kelpTopStateIds = kelpTopStateIds.clone();
        this.magmaBlockStateId = magmaBlockStateId;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int placeY = view.getOceanFloorY(x, z) + 1;
        if (placeY < view.minY() || placeY >= (view.maxY() + 1)) {
            return false;
        }
        if (!isSourceWater(view.getBlock(x, placeY, z))) return false;

        int placed = 0;
        int height = random.nextInt(10) + 1;
        int currentY = placeY;
        for (int offset = 0; offset <= height; offset++) {
            if (currentY < view.minY() || currentY >= (view.maxY() + 1)) break;
            int current = view.getBlock(x, currentY, z);
            int above = (currentY + 1 < (view.maxY() + 1))
                ? view.getBlock(x, currentY + 1, z)
                : 0;
            if (isSourceWater(current) && isSourceWater(above) && canGrowInto(view.getBlock(x, currentY - 1, z))) {
                if (offset == height) {
                    view.setBlock(x, currentY, z, kelpTopState(random));
                    placed++;
                } else {
                    view.setBlock(x, currentY, z, kelpPlantStateId);
                }
            } else if (offset > 0) {
                int belowY = currentY - 1;
                if (canGrowInto(view.getBlock(x, belowY - 1, z)) && view.getBlock(x, belowY, z) != kelpDefaultStateId) {
                    view.setBlock(x, belowY, z, kelpTopState(random));
                    placed++;
                }
                break;
            }
            currentY++;
        }

        return placed > 0;
    }

    private boolean canGrowInto(int below) {
        return below == kelpPlantStateId || (below != 0 && !isWater(below) && below != magmaBlockStateId);
    }

    private int kelpTopState(WorldgenRandom random) {
        return kelpTopStateIds[random.nextInt(kelpTopStateIds.length)];
    }

    private static boolean isWater(int stateId) {
        return stateId >= WATER_MIN_STATE && stateId <= WATER_MAX_STATE;
    }

    private static boolean isSourceWater(int stateId) {
        return stateId == WATER_SOURCE_STATE;
    }
}
