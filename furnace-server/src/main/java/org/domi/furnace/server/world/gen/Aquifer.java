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

    /**
     * 바닐라 Aquifer.shouldScheduleFluidUpdate — 마지막 computeSubstance 가 고른 유체가 이웃과 맞닿아 흘러야 하는가.
     * 노이즈 채우기·카버가 true 이고 놓은 블록에 유체가 있으면 후처리 표시를 한다(DisabledAquifer 는 늘 false).
     */
    default boolean shouldScheduleFluidUpdate() { return false; }
}
