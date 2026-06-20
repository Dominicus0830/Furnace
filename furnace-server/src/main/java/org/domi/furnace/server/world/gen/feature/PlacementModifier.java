package org.domi.furnace.server.world.gen.feature;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public sealed interface PlacementModifier
        permits PlacementModifier.CountPlacement,
                PlacementModifier.InSquarePlacement,
                PlacementModifier.RarityFilterPlacement,
                PlacementModifier.BiomeFilter,
                PlacementModifier.HeightRangePlacement {

    Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos);

    record CountPlacement(int count) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return IntStream.range(0, count).mapToObj(__ -> pos);
        }
    }

    final class InSquarePlacement implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return Stream.of(new BlockPos(pos.x() + random.nextInt(16), pos.y(), pos.z() + random.nextInt(16)));
        }
    }

    record RarityFilterPlacement(int chance) implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return random.nextInt(chance) == 0 ? Stream.of(pos) : Stream.empty();
        }
    }

    final class BiomeFilter implements PlacementModifier {
        @Override
        public Stream<BlockPos> filter(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
            return Stream.of(pos);
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
            return minInclusive + random.nextInt(random.nextInt(Math.max(bound, 1)) + inner + 1);
        }
    }

    record VeryBiasedToBottomHeight(int minInclusive, int maxInclusive, int inner) implements HeightProvider {
        @Override
        public int sample(WorldgenRandom random) {
            int bound = maxInclusive - minInclusive - inner + 1;
            return minInclusive + random.nextInt(random.nextInt(random.nextInt(Math.max(bound, 1)) + 1) + inner + 1);
        }
    }

    record TrapezoidHeight(int minInclusive, int maxInclusive, int plateau) implements HeightProvider {
        @Override
        public int sample(WorldgenRandom random) {
            int span = maxInclusive - minInclusive + 1;
            if (plateau >= span) return minInclusive + random.nextInt(span);
            int side = (span - plateau) / 2;
            return minInclusive + random.nextInt(side + plateau) + random.nextInt(side + 1);
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
        if (el.isJsonPrimitive()) return el.getAsInt();
        com.google.gson.JsonObject obj = el.getAsJsonObject();
        if (obj.has("absolute")) return obj.get("absolute").getAsInt();
        if (obj.has("above_bottom")) return ChunkColumn.MIN_Y + obj.get("above_bottom").getAsInt();
        if (obj.has("below_top")) {
            int top = ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16;
            return top - obj.get("below_top").getAsInt();
        }
        return fallback;
    }
}
