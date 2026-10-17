// 유체 예약 틱(바닐라 FlowingFluid.tick). 물·용암 흐름은 3-4d 에서 채운다 — 지금은 아무것도 하지 않는다.
package org.domi.furnace.server.block;

public final class FluidTicker {

    private FluidTicker() {}

    public static void tick(BlockLevel level, Pos pos, int fluidState) {}

    /** FluidState.randomTick — 용암(LavaFluid.randomTick: 불 번지기)만. TODO(3-4d). */
    public static void randomTick(BlockLevel level, Pos pos, int fluidState, RandomSource random) {}
}
