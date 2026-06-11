// 바닐라 NoiseBasedAquifer 포팅 — 지하 수체/용암 배치 결정 (Task 8에서 완전 구현)
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.gen.df.DensityFunction;

public final class NoiseBasedAquifer implements Aquifer {

    @Override
    public int computeSubstance(DensityFunction.FunctionContext ctx, double density) {
        throw new UnsupportedOperationException("NoiseBasedAquifer not yet implemented");
    }
}
