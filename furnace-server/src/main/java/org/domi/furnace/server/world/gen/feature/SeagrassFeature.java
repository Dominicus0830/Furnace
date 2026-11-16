package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.StateIds;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class SeagrassFeature implements ConfiguredFeature {

    private static final int WATER_MIN_STATE = StateIds.WATER_FIRST;
    private static final int WATER_MAX_STATE = StateIds.WATER_LAST;
    private static final int WATER_SOURCE_STATE = StateIds.WATER_SOURCE;

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

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int dx1 = random.nextInt(8), dx2 = random.nextInt(8);
        int dz1 = random.nextInt(8), dz2 = random.nextInt(8);
        int placeX = x + dx1 - dx2;
        int placeZ = z + dz1 - dz2;
        int placeY = view.getOceanFloorY(placeX, placeZ) + 1;
        if (placeY < view.minY()) return false;
        if (!isWater(view.getBlock(placeX, placeY, placeZ))) return false;
        boolean isTall = random.nextDouble() < tallProbability;
        // vanilla canSurvive: floor must be solid (not water/air/magma) — SeagrassBlock.mayPlaceOn
        int floor = view.getBlock(placeX, placeY - 1, placeZ);
        if (!isSolidFloor(floor) || floor == magmaBlockStateId) return true;
        if (isTall) {
            // vanilla TallSeagrassBlock.canSurvive(LOWER): fluid at pos must be source water (amount==8)
            if (view.getBlock(placeX, placeY, placeZ) != WATER_SOURCE_STATE) return true;
            if (isWater(view.getBlock(placeX, placeY + 1, placeZ))) {
                view.setBlock(placeX, placeY, placeZ, tallLowerStateId);
                view.setBlock(placeX, placeY + 1, placeZ, tallUpperStateId);
            }
        } else {
            view.setBlock(placeX, placeY, placeZ, seagrassStateId);
        }
        return true;
    }

    // SeagrassBlock.mayPlaceOn: 위쪽 면이 단단한(isFaceSturdy UP, SupportType.FULL) 블록이어야 한다. 바닐라 export 표(BlockAttrs)로 그대로 판정 —
    // BlockSurvival 의 SEAGRASS 와 같은 규칙이다(마그마 블록 제외는 place 가 따로 본다).
    static boolean isSolidFloor(int stateId) {
        return BlockAttrs.get().isFaceSturdy(stateId, BlockAttrs.UP);
    }

    private static boolean isWater(int stateId) {
        return stateId >= WATER_MIN_STATE && stateId <= WATER_MAX_STATE;
    }
}
