// 바닐라 NetherVines: 뒤틀린·눈물 덩굴 공통 — 빈칸으로 자라고, 뼛가루는 0.826 배씩 줄어드는 확률로 여러 칸.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.RandomSource;

final class NetherVines {
    private NetherVines() {}

    static boolean isValidGrowthState(int state) { return B.isAir(state); }

    static int blocksToGrowWhenBonemealed(RandomSource random) {
        double p = 1.0;
        int n = 0;
        while (random.nextDouble() < p) {
            p *= 0.826;
            n++;
        }
        return n;
    }
}
