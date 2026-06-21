// weighted_state_provider — nextInt(totalWeight) 1회 후 누적차감으로 entry 선택(vanilla WeightedRandom 비트정확)
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

/**
 * 바닐라 WeightedStateProvider.getState = weightedList.getRandom(random):
 *   int r = random.nextInt(totalWeight);                 // 정확히 1 소비
 *   for entry: r -= entry.weight; if (r < 0) return entry;
 * totalWeight = Σ weight(int). states/weights는 JSON entries 삽입순.
 */
public final class WeightedStateProvider implements StateProvider {

    private final int[] states;
    private final int[] weights;
    private final int totalWeight;

    public WeightedStateProvider(int[] states, int[] weights) {
        this.states = states.clone();
        this.weights = weights.clone();
        // 바닐라 WeightedRandom.getTotalWeight: long 합산 후 > Integer.MAX_VALUE면 예외.
        long sum = 0;
        for (int w : weights) sum += w;
        if (sum > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Sum of weights must be <= 2147483647");
        }
        this.totalWeight = (int) sum;
    }

    @Override
    public int getState(WorldgenRandom random, int x, int y, int z) {
        // 바닐라 getRandom: totalWeight==0 → empty → getState의 orElseThrow(IllegalStateException). random 미소비.
        if (totalWeight == 0) {
            throw new IllegalStateException("WeightedStateProvider with no states");
        }
        int weightedIndex = random.nextInt(totalWeight);
        for (int i = 0; i < weights.length; i++) {
            weightedIndex -= weights[i];
            if (weightedIndex < 0) {
                return states[i];
            }
        }
        throw new IllegalStateException("WeightedStateProvider exhausted");
    }
}
