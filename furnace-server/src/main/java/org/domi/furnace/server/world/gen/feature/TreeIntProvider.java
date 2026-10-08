package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public sealed interface TreeIntProvider permits TreeIntProvider.Constant, TreeIntProvider.Uniform, TreeIntProvider.WeightedList,
        TreeIntProvider.BiasedToBottom, TreeIntProvider.Clamped, TreeIntProvider.ClampedNormal {

    int sample(WorldgenRandom random);

    record Constant(int value) implements TreeIntProvider {
        @Override
        public int sample(WorldgenRandom random) {
            return value;
        }
    }

    record Uniform(int minInclusive, int maxInclusive) implements TreeIntProvider {
        @Override
        public int sample(WorldgenRandom random) {
            return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
        }
    }

    // vanilla WeightedListInt + net.minecraft.util.random.WeightedRandom 포팅 (cherry_trunk_placer의
    // branch_count가 minecraft:weighted_list 사용). random.nextInt(totalWeight) 1회 뽑고 누적 가중치로
    // 항목을 고른 뒤 그 항목의 provider를 샘플(entry가 상수면 추가 RNG 소비 없음).
    record WeightedList(List<Entry> entries) implements TreeIntProvider {
        public record Entry(int weight, TreeIntProvider data) {}

        @Override
        public int sample(WorldgenRandom random) {
            int totalWeight = 0;
            for (Entry e : entries) totalWeight += e.weight();
            int idx = random.nextInt(totalWeight);
            for (Entry e : entries) {
                idx -= e.weight();
                if (idx < 0) return e.data().sample(random);
            }
            throw new IllegalStateException("weighted_list exhausted without a match");
        }
    }

    /** vanilla BiasedToBottomInt: min + nextInt(nextInt(max - min + 1) + 1). */
    record BiasedToBottom(int minInclusive, int maxInclusive) implements TreeIntProvider {
        @Override
        public int sample(WorldgenRandom random) {
            return minInclusive + random.nextInt(random.nextInt(maxInclusive - minInclusive + 1) + 1);
        }
    }

    /** vanilla ClampedInt: Mth.clamp(source.sample(random), min, max). */
    record Clamped(TreeIntProvider source, int minInclusive, int maxInclusive) implements TreeIntProvider {
        @Override
        public int sample(WorldgenRandom random) {
            return Math.max(minInclusive, Math.min(maxInclusive, source.sample(random)));
        }
    }

    /** vanilla ClampedNormalInt: (int) Mth.clamp(mean + (float) nextGaussian() * deviation, min, max) — float 연산 순서까지 동일. */
    record ClampedNormal(float mean, float deviation, int minInclusive, int maxInclusive) implements TreeIntProvider {
        @Override
        public int sample(WorldgenRandom random) {
            float normal = mean + (float) random.nextGaussian() * deviation;
            float clamped = Math.max((float) minInclusive, Math.min((float) maxInclusive, normal));
            return (int) clamped;
        }
    }
}
