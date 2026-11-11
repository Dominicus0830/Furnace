// 유체 종류 — vanilla FluidState.getType()에 대응(source/flowing 구분).
package org.domi.furnace.server.world.gen.feature;

public enum FluidKind {
    EMPTY, WATER, FLOWING_WATER, LAVA, FLOWING_LAVA;

    public static FluidKind byName(String name) {
        return switch (name) {
            case "minecraft:empty" -> EMPTY;
            case "minecraft:water" -> WATER;
            case "minecraft:flowing_water" -> FLOWING_WATER;
            case "minecraft:lava" -> LAVA;
            case "minecraft:flowing_lava" -> FLOWING_LAVA;
            default -> throw new IllegalArgumentException("알 수 없는 유체: " + name);
        };
    }

    /** 서버 유체 종류 번호(block.Fluids.T_*, 없으면 0). */
    public int fluidType() {
        return switch (this) {
            case EMPTY -> 0;
            case FLOWING_WATER -> org.domi.furnace.server.block.Fluids.T_FLOWING_WATER;
            case WATER -> org.domi.furnace.server.block.Fluids.T_WATER;
            case FLOWING_LAVA -> org.domi.furnace.server.block.Fluids.T_FLOWING_LAVA;
            case LAVA -> org.domi.furnace.server.block.Fluids.T_LAVA;
        };
    }

    public boolean isWater() { return this == WATER || this == FLOWING_WATER; }
    public boolean isLava() { return this == LAVA || this == FLOWING_LAVA; }
}
