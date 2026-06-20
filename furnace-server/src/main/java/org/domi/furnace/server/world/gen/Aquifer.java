// 바닐라 aquifer 시스템 — cave void를 water/lava로 채울지 결정하는 sealed interface
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.gen.df.DensityFunction;

public sealed interface Aquifer permits DisabledAquifer, NoiseBasedAquifer {
    /**
     * Called for every block where density <= 0.
     * Returns block state ID: 0=air, waterId, or lavaId.
     * Returns NO_CARVE (-1) when barrier pressure prevents carving (vanilla returns null).
     */
    int NO_CARVE = -1;

    int computeSubstance(DensityFunction.FunctionContext ctx, double density);
}
