package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.carver.RandomTrace;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.noise.SimplexNoise;

public sealed interface PlacementModifier
        permits PlacementModifier.CountPlacement,
                PlacementModifier.RandomOffsetPlacement,
                PlacementModifier.EnvironmentScanPlacement,
                PlacementModifier.PredicateFilter,
                PlacementModifier.ProviderCountPlacement,
                PlacementModifier.UniformCountPlacement,
                PlacementModifier.WeightedListCountPlacement,
                PlacementModifier.InSquarePlacement,
                PlacementModifier.RarityFilterPlacement,
                PlacementModifier.BiomeFilter,
                PlacementModifier.HeightRangePlacement,
                PlacementModifier.HeightmapPlacement,
                PlacementModifier.SurfaceWaterDepthFilterPlacement,
                PlacementModifier.BlockPredicateFilter,
                PlacementModifier.CompositeBlockPredicateFilter,
                PlacementModifier.WouldSurviveFilter,
                PlacementModifier.NoiseThresholdCountPlacement,
                PlacementModifier.NoiseBasedCountPlacement,
                PlacementModifier.CarvingMaskPlacement,
                PlacementModifier.SurfaceRelativeThresholdFilter {

    // 바닐라 Biome.BIOME_INFO_NOISE = PerlinSimplexNoise(LegacyRandomSource(2345), octaves=[0]) = 단일 SimplexNoise 2D.
    SimplexNoise BIOME_INFO_NOISE = new SimplexNoise(new LegacyRandom(2345L));

    Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos);

    record CountPlacement(int count) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return IntStream.range(0, count).mapToObj(__ -> pos);
        }
    }

    /** vanilla CountPlacement: IntProvider를 한 번 샘플해 그 횟수만큼 같은 위치를 반복(모든 IntProvider 종류 지원). */
    record ProviderCountPlacement(TreeIntProvider count) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            int n = count.sample(random);
            return IntStream.range(0, n).mapToObj(__ -> pos);
        }
    }

    /** vanilla RandomOffsetPlacement: x = pos.x + xz.sample, y = pos.y + y.sample, z = pos.z + xz.sample (이 순서로 RNG 소비). */
    record RandomOffsetPlacement(TreeIntProvider xzSpread, TreeIntProvider ySpread) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            int x = pos.x() + xzSpread.sample(random);
            int y = pos.y() + ySpread.sample(random);
            int z = pos.z() + xzSpread.sample(random);
            return Stream.of(new BlockPos(x, y, z));
        }
    }

    /** vanilla EnvironmentScanPlacement: 허용 조건이 유지되는 동안 maxSteps까지 방향으로 이동하며 목표 조건을 찾는다(RNG 미소비). */
    record EnvironmentScanPlacement(int dx, int dy, int dz, BlockPredicate target, BlockPredicate allowed, int maxSteps)
            implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            int x = pos.x(), y = pos.y(), z = pos.z();
            if (!allowed.test(view, x, y, z)) return Stream.empty();
            for (int i = 0; i < maxSteps; i++) {
                if (target.test(view, x, y, z)) return Stream.of(new BlockPos(x, y, z));
                x += dx; y += dy; z += dz;
                if (y < ChunkColumn.MIN_Y || y > ChunkColumn.MAX_Y) return Stream.empty();   // isOutsideBuildHeight
                if (!allowed.test(view, x, y, z)) break;
            }
            return target.test(view, x, y, z) ? Stream.of(new BlockPos(x, y, z)) : Stream.empty();
        }
    }

    /** vanilla BlockPredicateFilter: 조건을 만족하는 위치만 통과(RNG 미소비). */
    record PredicateFilter(BlockPredicate predicate) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return predicate.test(view, pos.x(), pos.y(), pos.z()) ? Stream.of(pos) : Stream.empty();
        }
    }

    record UniformCountPlacement(int minInclusive, int maxInclusive) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            int count = minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
            return IntStream.range(0, count).mapToObj(__ -> pos);
        }
    }

    record WeightedListCountPlacement(int[] minInclusive, int[] maxInclusive, int[] weights) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            int totalWeight = 0;
            for (int weight : weights) totalWeight += weight;
            int selected = random.nextInt(totalWeight);
            int index = 0;
            while (selected >= weights[index]) {
                selected -= weights[index];
                index++;
            }
            int count = minInclusive[index] == maxInclusive[index]
                ? minInclusive[index]
                : minInclusive[index] + random.nextInt(maxInclusive[index] - minInclusive[index] + 1);
            return IntStream.range(0, count).mapToObj(__ -> pos);
        }
    }

    final class InSquarePlacement implements PlacementModifier {
        static int SEA_INSQ_COUNT = 0;
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            int sqX = random.nextInt(16);
            int sqZ = random.nextInt(16);
            if (Boolean.getBoolean("furnace.seaStreamDebug")) {
                System.out.printf("IN_SQ#%d pos=(%d,%d) sqX=%d sqZ=%d result=(%d,%d)%n",
                    SEA_INSQ_COUNT++, pos.x(), pos.z(), sqX, sqZ, pos.x()+sqX, pos.z()+sqZ);
            }
            return Stream.of(new BlockPos(pos.x() + sqX, pos.y(), pos.z() + sqZ));
        }
    }

    record RarityFilterPlacement(int chance) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return random.nextFloat() < 1.0F / chance ? Stream.of(pos) : Stream.empty();
        }
    }

    record SurfaceWaterDepthFilterPlacement(int maxWaterDepth) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            int oceanFloor = view.getOceanFloorY(pos.x(), pos.z());
            int worldSurface = view.getSurfaceY(pos.x(), pos.z());
            return worldSurface - oceanFloor <= maxWaterDepth ? Stream.of(pos) : Stream.empty();
        }
    }

    final class BiomeFilter implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return Stream.of(pos);
        }
    }

    /** heightmap 타입별로 vanilla 높이맵 의미를 근사한다. */
    record HeightmapPlacement(Type type) implements PlacementModifier {
        enum Type {
            WORLD_SURFACE_WG,
            MOTION_BLOCKING,
            MOTION_BLOCKING_NO_LEAVES,
            OCEAN_FLOOR,
            OCEAN_FLOOR_WG
        }

        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            int y = switch (type) {
                case OCEAN_FLOOR -> view.getOceanFloorY(pos.x(), pos.z()) + 1;
                case OCEAN_FLOOR_WG -> view.getWgOceanFloorHeight(pos.x(), pos.z());
                case WORLD_SURFACE_WG -> view.getWorldSurfaceWgY(pos.x(), pos.z()) + 1;
                case MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES ->
                    view.getSurfaceY(pos.x(), pos.z()) + 1;
            };
            if (y < ChunkColumn.MIN_Y) return Stream.empty();
            return Stream.of(new BlockPos(pos.x(), y, pos.z()));
        }
    }

    /** noise_threshold_count — BIOME_INFO_NOISE(x/200,z/200) < level면 below, 아니면 above회 반복. random 0소비. */
    record NoiseThresholdCountPlacement(double noiseLevel, int belowNoise, int aboveNoise) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            double value = BIOME_INFO_NOISE.getValue(pos.x() / 200.0, pos.z() / 200.0);
            int count = value < noiseLevel ? belowNoise : aboveNoise;
            return IntStream.range(0, count).mapToObj(__ -> pos);
        }
    }

    /** noise_based_count — ceil((BIOME_INFO_NOISE(x/factor,z/factor)+offset)*ratio)회 pos 반복. random 0소비. */
    record NoiseBasedCountPlacement(int noiseToCountRatio, double noiseFactor, double noiseOffset)
            implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            double value = BIOME_INFO_NOISE.getValue(pos.x() / noiseFactor, pos.z() / noiseFactor);
            int count = (int) Math.ceil((value + noiseOffset) * noiseToCountRatio);
            return IntStream.range(0, count).mapToObj(__ -> pos);
        }
    }

    /** block_predicate_filter — pos의 블록이 allowed 집합에 들면 통과(matching_blocks). random 0소비. */
    record BlockPredicateFilter(Set<Integer> allowed) implements PlacementModifier {
        public BlockPredicateFilter {
            allowed = Set.copyOf(allowed);
        }
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return allowed.contains(view.getBlock(pos.x(), pos.y(), pos.z())) ? Stream.of(pos) : Stream.empty();
        }
    }

    /**
     * block_predicate_filter 일반형 — matching_blocks(offset)/not/all_of/any_of 조합 predicate 트리 평가.
     * patch_grass_jungle의 all_of(air, not(podzol below)) 등 복합 predicate용. random 0소비.
     */
    record CompositeBlockPredicateFilter(BlockPred pred) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return pred.test(view, pos) ? Stream.of(pos) : Stream.empty();
        }
    }

    sealed interface BlockPred permits MatchingBlocksPred, NotPred, AllOfPred, AnyOfPred {
        boolean test(FeatureWorldView view, BlockPos pos);
    }

    record MatchingBlocksPred(Set<Integer> states, int ox, int oy, int oz) implements BlockPred {
        @Override
        public boolean test(FeatureWorldView view, BlockPos pos) {
            return states.contains(view.getBlock(pos.x() + ox, pos.y() + oy, pos.z() + oz));
        }
    }

    record NotPred(BlockPred inner) implements BlockPred {
        @Override
        public boolean test(FeatureWorldView view, BlockPos pos) {
            return !inner.test(view, pos);
        }
    }

    record AllOfPred(java.util.List<BlockPred> preds) implements BlockPred {
        @Override
        public boolean test(FeatureWorldView view, BlockPos pos) {
            for (BlockPred p : preds) if (!p.test(view, pos)) return false;
            return true;
        }
    }

    record AnyOfPred(java.util.List<BlockPred> preds) implements BlockPred {
        @Override
        public boolean test(FeatureWorldView view, BlockPos pos) {
            for (BlockPred p : preds) if (p.test(view, pos)) return true;
            return false;
        }
    }

    /** would_survive(sapling) 근사: 현재 칸은 air, 아래는 허용 지면이어야 통과. random 0소비. */
    record WouldSurviveFilter(Set<Integer> allowedGround) implements PlacementModifier {
        public WouldSurviveFilter {
            allowedGround = Set.copyOf(allowedGround);
        }
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            int below = view.getBlock(pos.x(), pos.y() - 1, pos.z());
            boolean pass = allowedGround.contains(below);
            RandomTrace.log("would_survive x=" + pos.x() + " y=" + pos.y() + " z=" + pos.z()
                + " below=" + below + " pass=" + pass);
            return pass ? Stream.of(pos) : Stream.empty();
        }
    }

    /** carving_mask — 오버월드에 liquid carver 없음 → 항상 empty. */
    record CarvingMaskPlacement(String step) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return Stream.empty();
        }
    }

    // 바닐라 SurfaceRelativeThresholdFilter — 위치 Y가 heightmap 기준 상대 범위 내인지 필터.
    record SurfaceRelativeThresholdFilter(HeightmapPlacement.Type heightmap, int minRelative, int maxRelative) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            int surfaceY = switch (heightmap) {
                case OCEAN_FLOOR_WG -> view.getWgOceanFloorHeight(pos.x(), pos.z());
                case OCEAN_FLOOR -> view.getOceanFloorY(pos.x(), pos.z());
                case WORLD_SURFACE_WG -> view.getWorldSurfaceWgY(pos.x(), pos.z());
                case MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES -> view.getSurfaceY(pos.x(), pos.z());
            };
            int relativeY = pos.y() - surfaceY;
            return relativeY >= minRelative && relativeY <= maxRelative ? Stream.of(pos) : Stream.empty();
        }
    }

    record HeightRangePlacement(HeightProvider height) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return Stream.of(new BlockPos(pos.x(), height.sample(random), pos.z()));
        }
    }

    sealed interface HeightProvider
            permits UniformHeight, BiasedToBottomHeight, VeryBiasedToBottomHeight, TrapezoidHeight, ConstantHeight {
        int sample(WorldgenRandom random);
    }

    record UniformHeight(int minInclusive, int maxInclusive) implements HeightProvider {
        @Override
        public int sample(WorldgenRandom random) {
            return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
        }
    }

    record BiasedToBottomHeight(int minInclusive, int maxInclusive, int inner) implements HeightProvider {
        @Override
        public int sample(WorldgenRandom random) {
            int bound = maxInclusive - minInclusive - inner + 1;
            if (bound <= 0) return minInclusive;
            int sampled = random.nextInt(bound);
            return minInclusive + random.nextInt(sampled + inner);
        }
    }

    record VeryBiasedToBottomHeight(int minInclusive, int maxInclusive, int inner) implements HeightProvider {
        @Override
        public int sample(WorldgenRandom random) {
            int bound = maxInclusive - minInclusive - inner + 1;
            if (bound <= 0) return minInclusive;
            int sampledMax = minInclusive + inner + random.nextInt(bound);
            int sampledMid = minInclusive + random.nextInt(sampledMax - minInclusive);
            return minInclusive + random.nextInt(sampledMid - minInclusive + inner);
        }
    }

    record TrapezoidHeight(int minInclusive, int maxInclusive, int plateau) implements HeightProvider {
        @Override
        public int sample(WorldgenRandom random) {
            int span = maxInclusive - minInclusive;
            if (plateau >= span) return minInclusive + random.nextInt(span + 1);
            int side = (span - plateau) / 2;
            int upper = span - side;
            return minInclusive + random.nextInt(upper + 1) + random.nextInt(side + 1);
        }
    }

    record ConstantHeight(int y) implements HeightProvider {
        @Override
        public int sample(WorldgenRandom random) {
            return y;
        }
    }

    static int absoluteYOrDefault(com.google.gson.JsonElement el, int fallback) {
        if (el == null || el.isJsonNull()) return fallback;
        int raw;
        if (el.isJsonPrimitive()) raw = el.getAsInt();
        else {
            com.google.gson.JsonObject obj = el.getAsJsonObject();
            if (obj.has("absolute")) raw = obj.get("absolute").getAsInt();
            else if (obj.has("above_bottom")) raw = ChunkColumn.MIN_Y + obj.get("above_bottom").getAsInt();
            else if (obj.has("below_top")) {
                int top = ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16 - 1;
                raw = top - obj.get("below_top").getAsInt();
            } else raw = fallback;
        }
        int minY = ChunkColumn.MIN_Y;
        int maxY = ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16 - 1;
        return Math.max(minY, Math.min(maxY, raw));
    }

    static int worldgenYOrDefault(com.google.gson.JsonElement el, int fallback) {
        if (el == null || el.isJsonNull()) return fallback;
        int raw;
        if (el.isJsonPrimitive()) raw = el.getAsInt();
        else {
            com.google.gson.JsonObject obj = el.getAsJsonObject();
            if (obj.has("absolute")) raw = obj.get("absolute").getAsInt();
            else if (obj.has("above_bottom")) raw = ChunkColumn.MIN_Y + obj.get("above_bottom").getAsInt();
            else if (obj.has("below_top")) {
                int top = ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16 - 1;
                raw = top - obj.get("below_top").getAsInt();
            } else raw = fallback;
        }
        return raw;
    }
}
