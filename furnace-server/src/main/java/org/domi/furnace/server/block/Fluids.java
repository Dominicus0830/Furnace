// 유체 상태(바닐라 FluidState)를 int 하나로: 종류(0 없음, 1 흐르는 물, 2 물, 3 흐르는 용암, 4 용암) | 양(1..8) << 3 | 떨어짐 << 7.
// 원천은 양 8. 레거시 블록(물·용암 블록의 level 속성)과의 변환은 바닐라 LiquidBlock / FlowingFluid.createLegacyState 와 같다.
package org.domi.furnace.server.block;

public final class Fluids {
    private Fluids() {}

    public static final int EMPTY = 0;
    public static final int T_FLOWING_WATER = 1, T_WATER = 2, T_FLOWING_LAVA = 3, T_LAVA = 4;

    public static final int WATER_SOURCE = T_WATER | 8 << 3;
    public static final int LAVA_SOURCE = T_LAVA | 8 << 3;

    public static int type(int f) { return f & 7; }

    public static boolean isEmpty(int f) { return (f & 7) == 0; }

    public static boolean isSource(int f) { return (f & 7) == T_WATER || (f & 7) == T_LAVA; }

    /** getAmount(원천 8). */
    public static int amount(int f) { return f >> 3 & 15; }

    public static boolean falling(int f) { return (f >> 7 & 1) != 0; }

    /** #minecraft:water 태그(물·흐르는 물). */
    public static boolean isWater(int f) { int t = f & 7; return t == T_WATER || t == T_FLOWING_WATER; }

    /** #minecraft:lava 태그. */
    public static boolean isLava(int f) { int t = f & 7; return t == T_LAVA || t == T_FLOWING_LAVA; }

    /** 원천(물) 그 자체인가 — getType() == Fluids.WATER. */
    public static boolean isWaterSource(int f) { return (f & 7) == T_WATER; }

    public static int flowing(boolean water, int amount, boolean falling) {
        return (water ? T_FLOWING_WATER : T_FLOWING_LAVA) | amount << 3 | (falling ? 1 << 7 : 0);
    }

    /** LiquidBlock 의 level 속성(0..15) → 유체 상태. */
    public static int fromLegacyLevel(boolean water, int level) {
        if (level == 0) return water ? WATER_SOURCE : LAVA_SOURCE;
        if (level < 8) return flowing(water, 8 - level, false);
        return flowing(water, 8, true);
    }

    /** FlowingFluid.getLegacyLevel: 원천 0, 아니면 8 - min(양, 8) (+8 떨어짐). */
    public static int legacyLevel(int f) {
        if (isSource(f)) return 0;
        return 8 - Math.min(amount(f), 8) + (falling(f) ? 8 : 0);
    }

    /** FluidState.createLegacyBlock — 비었으면 공기, 아니면 물/용암 블록. */
    public static int createLegacyBlock(int f) {
        Blocks b = Blocks.get();
        if (isEmpty(f)) return b.defaultState("minecraft:air");
        int base = b.defaultState(isWater(f) ? "minecraft:water" : "minecraft:lava");
        return LEVEL.withInt(base, legacyLevel(f));
    }

    private static final Prop LEVEL = Blocks.get().prop("level");

    /** 물 블록 틱 지연(Fluids.WATER.getTickDelay) = 5, 용암 = 30(울트라웜 차원 10). */
    public static int tickDelay(int f, boolean ultraWarm) {
        if (isWater(f)) return 5;
        return ultraWarm ? 10 : 30;
    }
}
