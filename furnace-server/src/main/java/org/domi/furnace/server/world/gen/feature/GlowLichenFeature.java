package org.domi.furnace.server.world.gen.feature;

import java.util.Arrays;
import java.util.Set;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class GlowLichenFeature implements ConfiguredFeature {

    private static final int WATER_MIN_STATE = 80;
    private static final int WATER_MAX_STATE = 95;
    private static final int WATER_SOURCE_STATE = 80;

    private static final int DOWN = 0;
    private static final int UP = 1;
    private static final int NORTH = 2;
    private static final int SOUTH = 3;
    private static final int WEST = 4;
    private static final int EAST = 5;

    private static final int[][] OFFSETS = {
        {0, -1, 0},
        {0, 1, 0},
        {0, 0, -1},
        {0, 0, 1},
        {-1, 0, 0},
        {1, 0, 0}
    };

    private static final int[] OPPOSITES = {UP, DOWN, SOUTH, NORTH, EAST, WEST};

    private static final int[] ALL_DIRECTIONS = {DOWN, UP, NORTH, SOUTH, WEST, EAST};

    private static final int DOWN_BIT = 64;
    private static final int EAST_BIT = 32;
    private static final int NORTH_BIT = 16;
    private static final int SOUTH_BIT = 8;
    private static final int UP_BIT = 4;
    private static final int WATERLOGGED_BIT = 2;
    private static final int WEST_BIT = 1;

    private final int searchRange;
    private final float chanceOfSpreading;
    private final Set<Integer> canBePlacedOnIds;
    private final int glowLichenBaseStateId;
    private final int[] validDirections;

    public GlowLichenFeature(
            int searchRange,
            boolean canPlaceOnCeiling,
            boolean canPlaceOnFloor,
            boolean canPlaceOnWall,
            float chanceOfSpreading,
            Set<Integer> canBePlacedOnIds,
            int glowLichenBaseStateId) {
        this.searchRange = searchRange;
        this.chanceOfSpreading = chanceOfSpreading;
        this.canBePlacedOnIds = Set.copyOf(canBePlacedOnIds);
        this.glowLichenBaseStateId = glowLichenBaseStateId;
        this.validDirections = buildValidDirections(canPlaceOnCeiling, canPlaceOnFloor, canPlaceOnWall);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int stateId = view.getBlock(x, y, z);
        if (!isAirOrWater(stateId)) {
            return false;
        }

        int[] shuffledDirections = getShuffledDirections(random);
        if (placeGrowthIfPossible(view, random, x, y, z, shuffledDirections)) {
            return true;
        }

        for (int direction : shuffledDirections) {
            int[] shuffledDirectionsExcept = getShuffledDirectionsExcept(random, OPPOSITES[direction]);
            // vanilla quirk: setWithOffset(blockPos, direction)이 매 반복 origin 기준 → 탐색이 전진하지 않고
            // origin+1 위치만 searchRange번 재시도한다. byte-exact 위해 그대로 재현.
            int currentX = x + OFFSETS[direction][0];
            int currentY = y + OFFSETS[direction][1];
            int currentZ = z + OFFSETS[direction][2];
            for (int i = 0; i < searchRange; i++) {
                int currentState = view.getBlock(currentX, currentY, currentZ);
                if (!isAirOrWater(currentState) && !isGlowLichen(currentState)) {
                    break;
                }
                if (placeGrowthIfPossible(view, random, currentX, currentY, currentZ, shuffledDirectionsExcept)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean placeGrowthIfPossible(FeatureWorldView view, WorldgenRandom random, int x, int y, int z, int[] directions) {
        for (int direction : directions) {
            int adjacentX = x + OFFSETS[direction][0];
            int adjacentY = y + OFFSETS[direction][1];
            int adjacentZ = z + OFFSETS[direction][2];
            if (!canBePlacedOnIds.contains(view.getBlock(adjacentX, adjacentY, adjacentZ))) {
                continue;
            }

            int newState = computeStateForPlacement(view, x, y, z, direction);
            if (newState == -1) {
                return false;
            }

            view.setBlock(x, y, z, newState);
            if (random.nextFloat() < chanceOfSpreading) {
                spreadFromFaceTowardRandomDirection(view, random, x, y, z, direction);
            }
            return true;
        }
        return false;
    }

    private int computeStateForPlacement(FeatureWorldView view, int x, int y, int z, int faceDir) {
        int stateId = view.getBlock(x, y, z);
        if (!isValidStateForPlacement(view, stateId, x, y, z, faceDir)) {
            return -1;
        }

        int newFlags = currentFlags(stateId) | directionBit(faceDir);
        boolean isWaterSource = stateId == WATER_SOURCE_STATE;
        int finalFlags = newFlags | (isWaterSource ? WATERLOGGED_BIT : 0);
        if ((finalFlags & ~WATERLOGGED_BIT) == 0) {
            return -1;
        }
        return glowLichenBaseStateId - finalFlags;
    }

    private void spreadFromFaceTowardRandomDirection(FeatureWorldView view, WorldgenRandom random, int x, int y, int z, int spreadDir) {
        int sourceState = view.getBlock(x, y, z);
        if (!hasFace(sourceState, spreadDir)) {
            return;
        }

        int[] allDirections = allDirectionsShuffled(random);
        for (int face : allDirections) {
            if (axis(face) == axis(spreadDir)) {
                continue;
            }
            if (hasFace(sourceState, face)) {
                continue;
            }

            if (trySpread(view, x, y, z, face, spreadDir, 0)) {
                return;
            }
            if (trySpread(view, x, y, z, face, spreadDir, 1)) {
                return;
            }
            if (trySpread(view, x, y, z, face, spreadDir, 2)) {
                return;
            }
        }
    }

    private boolean trySpread(FeatureWorldView view, int x, int y, int z, int face, int spreadDir, int spreadType) {
        int targetX;
        int targetY;
        int targetZ;
        int targetFace;
        if (spreadType == 0) {
            targetX = x;
            targetY = y;
            targetZ = z;
            targetFace = face;
        } else if (spreadType == 1) {
            targetX = x + OFFSETS[face][0];
            targetY = y + OFFSETS[face][1];
            targetZ = z + OFFSETS[face][2];
            targetFace = spreadDir;
        } else {
            targetX = x + OFFSETS[face][0] + OFFSETS[spreadDir][0];
            targetY = y + OFFSETS[face][1] + OFFSETS[spreadDir][1];
            targetZ = z + OFFSETS[face][2] + OFFSETS[spreadDir][2];
            targetFace = OPPOSITES[face];
        }

        if (!canSpreadInto(view, targetX, targetY, targetZ, targetFace)) {
            return false;
        }

        int newState = computeStateForPlacement(view, targetX, targetY, targetZ, targetFace);
        if (newState == -1) {
            return false;
        }

        view.setBlock(targetX, targetY, targetZ, newState);
        return true;
    }

    private boolean canSpreadInto(FeatureWorldView view, int x, int y, int z, int faceDir) {
        int targetState = view.getBlock(x, y, z);
        boolean canReplace = targetState == 0 || targetState == 12958 || targetState == 12959
                || targetState == WATER_SOURCE_STATE || isGlowLichen(targetState);
        return canReplace && isValidStateForPlacement(view, targetState, x, y, z, faceDir);
    }

    private boolean isValidStateForPlacement(FeatureWorldView view, int stateId, int x, int y, int z, int faceDir) {
        // vanilla MultifaceBlock.isValidStateForPlacement = isFaceSupported(전방향 true) 기준.
        // canPlaceOnFloor 등 feature 설정은 방향 목록 필터로만 작용 — spread는 DOWN면도 배치 가능.
        if (hasFace(stateId, faceDir)) {
            return false;
        }
        int adjacentX = x + OFFSETS[faceDir][0];
        int adjacentY = y + OFFSETS[faceDir][1];
        int adjacentZ = z + OFFSETS[faceDir][2];
        int adjacentState = view.getBlock(adjacentX, adjacentY, adjacentZ);
        // spreading uses canAttachTo() (any solid face) not canBePlacedOn list — matches vanilla
        return !isAirOrWater(adjacentState) && !isGlowLichen(adjacentState);
    }

    private int[] getShuffledDirections(WorldgenRandom random) {
        int[] copy = Arrays.copyOf(validDirections, validDirections.length);
        shuffle(copy, random);
        return copy;
    }

    private int[] allDirectionsShuffled(WorldgenRandom random) {
        int[] copy = Arrays.copyOf(ALL_DIRECTIONS, ALL_DIRECTIONS.length);
        shuffle(copy, random);
        return copy;
    }

    private int[] getShuffledDirectionsExcept(WorldgenRandom random, int excludeDir) {
        int count = 0;
        for (int dir : validDirections) {
            if (dir != excludeDir) {
                count++;
            }
        }
        int[] filtered = new int[count];
        int index = 0;
        for (int dir : validDirections) {
            if (dir != excludeDir) {
                filtered[index++] = dir;
            }
        }
        shuffle(filtered, random);
        return filtered;
    }

    private static void shuffle(int[] values, WorldgenRandom random) {
        for (int i = values.length; i > 1; i--) {
            int j = random.nextInt(i);
            int tmp = values[i - 1];
            values[i - 1] = values[j];
            values[j] = tmp;
        }
    }

    private int currentFlags(int stateId) {
        if (isGlowLichen(stateId)) {
            return glowLichenBaseStateId - stateId;
        }
        if (isWater(stateId)) {
            return WATERLOGGED_BIT;
        }
        return 0;
    }

    private boolean isGlowLichen(int stateId) {
        return stateId <= glowLichenBaseStateId && stateId >= glowLichenBaseStateId - 127;
    }

    private boolean hasFace(int stateId, int dir) {
        return isGlowLichen(stateId) && ((glowLichenBaseStateId - stateId) & directionBit(dir)) != 0;
    }

    private static boolean isAirOrWater(int stateId) {
        return stateId == 0 || stateId == 12958 || stateId == 12959 || isWater(stateId);
    }

    private static boolean isWater(int stateId) {
        return stateId >= WATER_MIN_STATE && stateId <= WATER_MAX_STATE;
    }

    private static int buildDirectionMask(boolean canPlaceOnCeiling, boolean canPlaceOnFloor, boolean canPlaceOnWall) {
        int count = 0;
        if (canPlaceOnCeiling) count++;
        if (canPlaceOnFloor) count++;
        if (canPlaceOnWall) count += 4;
        return count;
    }

    private static int[] buildValidDirections(boolean canPlaceOnCeiling, boolean canPlaceOnFloor, boolean canPlaceOnWall) {
        int[] dirs = new int[buildDirectionMask(canPlaceOnCeiling, canPlaceOnFloor, canPlaceOnWall)];
        int index = 0;
        if (canPlaceOnCeiling) {
            dirs[index++] = UP;
        }
        if (canPlaceOnFloor) {
            dirs[index++] = DOWN;
        }
        if (canPlaceOnWall) {
            dirs[index++] = NORTH;
            dirs[index++] = EAST;
            dirs[index++] = SOUTH;
            dirs[index] = WEST;
        }
        return dirs;
    }

    private static int axis(int dir) {
        return switch (dir) {
            case DOWN, UP -> 0;
            case NORTH, SOUTH -> 1;
            case WEST, EAST -> 2;
            default -> -1;
        };
    }

    private static int directionBit(int dir) {
        return switch (dir) {
            case DOWN -> DOWN_BIT;
            case UP -> UP_BIT;
            case NORTH -> NORTH_BIT;
            case SOUTH -> SOUTH_BIT;
            case WEST -> WEST_BIT;
            case EAST -> EAST_BIT;
            default -> 0;
        };
    }
}
