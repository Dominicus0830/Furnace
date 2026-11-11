// 유체 상태 하나(바닐라 FluidState) — 블록 상태가 품은 물·용암. 읽기 전용 값.
package org.domi.furnace.api.world;

/**
 * @param type    유체 키: minecraft:empty, minecraft:water, minecraft:flowing_water, minecraft:lava, minecraft:flowing_lava
 * @param amount  바닐라 {@code FluidState.getAmount}: 빈 유체 0, 원천 8, 흐르는 유체 1..8
 * @param falling 떨어지는 유체인가(바닐라 FlowingFluid.FALLING). 빈 유체는 거짓
 */
public record FluidData(String type, int amount, boolean falling) {

    public static final FluidData EMPTY = new FluidData("minecraft:empty", 0, false);

    public boolean isEmpty() { return type.equals("minecraft:empty"); }

    /** 원천(minecraft:water·minecraft:lava)인가. */
    public boolean isSource() { return type.equals("minecraft:water") || type.equals("minecraft:lava"); }
}
