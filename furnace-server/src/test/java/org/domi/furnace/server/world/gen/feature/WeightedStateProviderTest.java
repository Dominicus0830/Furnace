// weighted_state_provider의 nextInt(totalWeight) 1회 소비·가중치 누적차감 선택 parity
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class WeightedStateProviderTest {

    // flower_default: poppy(2), dandelion(1) → totalWeight 3. state id 임의(100,200).
    private WeightedStateProvider provider() {
        return new WeightedStateProvider(new int[]{100, 200}, new int[]{2, 1});
    }

    @Test
    void consumesExactlyOneNextInt() {
        WeightedStateProvider p = provider();
        WorldgenRandom actual = new WorldgenRandom();
        actual.setFeatureSeed(777L, 1, 4);
        p.getState(actual, 8, 64, 8);

        WorldgenRandom replay = new WorldgenRandom();
        replay.setFeatureSeed(777L, 1, 4);
        replay.nextInt(3); // totalWeight
        assertEquals(replay.nextInt(1_000_000), actual.nextInt(1_000_000));
    }

    @Test
    void weightedSelectionBoundaries() {
        // 누적차감: r 0,1 → state0(weight2), r 2 → state1(weight1).
        assertEquals(100, select(new int[]{100, 200}, new int[]{2, 1}, 0));
        assertEquals(100, select(new int[]{100, 200}, new int[]{2, 1}, 1));
        assertEquals(200, select(new int[]{100, 200}, new int[]{2, 1}, 2));

        WeightedStateProvider p = provider();
        WorldgenRandom a = new WorldgenRandom(); a.setFeatureSeed(1L, 0, 0);
        WorldgenRandom b = new WorldgenRandom(); b.setFeatureSeed(1L, 0, 0);
        assertEquals(p.getState(a, 0, 0, 0), p.getState(b, 0, 0, 0));
    }

    private static int select(int[] states, int[] weights, int r) {
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) return states[i];
        }
        throw new IllegalStateException();
    }
}
