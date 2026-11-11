package org.domi.furnace.server.world.gen.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.MathHelper;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.noise.LegacyNormalNoise;

public final class GeodeFeature implements ConfiguredFeature {

    private static final Direction[] DIRECTIONS = Direction.values();

    private final GeodeConfig config;

    public GeodeFeature(GeodeConfig config) {
        this.config = config;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        List<DistributionPoint> distributionPoints = new ArrayList<>();
        int pointCount = config.distributionPoints.sample(random);
        LegacyNormalNoise noise = new LegacyNormalNoise(view.worldSeed(), -4, new double[]{1.0});
        List<BlockPos> crackPoints = new ArrayList<>();
        double wallDistanceScale = (double) pointCount / (double) config.outerWallDistance.maxValue();
        double fillingThreshold = 1.0 / Math.sqrt(config.filling + 0.0);
        double innerThreshold = 1.0 / Math.sqrt(config.innerLayer + wallDistanceScale);
        double middleThreshold = 1.0 / Math.sqrt(config.middleLayer + wallDistanceScale);
        double outerThreshold = 1.0 / Math.sqrt(config.outerLayer + wallDistanceScale);
        double crackThreshold = 1.0 / Math.sqrt(
            config.baseCrackSize + random.nextDouble() / 2.0 + (pointCount > 3 ? wallDistanceScale : 0.0));
        boolean generateCrack = random.nextFloat() < config.generateCrackChance;
        int invalidCount = 0;

        for (int i = 0; i < pointCount; i++) {
            int dx = config.outerWallDistance.sample(random);
            int dy = config.outerWallDistance.sample(random);
            int dz = config.outerWallDistance.sample(random);
            BlockPos point = new BlockPos(x + dx, y + dy, z + dz);
            int state = view.getBlock(point.x, point.y, point.z);
            if (isAir(state) || config.invalidBlocks.contains(state)) {
                if (++invalidCount > config.invalidBlocksThreshold) {
                    return false;
                }
            }
            distributionPoints.add(new DistributionPoint(point, config.pointOffset.sample(random)));
        }

        if (generateCrack) {
            int crackDir = random.nextInt(4);
            int offset = pointCount * 2 + 1;
            if (crackDir == 0) {
                crackPoints.add(new BlockPos(x + offset, y + 7, z));
                crackPoints.add(new BlockPos(x + offset, y + 5, z));
                crackPoints.add(new BlockPos(x + offset, y + 1, z));
            } else if (crackDir == 1) {
                crackPoints.add(new BlockPos(x, y + 7, z + offset));
                crackPoints.add(new BlockPos(x, y + 5, z + offset));
                crackPoints.add(new BlockPos(x, y + 1, z + offset));
            } else if (crackDir == 2) {
                crackPoints.add(new BlockPos(x + offset, y + 7, z + offset));
                crackPoints.add(new BlockPos(x + offset, y + 5, z + offset));
                crackPoints.add(new BlockPos(x + offset, y + 1, z + offset));
            } else {
                crackPoints.add(new BlockPos(x, y + 7, z));
                crackPoints.add(new BlockPos(x, y + 5, z));
                crackPoints.add(new BlockPos(x, y + 1, z));
            }
        }

        List<BlockPos> potentialPlacements = new ArrayList<>();
        for (int bz = z + config.minGenOffset; bz <= z + config.maxGenOffset; bz++) {
            for (int by = y + config.minGenOffset; by <= y + config.maxGenOffset; by++) {
                for (int bx = x + config.minGenOffset; bx <= x + config.maxGenOffset; bx++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    double noiseValue = noise.getValue(bx, by, bz) * config.noiseMultiplier;
                    double distributionScore = 0.0;
                    double crackScore = 0.0;

                    for (DistributionPoint point : distributionPoints) {
                        distributionScore += fastInvSqrt(pos.distSqr(point.pos) + point.offset) + noiseValue;
                    }
                    for (BlockPos crackPoint : crackPoints) {
                        crackScore += fastInvSqrt(pos.distSqr(crackPoint) + config.crackPointOffset) + noiseValue;
                    }

                    if (distributionScore < outerThreshold) continue;

                    if (generateCrack && crackScore >= crackThreshold && distributionScore < fillingThreshold) {
                        safeSetBlock(view, pos, config.airId);
                        // 바닐라 GeodeFeature: 균열 칸의 6방향 이웃(Direction.values 순)에 유체가 있으면 그 유체 틱(지연 0)
                        for (Direction d : DIRECTIONS) view.scheduleFluidTickIfFluid(pos.x + d.dx, pos.y + d.dy, pos.z + d.dz, 0);
                    } else if (distributionScore >= fillingThreshold) {
                        safeSetBlock(view, pos, config.fillingStateId);
                    } else if (distributionScore >= innerThreshold) {
                        boolean useAlternate = random.nextFloat() < config.useAlternateLayer0Chance;
                        safeSetBlock(view, pos, useAlternate ? config.alternateInnerLayerStateId : config.innerLayerStateId);
                        if ((!config.placementsRequireLayer0Alternate || useAlternate)
                            && random.nextFloat() < config.usePotentialPlacementsChance) {
                            potentialPlacements.add(pos);
                        }
                    } else if (distributionScore >= middleThreshold) {
                        safeSetBlock(view, pos, config.middleLayerStateId);
                    } else if (distributionScore >= outerThreshold) {
                        safeSetBlock(view, pos, config.outerLayerStateId);
                    }
                }
            }
        }

        for (BlockPos placementPos : potentialPlacements) {
            String placementName = config.innerPlacementNames[random.nextInt(config.innerPlacementNames.length)];
            for (Direction direction : DIRECTIONS) {
                BlockPos target = placementPos.relative(direction);
                int adjacentState = view.getBlock(target.x, target.y, target.z);
                if (canClusterGrowAtState(adjacentState)) {
                    Map<String, String> props = Map.of(
                        "facing", direction.propertyName,
                        "waterlogged", Boolean.toString(isWater(adjacentState)));
                    int placementState = config.stateResolver.resolve(placementName, props);
                    safeSetBlock(view, target, placementState);
                    break;
                }
            }
        }

        return true;
    }

