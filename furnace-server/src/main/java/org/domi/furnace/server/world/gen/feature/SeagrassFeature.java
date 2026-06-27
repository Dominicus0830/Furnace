package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class SeagrassFeature implements ConfiguredFeature {

    private static final int WATER_MIN_STATE = 80;
    private static final int WATER_MAX_STATE = 95;
    private static final int WATER_SOURCE_STATE = 80;

    private final float tallProbability;
    private final int seagrassStateId;
    private final int tallLowerStateId;
    private final int tallUpperStateId;
    private final int magmaBlockStateId;

    public SeagrassFeature(
            float tallProbability,
            int seagrassStateId,
            int tallLowerStateId,
            int tallUpperStateId,
            int magmaBlockStateId) {
        this.tallProbability = tallProbability;
        this.seagrassStateId = seagrassStateId;
        this.tallLowerStateId = tallLowerStateId;
        this.tallUpperStateId = tallUpperStateId;
        this.magmaBlockStateId = magmaBlockStateId;
    }

    private static final boolean SEA_DEBUG = Boolean.getBoolean("furnace.seaDebug");
    private static int SEA_CALL_NUM = 0;

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int dx1 = random.nextInt(8), dx2 = random.nextInt(8);
        int dz1 = random.nextInt(8), dz2 = random.nextInt(8);
        int placeX = x + dx1 - dx2;
        int placeZ = z + dz1 - dz2;
        int placeY = view.getOceanFloorY(placeX, placeZ) + 1;
        boolean inCenter = (placeX >= view.cx()*16 && placeX < view.cx()*16+16 && placeZ >= view.cz()*16 && placeZ < view.cz()*16+16);
        if (SEA_DEBUG) System.out.printf("SEA#%d origin=(%d,%d) scatter=(%d,%d,%d) inCenter=%b water=%b%n",
            SEA_CALL_NUM++, x, z, placeX, placeY, placeZ, inCenter, isWater(view.getBlock(placeX, placeY, placeZ)));
        if (placeY < org.domi.furnace.server.world.ChunkColumn.MIN_Y) return false;
        if (!isWater(view.getBlock(placeX, placeY, placeZ))) return false;
        boolean isTall = random.nextDouble() < tallProbability;
        // vanilla canSurvive: floor must be solid (not water/air/magma) — SeagrassBlock.mayPlaceOn
        int floor = view.getBlock(placeX, placeY - 1, placeZ);
        if (!isSolidFloor(floor) || floor == magmaBlockStateId) return true;
        if (isTall) {
            if (isWater(view.getBlock(placeX, placeY + 1, placeZ))) {
                view.setBlock(placeX, placeY, placeZ, tallLowerStateId);
                view.setBlock(placeX, placeY + 1, placeZ, tallUpperStateId);
            }
        } else {
            view.setBlock(placeX, placeY, placeZ, seagrassStateId);
        }
        return true;
    }

    // BushBlock.mayPlaceOn: 블록이 solid face를 위로 제공해야 함.
    // 물, 공기, 식물류는 face-sturdy 아님. 모든 solid full-cube 블록은 face-sturdy.
    private static boolean isSolidFloor(int stateId) {
        if (stateId == 0 || stateId == 12958 || stateId == 12959) return false; // air
        if (stateId >= 80 && stateId <= 95) return false;   // water
        // seagrass / tall_seagrass: not solid
        if (stateId >= 2008 && stateId <= 2013) return false;
        // kelp_plant, kelp: not solid
        if (stateId >= 12782 && stateId <= 12786) return false;
        return true;
    }

    private static boolean isWater(int stateId) {
        return stateId >= WATER_MIN_STATE && stateId <= WATER_MAX_STATE;
    }
}
