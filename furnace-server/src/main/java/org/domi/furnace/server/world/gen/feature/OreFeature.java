package org.domi.furnace.server.world.gen.feature;

import java.util.BitSet;
import java.util.List;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class OreFeature implements ConfiguredFeature {

    public static volatile boolean DEBUG_PLACE = false;

    private static final float[] MTH_SIN;

    static {
        MTH_SIN = new float[65536];
        for (int i = 0; i < 65536; i++) {
            MTH_SIN[i] = (float) Math.sin(i * Math.PI * 2.0 / 65536.0);
        }
    }

    private final List<Target> targets;
    private final int size;
    private final float discardChanceOnAirExposure;

    public OreFeature(List<Target> targets, int size, float discardChanceOnAirExposure) {
        this.targets = List.copyOf(targets);
        this.size = size;
        this.discardChanceOnAirExposure = discardChanceOnAirExposure;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        float angle = random.nextFloat() * (float) Math.PI;
        float horizontalScale = (float) size / 8.0f;
        int padding = (int) Math.ceil(((float) size / 16.0f * 2.0f + 1.0f) / 2.0f);
        double startX = (double) x + Math.sin(angle) * (double) horizontalScale;
        double endX = (double) x - Math.sin(angle) * (double) horizontalScale;
        double startZ = (double) z + Math.cos(angle) * (double) horizontalScale;
        double endZ = (double) z - Math.cos(angle) * (double) horizontalScale;
        double startY = (double) y + random.nextInt(3) - 2.0;
        double endY = (double) y + random.nextInt(3) - 2.0;
        int minX = x - (int) Math.ceil(horizontalScale) - padding;
        int minY = y - 2 - padding;
        int minZ = z - (int) Math.ceil(horizontalScale) - padding;
        int horizontalSize = 2 * ((int) Math.ceil(horizontalScale) + padding);
        int verticalSize = 2 * (2 + padding);

        boolean debugPlace = DEBUG_PLACE;
        for (int bx = minX; bx <= minX + horizontalSize; bx++) {
            for (int bz = minZ; bz <= minZ + horizontalSize; bz++) {
                int wgFloor = view.getWgOceanFloorHeight(bx, bz);
                if (minY <= wgFloor) {
                    if (debugPlace) {
                        System.out.println("PLACE_PASS x=" + x + " y=" + y + " z=" + z + " minY=" + minY + " wgFloor=" + wgFloor);
                    }
                    return doPlace(view, random, startX, endX, startZ, endZ, startY, endY,
                        minX, minY, minZ, horizontalSize, verticalSize);
                }
            }
        }
        if (debugPlace) {
            System.out.println("PLACE_FAIL x=" + x + " y=" + y + " z=" + z + " minY=" + minY);
        }
        return false;
    }

    private boolean doPlace(
            FeatureWorldView view,
            WorldgenRandom random,
            double startX,
            double endX,
            double startZ,
            double endZ,
            double startY,
            double endY,
            int minX,
            int minY,
            int minZ,
            int horizontalSize,
            int verticalSize) {
        BitSet visited = new BitSet(horizontalSize * verticalSize * horizontalSize);
        double[] samples = new double[size * 4];

        for (int i = 0; i < size; i++) {
            float progress = (float) i / (float) size;
            double centerX = lerp(progress, startX, endX);
            double centerY = lerp(progress, startY, endY);
            double centerZ = lerp(progress, startZ, endZ);
            double randomScale = random.nextDouble() * (double) size / 16.0;
            double radius = ((double) (mthSin((float) Math.PI * progress) + 1.0f) * randomScale + 1.0) / 2.0;
            samples[i * 4] = centerX;
            samples[i * 4 + 1] = centerY;
            samples[i * 4 + 2] = centerZ;
            samples[i * 4 + 3] = radius;
        }

        removeOverlappingSamples(samples);

        boolean placed = false;
        for (int i = 0; i < size; i++) {
            double radius = samples[i * 4 + 3];
            if (radius < 0.0) continue;

            double centerX = samples[i * 4];
            double centerY = samples[i * 4 + 1];
            double centerZ = samples[i * 4 + 2];
            int fromX = Math.max((int) Math.floor(centerX - radius), minX);
            int fromY = Math.max((int) Math.floor(centerY - radius), minY);
            int fromZ = Math.max((int) Math.floor(centerZ - radius), minZ);
            int toX = Math.max((int) Math.floor(centerX + radius), fromX);
            int toY = Math.max((int) Math.floor(centerY + radius), fromY);
            int toZ = Math.max((int) Math.floor(centerZ + radius), fromZ);

            for (int bx = fromX; bx <= toX; bx++) {
                double dx = ((double) bx + 0.5 - centerX) / radius;
                if (dx * dx >= 1.0) continue;
                for (int by = fromY; by <= toY; by++) {
                    double dy = ((double) by + 0.5 - centerY) / radius;
                    if (dx * dx + dy * dy >= 1.0) continue;
                    for (int bz = fromZ; bz <= toZ; bz++) {
                        double dz = ((double) bz + 0.5 - centerZ) / radius;
                        if (dx * dx + dy * dy + dz * dz >= 1.0 || !inWorldY(view, by)) continue;

                        int index = bx - minX + (by - minY) * horizontalSize
                            + (bz - minZ) * horizontalSize * verticalSize;
                        if (visited.get(index)) continue;
                        visited.set(index);
                        placed |= tryPlace(view, random, bx, by, bz);
                    }
                }
            }
        }
        return placed;
    }

    private boolean tryPlace(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int current = view.getBlock(x, y, z);
        for (Target target : targets) {
            if (target.matches(current) && shouldPlace(view, random, x, y, z)) {
                view.setBlock(x, y, z, target.stateId());
                return true;
            }
        }
        return false;
    }

    private static void removeOverlappingSamples(double[] samples) {
        int sampleCount = samples.length / 4;
        for (int i = 0; i < sampleCount - 1; i++) {
            if (samples[i * 4 + 3] <= 0.0) continue;
            for (int j = i + 1; j < sampleCount; j++) {
                if (samples[j * 4 + 3] <= 0.0) continue;
                double dx = samples[i * 4] - samples[j * 4];
                double dy = samples[i * 4 + 1] - samples[j * 4 + 1];
                double dz = samples[i * 4 + 2] - samples[j * 4 + 2];
                double dr = samples[i * 4 + 3] - samples[j * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0.0) {
                        samples[j * 4 + 3] = -1.0;
                    } else {
                        samples[i * 4 + 3] = -1.0;
                    }
                }
            }
        }
    }

    private static double lerp(float progress, double start, double end) {
        return start + (double) progress * (end - start);
    }

    private static float mthSin(float value) {
        return MTH_SIN[(int) (value * (65536.0f / (float) (Math.PI * 2.0))) & 65535];
    }

    private boolean shouldPlace(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        if (discardChanceOnAirExposure <= 0.0f) return true;
        if (discardChanceOnAirExposure >= 1.0f) return !isAdjacentToAir(view, x, y, z);
        boolean skipAirCheck = random.nextFloat() >= discardChanceOnAirExposure;
        return skipAirCheck || !isAdjacentToAir(view, x, y, z);
    }

    private static boolean isAdjacentToAir(FeatureWorldView view, int x, int y, int z) {
        return isAirBlock(view.getBlock(x + 1, y, z))
            || isAirBlock(view.getBlock(x - 1, y, z))
            || isAirBlock(view.getBlock(x, y + 1, z))
            || isAirBlock(view.getBlock(x, y - 1, z))
            || isAirBlock(view.getBlock(x, y, z + 1))
            || isAirBlock(view.getBlock(x, y, z - 1));
    }

    // vanilla BlockState.isAir() — air(0), cave_air, void_air 모두 공기로 처리
    private static boolean isAirBlock(int stateId) {
        return stateId == 0 || stateId == 12958 || stateId == 12959;
    }

    private static boolean inWorldY(FeatureWorldView view, int y) {
        return y >= view.minY() && y < (view.maxY() + 1);
    }

    public record Target(List<Integer> replaceStateIds, int stateId) {
        public Target(int replaceStateId, int stateId) {
            this(List.of(replaceStateId), stateId);
        }

        boolean matches(int stateId) {
            return replaceStateIds.isEmpty() || replaceStateIds.contains(stateId);
        }
    }
}
