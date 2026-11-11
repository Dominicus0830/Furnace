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
     * 밀도가 0 이하로 보장된 블록(셀 상한이 음수)에서 computeSubstance 와 같은 결과·부수효과를 내되, 밀도 값이 결과에 쓰이는
     * 갈래에서만 density 를 계산한다(성능 — 지표 위 공기처럼 결과가 밀도와 무관한 블록이 대부분이다).
     */
    default int computeSubstanceNonPositive(DensityFunction.FunctionContext ctx, DensityFunction density) {
        return computeSubstance(ctx, density.compute(ctx));
    }

    /**
     * 바닐라 Aquifer.shouldScheduleFluidUpdate — 마지막 computeSubstance 가 고른 유체가 이웃과 맞닿아 흘러야 하는가.
     * 노이즈 채우기·카버가 true 이고 놓은 블록에 유체가 있으면 후처리 표시를 한다(DisabledAquifer 는 늘 false).
     */
    default boolean shouldScheduleFluidUpdate() { return false; }
}