    private void safeSetBlock(FeatureWorldView view, BlockPos pos, int stateId) {
        int current = view.getBlock(pos.x, pos.y, pos.z);
        if (!config.cannotReplace.contains(current)) {
            view.setBlock(pos.x, pos.y, pos.z, stateId);
        }
    }

    private static boolean canClusterGrowAtState(int stateId) {
        return isAir(stateId) || isWater(stateId);
    }

    private static boolean isAir(int stateId) {
        return stateId == ChunkSection.AIR || stateId == 12958 || stateId == 12959;
    }

    private static boolean isWater(int stateId) {
        return stateId == 80;
    }

    private static double fastInvSqrt(double value) {
        return 1.0 / Math.sqrt(value);
    }

    private record DistributionPoint(BlockPos pos, int offset) {}

    private record BlockPos(int x, int y, int z) {
        BlockPos relative(Direction direction) {
            return new BlockPos(x + direction.dx, y + direction.dy, z + direction.dz);
        }

        double distSqr(BlockPos other) {
            return MathHelper.square((double) x - other.x)
                + MathHelper.square((double) y - other.y)
                + MathHelper.square((double) z - other.z);
        }
    }

    private enum Direction {
        DOWN(0, -1, 0, "down"),
        UP(0, 1, 0, "up"),
        NORTH(0, 0, -1, "north"),
        SOUTH(0, 0, 1, "south"),
        WEST(-1, 0, 0, "west"),
        EAST(1, 0, 0, "east");

        private final int dx;
        private final int dy;
        private final int dz;
        private final String propertyName;

        Direction(int dx, int dy, int dz, String propertyName) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.propertyName = propertyName;
        }
    }

    @FunctionalInterface
    public interface StateResolver {
        int resolve(String name, Map<String, String> properties);
    }

    public record IntRange(int minInclusive, int maxInclusive) {
        public int sample(WorldgenRandom random) {
            return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
        }

        public int maxValue() {
            return maxInclusive;
        }
    }

    public record GeodeConfig(
        int fillingStateId,
        int innerLayerStateId,
        int alternateInnerLayerStateId,
        int middleLayerStateId,
        int outerLayerStateId,
        String[] innerPlacementNames,
        Set<Integer> cannotReplace,
        Set<Integer> invalidBlocks,
        double filling,
        double innerLayer,
        double middleLayer,
        double outerLayer,
        double generateCrackChance,
        double baseCrackSize,
        int crackPointOffset,
        double usePotentialPlacementsChance,
        double useAlternateLayer0Chance,
        boolean placementsRequireLayer0Alternate,
        IntRange outerWallDistance,
        IntRange distributionPoints,
        IntRange pointOffset,
        int minGenOffset,
        int maxGenOffset,
        double noiseMultiplier,
        int invalidBlocksThreshold,
        int airId,
        StateResolver stateResolver
    ) {}
}
