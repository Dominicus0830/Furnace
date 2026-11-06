// 드립스톤 계열 feature 3종 — 바닐라 DripstoneClusterFeature / LargeDripstoneFeature / PointedDripstoneFeature + DripstoneUtils + Column 포팅.
// 블록 분류는 state id → boolean 표로 미리 만들어 두고(박싱·해시 조회 없음), 열 탐색은 정수 두 개로 표현해 할당을 피한다.
package org.domi.furnace.server.world.gen.feature;

import java.util.function.IntPredicate;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.Mth;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class DripstoneFeatures {

    private DripstoneFeatures() {}

    static final int NONE = Integer.MIN_VALUE;
    // pointed_dripstone thickness 서수: TIP_MERGE, TIP, FRUSTUM, MIDDLE, BASE
    private static final int TIP_MERGE = 0, TIP = 1, FRUSTUM = 2, MIDDLE = 3, BASE = 4;
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};   // NORTH, EAST, SOUTH, WEST

    /** 블록 분류 표 + pointed_dripstone state 표 (월드젠 데이터 로드마다 한 번 생성). */
    public static final class Support {
        final BlockAttrs attrs = BlockAttrs.get();
        final FluidKinds fluids;
        final boolean[] replaceable;     // #dripstone_replaceable_blocks
        final boolean[] baseStone;       // #base_stone_overworld
        final boolean[] waterBlock;
        final boolean[] lavaBlock;
        final boolean[] dripstoneBlock;
        final boolean[] pointed;
        final int dripstoneBlockId;
        final int[][][] pointedState;    // [up?1:0][thickness][waterlogged?1:0]

        public Support(BlockRegistry registry, FluidKinds fluids) {
            this.fluids = fluids;
            int n = registry.totalStateCount();
            this.replaceable = table(registry, n, BlockTags.get().blocksOf("minecraft:dripstone_replaceable_blocks"));
            this.baseStone = table(registry, n, BlockTags.get().blocksOf("minecraft:base_stone_overworld"));
            this.waterBlock = table(registry, n, java.util.List.of("minecraft:water"));
            this.lavaBlock = table(registry, n, java.util.List.of("minecraft:lava"));
            this.dripstoneBlock = table(registry, n, java.util.List.of("minecraft:dripstone_block"));
            this.pointed = table(registry, n, java.util.List.of("minecraft:pointed_dripstone"));
            this.dripstoneBlockId = registry.defaultStateId("minecraft:dripstone_block");
            this.waterDefaultId = registry.defaultStateId("minecraft:water");
            String[] names = {"tip_merge", "tip", "frustum", "middle", "base"};
            this.pointedState = new int[2][5][2];
            for (int up = 0; up < 2; up++) {
                for (int t = 0; t < 5; t++) {
                    for (int w = 0; w < 2; w++) {
                        pointedState[up][t][w] = registry.stateIdOrAir("minecraft:pointed_dripstone", java.util.Map.of(
                            "thickness", names[t], "vertical_direction", up == 1 ? "up" : "down",
                            "waterlogged", w == 1 ? "true" : "false"));
                    }
                }
            }
        }

        private static boolean[] table(BlockRegistry registry, int n, Iterable<String> blocks) {
            boolean[] t = new boolean[n];
            for (String b : blocks) for (int s : registry.stateIdsForBlock(b)) t[s] = true;
            return t;
        }

        final int waterDefaultId;

        int waterBlockDefault() { return waterDefaultId; }

        boolean isAir(int s) { return attrs.isAir(s); }
        boolean isEmptyOrWater(int s) { return isAir(s) || waterBlock[s]; }
        boolean isNeitherEmptyNorWater(int s) { return !isAir(s) && !waterBlock[s]; }
        boolean isEmptyOrWaterOrLava(int s) { return isAir(s) || waterBlock[s] || lavaBlock[s]; }
        boolean isDripstoneBase(int s) { return dripstoneBlock[s] || replaceable[s]; }
        boolean isDripstoneBaseOrLava(int s) { return isDripstoneBase(s) || lavaBlock[s]; }
        boolean isWaterFluid(int s) { return fluids.of(s).isWater(); }

        boolean placeDripstoneBlockIfPossible(FeatureWorldView v, int x, int y, int z) {
            if (replaceable[v.getBlock(x, y, z)]) {
                v.setBlock(x, y, z, dripstoneBlockId);
                return true;
            }
            return false;
        }

        /** DripstoneUtils.growPointedDripstone */
        void growPointedDripstone(FeatureWorldView v, int x, int y, int z, boolean up, int height, boolean mergedTip) {
            if (!isDripstoneBase(v.getBlock(x, up ? y - 1 : y + 1, z))) return;
            int cy = y;
            int step = up ? 1 : -1;
            int u = up ? 1 : 0;
            if (height >= 3) {
                setPointed(v, x, cy, z, u, BASE);
                cy += step;
                for (int i = 0; i < height - 3; i++) {
                    setPointed(v, x, cy, z, u, MIDDLE);
                    cy += step;
                }
            }
            if (height >= 2) {
                setPointed(v, x, cy, z, u, FRUSTUM);
                cy += step;
            }
            if (height >= 1) {
                setPointed(v, x, cy, z, u, mergedTip ? TIP_MERGE : TIP);
            }
        }

        private void setPointed(FeatureWorldView v, int x, int y, int z, int up, int thickness) {
            int w = isWaterFluid(v.getBlock(x, y, z)) ? 1 : 0;       // level.isWaterAt(pos) — 설치 직전 상태 기준
            v.setBlock(x, y, z, pointedState[up][thickness][w]);
        }

        /** DripstoneUtils.isCircleMostlyEmbeddedInStone */
        boolean isCircleMostlyEmbeddedInStone(FeatureWorldView v, int x, int y, int z, int radius) {
            if (isEmptyOrWaterOrLava(v.getBlock(x, y, z))) return false;
            float step = 6.0F / (float) radius;
            for (float a = 0.0F; a < (float) (Math.PI * 2); a += step) {
                int dx = (int) (Mth.cos(a) * (float) radius);
                int dz = (int) (Mth.sin(a) * (float) radius);
                if (isEmptyOrWaterOrLava(v.getBlock(x + dx, y, z + dz))) return false;
            }
            return true;
        }

        /** DripstoneUtils.getDripstoneHeight */
        static double getDripstoneHeight(double radius, double maxRadius, double scale, double minRadius) {
            if (radius < minRadius) radius = minRadius;
            double d5 = radius / maxRadius * 0.384;
            double d6 = 0.75 * Math.pow(d5, 1.3333333333333333);
            double d7 = Math.pow(d5, 0.6666666666666666);
            double d8 = 0.3333333333333333 * Math.log(d5);
            double d9 = scale * (d6 - d7 - d8);
            d9 = Math.max(d9, 0.0);
            return d9 / 0.384 * maxRadius;
        }

        /** Column.scan 한 방향: 시작 y에서 inside가 계속되는 동안 이동, 끝 칸이 edge면 그 y, 아니면 NONE. */
        static int scanDirection(FeatureWorldView v, int x, int startY, int z, int range, IntPredicate inside, IntPredicate edge, boolean up) {
            int cy = startY;
            for (int i = 1; i < range && inside.test(v.getBlock(x, cy, z)); i++) cy += up ? 1 : -1;
            return edge.test(v.getBlock(x, cy, z)) ? cy : NONE;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PointedDripstoneFeature
    // ---------------------------------------------------------------------------------------------
    public static final class Pointed implements ConfiguredFeature {
        private final Support s;
        private final float chanceOfTaller, chanceOfDirectionalSpread, chanceOfSpreadRadius2, chanceOfSpreadRadius3;

        public Pointed(Support s, float taller, float directional, float radius2, float radius3) {
            this.s = s;
            this.chanceOfTaller = taller;
            this.chanceOfDirectionalSpread = directional;
            this.chanceOfSpreadRadius2 = radius2;
            this.chanceOfSpreadRadius3 = radius3;
        }

        @Override
        public boolean place(FeatureWorldView v, WorldgenRandom r, int x, int y, int z) {
            boolean canGrowDown = s.isDripstoneBase(v.getBlock(x, y + 1, z));
            boolean canGrowUp = s.isDripstoneBase(v.getBlock(x, y - 1, z));
            boolean up;                                  // 끝(tip) 방향
            if (canGrowDown && canGrowUp) up = !r.nextBoolean();         // nextBoolean ? DOWN : UP
            else if (canGrowDown) up = false;
            else if (canGrowUp) up = true;
            else return false;

            int bx = x, by = up ? y - 1 : y + 1, bz = z;       // pos.relative(dir.getOpposite())
            createPatch(v, r, bx, by, bz);
            int ny = up ? y + 1 : y - 1;
            int height = r.nextFloat() < chanceOfTaller && s.isEmptyOrWater(v.getBlock(x, ny, z)) ? 2 : 1;
            s.growPointedDripstone(v, x, y, z, up, height, false);
            return true;
        }

        private void createPatch(FeatureWorldView v, WorldgenRandom r, int x, int y, int z) {
            s.placeDripstoneBlockIfPossible(v, x, y, z);
            for (int[] d : HORIZONTAL) {
                if (r.nextFloat() > chanceOfDirectionalSpread) continue;
                int x1 = x + d[0], z1 = z + d[1];
                s.placeDripstoneBlockIfPossible(v, x1, y, z1);
                if (r.nextFloat() > chanceOfSpreadRadius2) continue;
                int[] d2 = randomDirection(r);
                int x2 = x1 + d2[0], y2 = y + d2[1], z2 = z1 + d2[2];
                s.placeDripstoneBlockIfPossible(v, x2, y2, z2);
                if (r.nextFloat() > chanceOfSpreadRadius3) continue;
                int[] d3 = randomDirection(r);
                s.placeDripstoneBlockIfPossible(v, x2 + d3[0], y2 + d3[1], z2 + d3[2]);
            }
        }
    }

    // Direction.values(): DOWN, UP, NORTH, SOUTH, WEST, EAST
    private static final int[][] DIRECTIONS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private static int[] randomDirection(WorldgenRandom r) {
        return DIRECTIONS[r.nextInt(DIRECTIONS.length)];
    }

    // ---------------------------------------------------------------------------------------------
    // DripstoneClusterFeature
    // ---------------------------------------------------------------------------------------------
    public static final class Cluster implements ConfiguredFeature {
        private final Support s;
        private final int floorToCeilingSearchRange;
        private final TreeIntProvider height, radius, layerThickness;
        private final int maxHeightDiff, heightDeviation;
        private final FeatureFloatProvider density, wetness;
        private final float chanceAtMaxDistance;
        private final int maxDistFromEdge, maxDistFromCenter;

        public Cluster(Support s, int search, TreeIntProvider height, TreeIntProvider radius, int maxHeightDiff, int heightDeviation,
                       TreeIntProvider layerThickness, FeatureFloatProvider density, FeatureFloatProvider wetness,
                       float chanceAtMaxDistance, int maxDistFromEdge, int maxDistFromCenter) {
            this.s = s;
            this.floorToCeilingSearchRange = search;
            this.height = height;
            this.radius = radius;
            this.maxHeightDiff = maxHeightDiff;
            this.heightDeviation = heightDeviation;
            this.layerThickness = layerThickness;
            this.density = density;
            this.wetness = wetness;
            this.chanceAtMaxDistance = chanceAtMaxDistance;
            this.maxDistFromEdge = maxDistFromEdge;
            this.maxDistFromCenter = maxDistFromCenter;
        }

        @Override
        public boolean place(FeatureWorldView v, WorldgenRandom r, int x, int y, int z) {
            if (!s.isEmptyOrWater(v.getBlock(x, y, z))) return false;
            int h = height.sample(r);
            float wet = wetness.sample(r);
            float dens = density.sample(r);
            int rx = radius.sample(r);
            int rz = radius.sample(r);
            for (int dx = -rx; dx <= rx; dx++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    double chance = chanceOfStalagmiteOrStalactite(rx, rz, dx, dz);
                    placeColumn(v, r, x + dx, y, z + dz, dx, dz, wet, chance, h, dens);
                }
            }
            return true;
        }

        private double chanceOfStalagmiteOrStalactite(int rx, int rz, int dx, int dz) {
            int a = rx - Math.abs(dx);
            int b = rz - Math.abs(dz);
            int m = Math.min(a, b);
            return (double) FeatureFloatProvider.clampedMap((float) m, 0.0F, (float) maxDistFromEdge, chanceAtMaxDistance, 1.0F);
        }

        private void placeColumn(FeatureWorldView v, WorldgenRandom r, int x, int y, int z, int dx, int dz,
                                 float wet, double chance, int h, float dens) {
            if (!s.isEmptyOrWater(v.getBlock(x, y, z))) return;            // Column.scan: 시작 칸이 inside가 아니면 빈 결과
            int floor = DripstoneFeatures.Support.scanDirection(v, x, y, z, floorToCeilingSearchRange, s::isEmptyOrWater, s::isNeitherEmptyNorWater, false);
            int ceiling = DripstoneFeatures.Support.scanDirection(v, x, y, z, floorToCeilingSearchRange, s::isEmptyOrWater, s::isNeitherEmptyNorWater, true);
            if (!(ceiling != NONE || floor != NONE)) return;
            // Column.of(floor, ceiling) 이 Range인 경우만 높이가 정의됨
            boolean isRange = floor != NONE && ceiling != NONE;

            boolean makeWater = r.nextFloat() < wet;
            int newFloor;
            boolean newIsRange = isRange;
            if (makeWater && floor != NONE && canBeAdjacentToWater(v, x, floor, z)) {
                newFloor = floor - 1;
                v.setBlock(x, floor, z, s.waterBlockDefault());
            } else {
                newFloor = floor;
            }

            boolean placeStalactite = r.nextDouble() < chance;
            int stalactiteH;
            if (ceiling != NONE && placeStalactite && !isLava(v, x, ceiling, z)) {
                int thick = layerThickness.sample(r);
                replaceWithDripstoneBlocks(v, x, ceiling, z, thick, true);
                int maxH = newFloor != NONE ? Math.min(h, ceiling - newFloor) : h;
                stalactiteH = getDripstoneHeight(r, dx, dz, dens, maxH);
            } else {
                stalactiteH = 0;
            }

            boolean placeStalagmite = r.nextDouble() < chance;
            int stalagmiteH;
            if (newFloor != NONE && placeStalagmite && !isLava(v, x, newFloor, z)) {
                int thick = layerThickness.sample(r);
                replaceWithDripstoneBlocks(v, x, newFloor, z, thick, false);
                if (ceiling != NONE) {
                    stalagmiteH = Math.max(0, stalactiteH + randomBetweenInclusive(r, -maxHeightDiff, maxHeightDiff));
                } else {
                    stalagmiteH = getDripstoneHeight(r, dx, dz, dens, h);
                }
            } else {
                stalagmiteH = 0;
            }

            int finalStalactite, finalStalagmite;
            if (ceiling != NONE && newFloor != NONE && ceiling - stalactiteH <= newFloor + stalagmiteH) {
                int lowestStalactiteBottom = Math.max(ceiling - stalactiteH, newFloor + 1);
                int highestStalagmiteTop = Math.min(newFloor + stalagmiteH, ceiling - 1);
                int actualBottom = randomBetweenInclusive(r, lowestStalactiteBottom, highestStalagmiteTop + 1);
                int actualTop = actualBottom - 1;
                finalStalactite = ceiling - actualBottom;
                finalStalagmite = actualTop - newFloor;
            } else {
                finalStalactite = stalactiteH;
                finalStalagmite = stalagmiteH;
            }

            int columnHeight = (newIsRange && newFloor != NONE && ceiling != NONE) ? ceiling - newFloor - 1 : NONE;
            boolean merged = r.nextBoolean() && finalStalactite > 0 && finalStalagmite > 0 && columnHeight != NONE
                && finalStalactite + finalStalagmite == columnHeight;

            if (ceiling != NONE) s.growPointedDripstone(v, x, ceiling - 1, z, false, finalStalactite, merged);
            if (newFloor != NONE) s.growPointedDripstone(v, x, newFloor + 1, z, true, finalStalagmite, merged);
        }

        private boolean isLava(FeatureWorldView v, int x, int y, int z) {
            return s.lavaBlock[v.getBlock(x, y, z)];
        }

        private void replaceWithDripstoneBlocks(FeatureWorldView v, int x, int y, int z, int thickness, boolean up) {
            int cy = y;
            for (int i = 0; i < thickness; i++) {
                if (!s.placeDripstoneBlockIfPossible(v, x, cy, z)) return;
                cy += up ? 1 : -1;
            }
        }

        private int getDripstoneHeight(WorldgenRandom r, int dx, int dz, float density, int maxHeight) {
            if (r.nextFloat() > density) return 0;
            int dist = Math.abs(dx) + Math.abs(dz);
            float mean = (float) FeatureFloatProvider.clampedMap((double) dist, 0.0, (double) maxDistFromCenter, (double) maxHeight / 2.0, 0.0);
            return (int) FeatureFloatProvider.ClampedNormal.sampleStatic(r, mean, (float) heightDeviation, 0.0F, (float) maxHeight);
        }

        private boolean canBeAdjacentToWater(FeatureWorldView v, int x, int y, int z) {
            int st = v.getBlock(x, y, z);
            if (s.waterBlock[st] || s.dripstoneBlock[st] || s.pointed[st]) return false;
            if (s.isWaterFluid(v.getBlock(x, y + 1, z))) return false;
            for (int[] d : HORIZONTAL) {
                if (!isStoneOrWater(v, x + d[0], y, z + d[1])) return false;
            }
            return isStoneOrWater(v, x, y - 1, z);
        }

        private boolean isStoneOrWater(FeatureWorldView v, int x, int y, int z) {
            int st = v.getBlock(x, y, z);
            return s.baseStone[st] || s.isWaterFluid(st);
        }
    }

    static int randomBetweenInclusive(WorldgenRandom r, int min, int max) {
        return r.nextInt(max - min + 1) + min;
    }

    // ---------------------------------------------------------------------------------------------
    // LargeDripstoneFeature
    // ---------------------------------------------------------------------------------------------
    public static final class Large implements ConfiguredFeature {
        private final Support s;
        private final int floorToCeilingSearchRange;
        private final int radiusMin, radiusMax;
        private final TreeIntProvider columnRadius;
        private final FeatureFloatProvider heightScale, stalactiteBluntness, stalagmiteBluntness, windSpeed;
        private final float maxColumnRadiusToCaveHeightRatio;
        private final int minRadiusForWind;
        private final float minBluntnessForWind;

        public Large(Support s, int search, TreeIntProvider.Uniform columnRadius, FeatureFloatProvider heightScale, float ratio,
                     FeatureFloatProvider stalactiteBluntness, FeatureFloatProvider stalagmiteBluntness, FeatureFloatProvider windSpeed,
                     int minRadiusForWind, float minBluntnessForWind) {
            this.s = s;
            this.floorToCeilingSearchRange = search;
            this.columnRadius = columnRadius;
            this.radiusMin = columnRadius.minInclusive();
            this.radiusMax = columnRadius.maxInclusive();
            this.heightScale = heightScale;
            this.maxColumnRadiusToCaveHeightRatio = ratio;
            this.stalactiteBluntness = stalactiteBluntness;
            this.stalagmiteBluntness = stalagmiteBluntness;
            this.windSpeed = windSpeed;
            this.minRadiusForWind = minRadiusForWind;
            this.minBluntnessForWind = minBluntnessForWind;
        }

        @Override
        public boolean place(FeatureWorldView v, WorldgenRandom r, int x, int y, int z) {
            if (!s.isEmptyOrWater(v.getBlock(x, y, z))) return false;
            int floor = Support.scanDirection(v, x, y, z, floorToCeilingSearchRange, s::isEmptyOrWater, s::isDripstoneBaseOrLava, false);
            int ceiling = Support.scanDirection(v, x, y, z, floorToCeilingSearchRange, s::isEmptyOrWater, s::isDripstoneBaseOrLava, true);
            if (floor == NONE || ceiling == NONE) return false;            // Range가 아니면 실패
            int caveHeight = ceiling - floor - 1;
            if (caveHeight < 4) return false;

            int maxRadiusByHeight = (int) ((float) caveHeight * maxColumnRadiusToCaveHeightRatio);
            int clampedMax = Math.min(Math.max(maxRadiusByHeight, radiusMin), radiusMax);
            int radius = randomBetweenInclusive(r, radiusMin, clampedMax);
            LargeDripstone stalactite = make(x, ceiling - 1, z, false, r, radius, stalactiteBluntness);
            LargeDripstone stalagmite = make(x, floor + 1, z, true, r, radius, stalagmiteBluntness);
            Wind wind;
            if (stalactite.suitableForWind() && stalagmite.suitableForWind()) {
                wind = new Wind(y, r, windSpeed);
            } else {
                wind = Wind.NO_WIND;
            }
            boolean a = stalactite.moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(v, wind);
            boolean b = stalagmite.moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(v, wind);
            if (a) stalactite.placeBlocks(v, r, wind);
            if (b) stalagmite.placeBlocks(v, r, wind);
            return true;
        }

        private LargeDripstone make(int x, int y, int z, boolean stalagmite, WorldgenRandom r, int radius, FeatureFloatProvider bluntness) {
            double blunt = (double) bluntness.sample(r);
            double scale = (double) heightScale.sample(r);
            return new LargeDripstone(x, y, z, stalagmite, radius, blunt, scale);
        }

        private final class LargeDripstone {
            int x, y, z;
            final boolean pointingUp;
            int radius;
            final double bluntness, scale;

            LargeDripstone(int x, int y, int z, boolean pointingUp, int radius, double bluntness, double scale) {
                this.x = x; this.y = y; this.z = z;
                this.pointingUp = pointingUp;
                this.radius = radius;
                this.bluntness = bluntness;
                this.scale = scale;
            }

            private int heightAt(float r) {
                return (int) Support.getDripstoneHeight((double) r, (double) radius, scale, bluntness);
            }

            boolean suitableForWind() {
                return radius >= minRadiusForWind && bluntness >= (double) minBluntnessForWind;
            }

            boolean moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(FeatureWorldView v, Wind wind) {
                while (radius > 1) {
                    int cy = y;
                    int limit = Math.min(10, heightAt(0.0F));
                    for (int i = 0; i < limit; i++) {
                        if (s.lavaBlock[v.getBlock(x, cy, z)]) return false;
                        int wx = wind.offsetX(x, cy), wz = wind.offsetZ(z, cy);
                        if (s.isCircleMostlyEmbeddedInStone(v, wx, cy, wz, radius)) {
                            y = cy;
                            return true;
                        }
                        cy += pointingUp ? -1 : 1;
                    }
                    radius /= 2;
                }
                return false;
            }

            void placeBlocks(FeatureWorldView v, WorldgenRandom r, Wind wind) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        float dist = (float) Math.sqrt((double) (float) (dx * dx + dz * dz));
                        if (dist > (float) radius) continue;
                        int h = heightAt(dist);
                        if (h <= 0) continue;
                        if ((double) r.nextFloat() < 0.2) {
                            h = (int) ((float) h * (r.nextFloat() * (1.0F - 0.8F) + 0.8F));
                        }
                        int cx = x + dx, cy = y, cz = z + dz;
                        boolean placedAny = false;
                        int maxY = pointingUp ? v.getWorldSurfaceWgY(cx, cz) + 1 : Integer.MAX_VALUE;
                        for (int i = 0; i < h && cy < maxY; i++) {
                            int px = wind.offsetX(cx, cy), pz = wind.offsetZ(cz, cy);
                            int st = v.getBlock(px, cy, pz);
                            if (s.isEmptyOrWaterOrLava(st)) {
                                placedAny = true;
                                v.setBlock(px, cy, pz, s.dripstoneBlockId);
                            } else if (placedAny && s.baseStone[st]) {
                                break;
                            }
                            cy += pointingUp ? 1 : -1;
                        }
                    }
                }
            }
        }
    }

    /** LargeDripstoneFeature.WindOffsetter */
    private static final class Wind {
        static final Wind NO_WIND = new Wind();
        private final int originY;
        private final boolean has;
        private final double wx, wz;

        private Wind() {
            this.originY = 0;
            this.has = false;
            this.wx = 0;
            this.wz = 0;
        }

        Wind(int originY, WorldgenRandom r, FeatureFloatProvider speedProvider) {
            this.originY = originY;
            float speed = speedProvider.sample(r);
            float angle = r.nextFloat() * ((float) Math.PI - 0.0F) + 0.0F;     // Mth.randomBetween(random, 0, PI)
            this.has = true;
            this.wx = (double) (Mth.cos(angle) * speed);
            this.wz = (double) (Mth.sin(angle) * speed);
        }

        int offsetX(int x, int y) {
            if (!has) return x;
            double dy = (double) (originY - y);
            return x + floor(wx * dy);
        }

        int offsetZ(int z, int y) {
            if (!has) return z;
            double dy = (double) (originY - y);
            return z + floor(wz * dy);
        }

        private static int floor(double d) {
            int i = (int) d;
            return d < (double) i ? i - 1 : i;
        }
    }
}
